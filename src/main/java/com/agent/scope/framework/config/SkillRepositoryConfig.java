package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.FileSystemSkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * AgentScope 2.0 GA 特性二十八/三十三：技能系统（Skills）+ 技能自动沉淀配置
 * <p>
 * 技能系统通过 Markdown 文件沉淀可复用知识，支持自动沉淀与按需加载：
 * <ul>
 *   <li>{@code SkillRepository}：技能仓库，管理技能的增删改查</li>
 *   <li>{@code SkillBox}：技能集合管理器，将技能暴露为工具供 Agent 调用</li>
 *   <li>{@code skill_load} 工具：Agent 运行时动态加载技能</li>
 *   <li>自动沉淀：成功任务自动生成 Markdown 技能文件到 workspace/skills/</li>
 * </ul>
 * </p>
 * <p>
 * 技能文件格式（Markdown）：
 * <pre>
 * ---
 * name: smart-home-control
 * description: 智能家居设备控制技能，支持多设备批量操作
 * ---
 * # 技能内容
 * 当用户要求控制设备时，先调用 query_device_list 获取设备列表...
 * </pre>
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.advanced", name = "skill-repository-enabled", havingValue = "true")
public class SkillRepositoryConfig {

    private final AgentScopeProperties properties;

    /**
     * 技能仓库 Bean。
     * <p>
     * 基于 {@link FileSystemSkillRepository}，技能文件存储于工作区 {@code skills/} 子目录。
     * 支持运行时动态增删技能，变更立即生效（下次 Agent 调用即可使用）。
     * </p>
     *
     * @param agentWorkspacePath 工作区路径
     * @return 技能仓库（可能为空，表示工作区未启用）
     */
    @Bean
    public Optional<AgentSkillRepository> skillRepository(Optional<Path> agentWorkspacePath) {
        if (agentWorkspacePath.isEmpty()) {
            log.warn("[SkillRepositoryConfig] 工作区未启用，技能仓库无法装配");
            return Optional.empty();
        }
        Path skillsDir = agentWorkspacePath.get().resolve("skills");
        // 确保技能目录存在（FileSystemSkillRepository 要求目录必须存在）
        try {
            Files.createDirectories(skillsDir);
        } catch (IOException e) {
            log.warn("[SkillRepositoryConfig] 创建技能目录失败: path={}, error={}",
                    skillsDir.toAbsolutePath(), e.getMessage());
            return Optional.empty();
        }
        AgentSkillRepository repository = new FileSystemSkillRepository(skillsDir);

        log.info("[SkillRepositoryConfig] 技能仓库已装配: skillsDir={}, skillCount={}",
                skillsDir.toAbsolutePath(), repository.getAllSkillNames().size());
        return Optional.of(repository);
    }
}
