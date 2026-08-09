package com.agent.scope.framework.tool;

import com.agent.scope.framework.constant.BusinessConst;
import com.agent.scope.framework.vo.ToolResultVO;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件系统工具（对齐 AgentScope 2.0 FileSystem 特性）。
 *
 * <p>通过 {@link WorkspaceManager} 提供工作区文件的读写能力，
 * 让 Agent 能自主读取计划文件、记忆文件、技能文件等。</p>
 *
 * <p>提供 3 个 @Tool 方法：</p>
 * <ul>
 *   <li>{@code read_file}：读取工作区内指定路径的文件内容</li>
 *   <li>{@code write_file}：向工作区内指定路径写入文件内容</li>
 *   <li>{@code list_files}：列出工作区内指定目录的文件列表</li>
 * </ul>
 *
 * <p>所有路径都限制在工作区根目录内，防止越权访问。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Component
public class FileTool extends AbstractTool {

    /** 工作区路径（可选注入，workspace 未装配时为空） */
    private final ObjectProvider<Path> workspacePathProvider;

    /**
     * 构造方法注入。
     *
     * @param workspacePathProvider 工作区路径 ObjectProvider
     */
    public FileTool(ObjectProvider<Path> workspacePathProvider) {
        this.workspacePathProvider = workspacePathProvider;
    }

    /**
     * 读取工作区内文件内容。
     *
     * @param relativePath   文件相对路径（相对于工作区根目录，如 "plans/PLAN.md"）
     * @param runtimeContext 运行时上下文（自动注入）
     * @return 工具结果 VO（data 含 path、content、size）
     */
    @Tool(name = "read_file",
            description = "读取工作区内指定路径的文件内容。路径相对于工作区根目录，如 'plans/PLAN.md'、'memory/MEMORY.md'。",
            readOnly = true)
    public ToolResultVO readFile(
            @ToolParam(name = "relativePath", required = true,
                    description = "文件相对路径，如 'plans/PLAN.md'") String relativePath,
            RuntimeContext runtimeContext) {

        WorkspaceManager workspace = getWorkspaceManager();
        if (workspace == null) {
            return ToolResultVO.failure(BusinessConst.HTTP_INTERNAL_ERROR,
                    BusinessConst.MSG_FILE_WORKSPACE_NOT_ENABLED);
        }

        try {
            Path filePath = resolveSafePath(workspace, relativePath);
            if (!Files.exists(filePath)) {
                return ToolResultVO.failure(BusinessConst.HTTP_BAD_REQUEST,
                        BusinessConst.MSG_FILE_NOT_FOUND + ": " + relativePath);
            }
            String content = Files.readString(filePath, StandardCharsets.UTF_8);

            // 构建返回数据
            Map<String, Object> data = new LinkedHashMap<>(3);
            data.put("path", relativePath);
            data.put("content", content);
            data.put("size", content.length());

            log.info("[FileTool] 读取文件: path={}, size={}", relativePath, content.length());
            return ToolResultVO.success(BusinessConst.MSG_FILE_READ_SUCCESS, data,
                    BusinessConst.ROUTE_FILE_RESULT, null);
        } catch (IOException e) {
            log.error("[FileTool] 读取文件失败: path={}", relativePath, e);
            return ToolResultVO.failure(BusinessConst.HTTP_INTERNAL_ERROR,
                    "读取失败: " + e.getMessage());
        }
    }

    /**
     * 写入文件到工作区。
     *
     * @param relativePath   文件相对路径
     * @param content        文件内容
     * @param runtimeContext 运行时上下文（自动注入）
     * @return 工具结果 VO（data 含 path、size、status）
     */
    @Tool(name = "write_file",
            description = "向工作区内指定路径写入文件内容。目录不存在时自动创建。",
            readOnly = false)
    public ToolResultVO writeFile(
            @ToolParam(name = "relativePath", required = true,
                    description = "文件相对路径，如 'notes/meeting.md'") String relativePath,
            @ToolParam(name = "content", required = true,
                    description = "文件内容（UTF-8 文本）") String content,
            RuntimeContext runtimeContext) {

        WorkspaceManager workspace = getWorkspaceManager();
        if (workspace == null) {
            return ToolResultVO.failure(BusinessConst.HTTP_INTERNAL_ERROR,
                    BusinessConst.MSG_FILE_WORKSPACE_NOT_ENABLED);
        }

        try {
            Path filePath = resolveSafePath(workspace, relativePath);
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, content, StandardCharsets.UTF_8);

            // 构建返回数据
            Map<String, Object> data = new LinkedHashMap<>(3);
            data.put("path", relativePath);
            data.put("size", content.length());
            data.put("status", "written");

            log.info("[FileTool] 写入文件: path={}, size={}", relativePath, content.length());
            return ToolResultVO.success(BusinessConst.MSG_FILE_WRITE_SUCCESS, data,
                    BusinessConst.ROUTE_FILE_RESULT, null);
        } catch (IOException e) {
            log.error("[FileTool] 写入文件失败: path={}", relativePath, e);
            return ToolResultVO.failure(BusinessConst.HTTP_INTERNAL_ERROR,
                    "写入失败: " + e.getMessage());
        }
    }

    /**
     * 列出工作区内目录的文件列表。
     *
     * @param relativeDir    目录相对路径（空字符串表示根目录）
     * @param runtimeContext 运行时上下文（自动注入）
     * @return 工具结果 VO（data 含 dir、files、total）
     */
    @Tool(name = "list_files",
            description = "列出工作区内指定目录的文件和子目录列表。",
            readOnly = true)
    public ToolResultVO listFiles(
            @ToolParam(name = "relativeDir", required = false,
                    description = "目录相对路径，空字符串表示根目录") String relativeDir,
            RuntimeContext runtimeContext) {

        WorkspaceManager workspace = getWorkspaceManager();
        if (workspace == null) {
            return ToolResultVO.failure(BusinessConst.HTTP_INTERNAL_ERROR,
                    BusinessConst.MSG_FILE_WORKSPACE_NOT_ENABLED);
        }

        try {
            String dir = relativeDir == null ? "" : relativeDir;
            Path dirPath = resolveSafePath(workspace, dir);
            if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
                return ToolResultVO.failure(BusinessConst.HTTP_BAD_REQUEST,
                        BusinessConst.MSG_FILE_DIR_NOT_FOUND + ": " + dir);
            }

            // 遍历目录构建文件列表
            List<Map<String, Object>> files = new ArrayList<>();
            Files.list(dirPath).forEach(p -> {
                Map<String, Object> entry = new LinkedHashMap<>(3);
                entry.put("name", p.getFileName().toString());
                entry.put("type", Files.isDirectory(p) ? "directory" : "file");
                try {
                    entry.put("size", Files.size(p));
                } catch (IOException ignored) {
                    entry.put("size", -1);
                }
                files.add(entry);
            });

            // 构建返回数据
            Map<String, Object> data = new LinkedHashMap<>(3);
            data.put("dir", dir);
            data.put("files", files);
            data.put("total", files.size());

            log.info("[FileTool] 列出目录: dir={}, count={}", dir, files.size());
            return ToolResultVO.success(BusinessConst.MSG_FILE_LIST_SUCCESS, data,
                    BusinessConst.ROUTE_FILE_RESULT, null);
        } catch (IOException e) {
            log.error("[FileTool] 列出目录失败: dir={}", relativeDir, e);
            return ToolResultVO.failure(BusinessConst.HTTP_INTERNAL_ERROR,
                    "列出失败: " + e.getMessage());
        }
    }

    /**
     * 获取 WorkspaceManager 实例（懒加载）。
     *
     * <p>从 ObjectProvider 获取工作区路径，若路径不存在则返回 null。
     * 每次调用都从 provider 获取最新实例，支持热更新。</p>
     *
     * @return WorkspaceManager 实例；工作区未启用时返回 null
     */
    private WorkspaceManager getWorkspaceManager() {
        Path workspacePath = workspacePathProvider.getIfAvailable();
        if (workspacePath == null) {
            log.warn("[FileTool] 工作区路径未配置，文件操作不可用");
            return null;
        }
        return new WorkspaceManager(workspacePath);
    }

    /**
     * 安全解析路径，防止路径穿越攻击。
     *
     * <p>确保解析后的绝对路径始终在工作区根目录内，
     * 防止通过 "../" 越权访问工作区外的文件。</p>
     *
     * @param workspace     工作区管理器
     * @param relativePath  相对路径
     * @return 安全的绝对路径
     * @throws IllegalArgumentException 路径越界时抛出
     */
    private Path resolveSafePath(WorkspaceManager workspace, String relativePath) {
        Path rootPath = workspace.getWorkspace();
        Path resolved = rootPath.resolve(relativePath).normalize();
        if (!resolved.startsWith(rootPath)) {
            throw new IllegalArgumentException("路径越界，禁止访问工作区外文件: " + relativePath);
        }
        return resolved;
    }
}
