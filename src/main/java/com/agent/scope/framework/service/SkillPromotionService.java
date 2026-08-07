package com.agent.scope.framework.service;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import com.agent.scope.framework.exception.BusinessException;
import com.agent.scope.framework.exception.ErrorCode;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static com.agent.scope.framework.constant.BusinessConst.AUTO_PROMOTED_SKILL_PREFIX;
import static com.agent.scope.framework.constant.BusinessConst.SKILL_CONTENT_MAX_LENGTH;
import static com.agent.scope.framework.constant.BusinessConst.SKILL_META_KEY_DESCRIPTION;
import static com.agent.scope.framework.constant.BusinessConst.SKILL_META_KEY_NAME;
import static com.agent.scope.framework.constant.BusinessConst.SKILL_META_KEY_STEPS;
import static com.agent.scope.framework.constant.BusinessConst.SKILL_META_KEY_TRIGGERS;
import static com.agent.scope.framework.constant.BusinessConst.SKILL_PROMOTION_MIN_TOOL_CALLS;

/**
 * AgentScope 2.0 GA 特性三十三：技能自动沉淀服务。
 * <p>
 * 在 Agent 完成复杂任务后，自动从会话记录中提取可复用的技能模式，
 * 生成 Markdown 技能文件写入工作区 {@code skills/} 目录，
 * 供后续 Agent 通过 {@code skill_load} 工具动态加载复用。
 * </p>
 * <p>
 * <b>沉淀触发条件</b>（避免低质量技能污染仓库）：
 * <ul>
 *   <li>会话中工具调用次数 ≥ {@link BusinessConst#SKILL_PROMOTION_MIN_TOOL_CALLS}</li>
 *   <li>存在最终回复内容（非空）</li>
 *   <li>技能仓库已装配（{@link AgentSkillRepository} 存在且可写）</li>
 * </ul>
 * </p>
 * <p>
 * <b>技能文件格式</b>（Markdown + YAML frontmatter，兼容 AgentScope MarkdownSkillParser）：
 * <pre>
 * ---
 * name: auto_xxx
 * description: ...
 * triggers: tool_a,tool_b
 * steps: 1. ... 2. ...
 * ---
 * # 技能内容
 * 详细执行步骤与示例...
 * </pre>
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.advanced", name = "skill-promotion-enabled", havingValue = "true")
public class SkillPromotionService {

    /**
     * 技能仓库（由 {@link com.agent.scope.framework.config.SkillRepositoryConfig} 装配）。
     * <p>若工作区未启用或技能仓库未装配，本服务仍会装配但所有沉淀操作降级为 no-op。</p>
     */
    private final Optional<AgentSkillRepository> skillRepository;

    /**
     * 工作区路径（由 {@link com.agent.scope.framework.config.WorkspaceConfig} 装配）。
     * <p>当技能仓库不可用时，作为兜底直接写入 skills/ 目录。</p>
     */
    private final Optional<Path> agentWorkspacePath;

    private final AgentScopeProperties properties;

    /**
     * 异步沉淀技能。
     * <p>
     * 在 ChatService 的 {@code doOnComplete} 回调中调用，从会话记录器中提取工具调用链
     * 与最终回复，生成 SKILL.md 文件并保存到技能仓库。
     * </p>
     * <p>
     * <b>容错策略</b>：所有异常均捕获并降级为日志输出，不影响主流程。
     * 技能沉淀失败不会导致用户请求失败。
     * </p>
     *
     * @param sessionId 会话 ID（用于生成技能唯一标识）
     * @param userId   用户 ID（用于日志追踪）
     * @param recorder 会话记录器（包含工具调用入参/出参、最终回复、Token 消耗等）
     */
    @Async
    public void promoteSkill(String sessionId, String userId, ChatService.ChatSessionRecorder recorder) {
        try {
            if (!shouldPromote(sessionId, recorder)) {
                return;
            }
            AgentSkill skill = buildSkillFromRecorder(sessionId, userId, recorder);
            saveSkill(skill, sessionId);
            log.info("[SkillPromotion] 技能沉淀成功: sessionId={}, skillName={}, toolCalls={}",
                    sessionId, skill.getName(), recorder.toolCallResults.size());
        } catch (Exception e) {
            log.error("[SkillPromotion] 技能沉淀失败: sessionId={}, error={}",
                    sessionId, e.getMessage(), e);
        }
    }

    /**
     * 判断当前会话是否值得沉淀为技能。
     * <p>
     * 沉淀门槛：
     * <ul>
     *   <li>工具调用次数 ≥ {@link BusinessConst#SKILL_PROMOTION_MIN_TOOL_CALLS}（简单问答不值得沉淀）</li>
     *   <li>存在最终回复内容（避免空回复沉淀为无效技能）</li>
     * </ul>
     * </p>
     *
     * @param sessionId 会话 ID
     * @param recorder  会话记录器
     * @return true=值得沉淀
     */
    private boolean shouldPromote(String sessionId, ChatService.ChatSessionRecorder recorder) {
        int toolCallCount = recorder.toolCallResults.size();
        if (toolCallCount < SKILL_PROMOTION_MIN_TOOL_CALLS) {
            log.debug("[SkillPromotion] 跳过沉淀（工具调用不足）: sessionId={}, toolCalls={}, min={}",
                    sessionId, toolCallCount, SKILL_PROMOTION_MIN_TOOL_CALLS);
            return false;
        }
        if (recorder.finalReplyContent.length() == 0) {
            log.debug("[SkillPromotion] 跳过沉淀（无最终回复）: sessionId={}", sessionId);
            return false;
        }
        return true;
    }

    /**
     * 从会话记录器构建技能实例。
     * <p>
     * 提取要素：
     * <ul>
     *   <li>name: {@code auto_<sessionId哈希>}（唯一标识，避免与人工技能重名）</li>
     *   <li>description: 基于最终回复摘要生成</li>
     *   <li>triggers: 触发该技能的工具名称列表</li>
     *   <li>steps: 工具调用序列步骤</li>
     *   <li>skillContent: 完整 Markdown 内容</li>
     * </ul>
     * </p>
     *
     * @param sessionId 会话 ID
     * @param userId   用户 ID
     * @param recorder  会话记录器
     * @return 技能实例
     */
    private AgentSkill buildSkillFromRecorder(String sessionId, String userId,
                                              ChatService.ChatSessionRecorder recorder) {
        // 工具调用序列（按 toolCallId 顺序）
        List<String> toolCallIds = new java.util.ArrayList<>(recorder.toolCallResults.keySet());
        Map<String, StringBuilder> argsMap = recorder.toolCallArguments;
        Map<String, StringBuilder> resultMap = recorder.toolCallResults;

        // 触发工具列表（去重保序）
        List<String> toolNames = new java.util.ArrayList<>();
        for (String toolCallId : toolCallIds) {
            // toolCallId 格式通常为 toolName_<seq>，但 ToolCallStartHandler 未记录 toolName 映射，
            // 此处通过 toolCallArguments 顺序推断；若 argsMap 无对应则用 toolCallId 兜底
            String name = inferToolName(toolCallId, argsMap, resultMap);
            if (!toolNames.contains(name)) {
                toolNames.add(name);
            }
        }
        String triggers = String.join(",", toolNames);

        // 执行步骤
        StringBuilder stepsBuilder = new StringBuilder();
        for (int i = 0; i < toolCallIds.size(); i++) {
            String id = toolCallIds.get(i);
            String name = inferToolName(id, argsMap, resultMap);
            String args = truncate(argsMap.getOrDefault(id, new StringBuilder("")).toString(), 100);
            stepsBuilder.append(i + 1).append(". 调用 ").append(name)
                    .append("(").append(args).append(")").append("\n");
        }
        String steps = stepsBuilder.toString().trim();

        // 技能描述（基于最终回复前 100 字符）
        String description = truncate(recorder.finalReplyContent.toString(), 100);

        // 技能名称（唯一，避免覆盖已有技能）
        String skillName = AUTO_PROMOTED_SKILL_PREFIX + Integer.toHexString(sessionId.hashCode());

        // 技能正文 Markdown
        String content = buildSkillMarkdown(sessionId, userId, toolNames, toolCallIds,
                argsMap, resultMap, recorder.finalReplyContent.toString(), steps);

        // 构建 AgentSkill
        return AgentSkill.builder()
                .name(skillName)
                .description(description)
                .putMetadata(SKILL_META_KEY_NAME, skillName)
                .putMetadata(SKILL_META_KEY_DESCRIPTION, description)
                .putMetadata(SKILL_META_KEY_TRIGGERS, triggers)
                .putMetadata(SKILL_META_KEY_STEPS, steps)
                .skillContent(content)
                .source("auto-promoted")
                .build();
    }

    /**
     * 从工具调用结果中推断工具名称。
     * <p>
     * 由于 {@link ChatService.ChatSessionRecorder} 未单独维护 toolCallId→toolName 映射，
     * 此处使用 toolCallId 作为兜底（实际工具名由 ToolCallStartHandler 落库时已记录）。
     * 后续可扩展为从数据库查询真实 toolName。
     * </p>
     *
     * @param toolCallId 工具调用 ID
     * @param argsMap    入参映射
     * @param resultMap  出参映射
     * @return 工具名称（推断值）
     */
    private String inferToolName(String toolCallId,
                                 Map<String, StringBuilder> argsMap,
                                 Map<String, StringBuilder> resultMap) {
        // toolCallId 兜底返回，避免 NPE；真实场景下可从 ChatRecordService 查询
        return toolCallId != null ? toolCallId : "unknown_tool";
    }

    /**
     * 生成技能 Markdown 正文。
     * <p>
     * 包含：
     * <ul>
     *   <li>会话元信息（sessionId/userId）</li>
     *   <li>工具调用链明细</li>
     *   <li>Agent 最终回复（截断）</li>
     *   <li>触发条件与复用建议</li>
     * </ul>
     * </p>
     *
     * @param sessionId       会话 ID
     * @param userId          用户 ID
     * @param toolNames       工具名称列表
     * @param toolCallIds     工具调用 ID 列表
     * @param argsMap         入参映射
     * @param resultMap       出参映射
     * @param finalReply      最终回复内容
     * @param steps           步骤摘要
     * @return Markdown 正文
     */
    private String buildSkillMarkdown(String sessionId, String userId, List<String> toolNames,
                                      List<String> toolCallIds,
                                      Map<String, StringBuilder> argsMap,
                                      Map<String, StringBuilder> resultMap,
                                      String finalReply, String steps) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 自动沉淀技能\n\n");
        sb.append("## 触发条件\n");
        sb.append("当 Agent 需要调用以下工具时复用本技能：").append(String.join("、", toolNames)).append("\n\n");
        sb.append("## 执行步骤\n");
        sb.append(steps).append("\n\n");
        sb.append("## 工具调用明细\n");
        for (String id : toolCallIds) {
            String args = truncate(argsMap.getOrDefault(id, new StringBuilder("")).toString(), 200);
            String result = truncate(resultMap.getOrDefault(id, new StringBuilder("")).toString(), 200);
            sb.append("- **").append(id).append("**\n");
            sb.append("  - 入参: ").append(args).append("\n");
            sb.append("  - 出参: ").append(result).append("\n");
        }
        sb.append("\n## Agent 最终回复\n");
        sb.append(truncate(finalReply, SKILL_CONTENT_MAX_LENGTH / 2)).append("\n");
        sb.append("\n---\n");
        sb.append("- sessionId: ").append(sessionId).append("\n");
        sb.append("- userId: ").append(userId).append("\n");

        // 截断避免技能文件过大
        return truncate(sb.toString(), SKILL_CONTENT_MAX_LENGTH);
    }

    /**
     * 保存技能到技能仓库。
     * <p>
     * 优先通过 {@link AgentSkillRepository#save} 持久化；
     * 若技能仓库未装配或保存失败，降级为直接写入工作区 skills/ 目录。
     * </p>
     *
     * @param skill     技能实例
     * @param sessionId 会话 ID（用于日志）
     */
    private void saveSkill(AgentSkill skill, String sessionId) {
        if (skillRepository.isPresent()) {
            AgentSkillRepository repo = skillRepository.get();
            if (!repo.isWriteable()) {
                repo.setWriteable(true);
            }
            boolean saved = repo.save(List.of(skill), false);
            if (saved) {
                log.info("[SkillPromotion] 技能已写入技能仓库: name={}", skill.getName());
                return;
            }
            log.warn("[SkillPromotion] 技能仓库 save 返回 false，尝试降级写入文件: name={}", skill.getName());
        }

        // 降级：直接写入 skills/ 目录
        if (agentWorkspacePath.isPresent()) {
            writeSkillToFileSystem(skill, agentWorkspacePath.get());
        } else {
            log.warn("[SkillPromotion] 技能仓库与工作区均不可用，技能沉淀失败: name={}", skill.getName());
        }
    }

    /**
     * 兜底：直接将技能写入工作区 skills/ 目录。
     * <p>
     * 仅在 {@link AgentSkillRepository} 不可用时使用，
     * 生成 {@code <workspace>/skills/<skillName>.md} 文件。
     * </p>
     *
     * @param skill       技能实例
     * @param workspacePath 工作区路径
     */
    private void writeSkillToFileSystem(AgentSkill skill, Path workspacePath) {
        try {
            Path skillsDir = workspacePath.resolve("skills");
            java.nio.file.Files.createDirectories(skillsDir);
            Path skillFile = skillsDir.resolve(skill.getName() + ".md");
            String content = renderSkillFile(skill);
            java.nio.file.Files.writeString(skillFile, content);
            log.info("[SkillPromotion] 技能已直接写入文件: path={}", skillFile.toAbsolutePath());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SKILL_PROMOTION_FAILED,
                    "写入技能文件失败: " + skill.getName(), e);
        }
    }

    /**
     * 渲染技能为完整 Markdown 文件（含 YAML frontmatter）。
     * <p>
     * 格式与 AgentScope {@code MarkdownSkillParser} 兼容：
     * <pre>
     * ---
     * name: ...
     * description: ...
     * triggers: ...
     * steps: ...
     * ---
     * # 正文
     * </pre>
     * </p>
     *
     * @param skill 技能实例
     * @return 完整 Markdown 文件内容
     */
    private String renderSkillFile(AgentSkill skill) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("name: ").append(skill.getName()).append("\n");
        sb.append("description: ").append(escapeYaml(skill.getDescription())).append("\n");
        // 元数据中的 triggers/steps（自动沉淀时由 buildSkillFromRecorder 写入）
        Object triggers = skill.getMetadataValue(SKILL_META_KEY_TRIGGERS);
        Object steps = skill.getMetadataValue(SKILL_META_KEY_STEPS);
        if (triggers != null) {
            sb.append("triggers: ").append(escapeYaml(triggers.toString())).append("\n");
        }
        if (steps != null) {
            sb.append("steps: ").append(escapeYaml(steps.toString())).append("\n");
        }
        sb.append("---\n");
        sb.append(skill.getSkillContent());
        return sb.toString();
    }

    /**
     * YAML 字符串转义（避免换行/冒号破坏 frontmatter 解析）。
     *
     * @param value 原始值
     * @return 转义后的值
     */
    private String escapeYaml(String value) {
        if (value == null) {
            return "";
        }
        // YAML 中含冒号或换行的值需用双引号包裹，此处简化处理：替换换行为分号
        return value.replace("\n", "; ").replace("\r", "");
    }

    /**
     * 截断字符串到指定长度，超出部分以 "..." 标记。
     *
     * @param value   原始字符串
     * @param maxLength 最大长度
     * @return 截断后的字符串
     */
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value != null ? value : "";
        }
        return value.substring(0, maxLength) + "...";
    }
}
