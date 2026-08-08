package com.agent.scope.framework.controller;

import com.agent.scope.framework.exception.BusinessException;
import com.agent.scope.framework.vo.Response;
import com.agent.scope.framework.vo.SkillPackageVO;
import com.agent.scope.framework.vo.SkillVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.agent.scope.framework.constant.BusinessConst.*;
import static com.agent.scope.framework.exception.ErrorCode.*;

/**
 * 技能管理控制器（特性28增强：用户自定义技能上传与管理）。
 * <p>
 * 提供技能文件的 CRUD 接口，用户可通过 HTTP 上传自定义技能 Markdown 文件，
 * 无需手动操作服务器文件系统。技能文件存储在工作区 {@code skills/} 目录下，
 * 与自动沉淀的技能共享同一仓库，Agent 运行时通过 {@code skill_load} 工具加载复用。
 * </p>
 * <p>
 * <b>接口列表</b>：
 * <ul>
 *   <li>POST /api/v1/skills/upload - 上传单个技能 Markdown 文件</li>
 *   <li>POST /api/v1/skills/upload-package - 上传技能压缩包（.zip），批量导入技能</li>
 *   <li>GET /api/v1/skills - 列出所有技能摘要</li>
 *   <li>GET /api/v1/skills/{name} - 查询技能完整内容</li>
 *   <li>DELETE /api/v1/skills/{name} - 删除指定技能</li>
 * </ul>
 * </p>
 * <p>
 * <b>技能文件格式</b>（Markdown + YAML frontmatter）：
 * <pre>
 * ---
 * name: my-skill
 * description: 技能描述（LLM 根据此判断是否加载）
 * triggers: tool_a,tool_b
 * ---
 * # 技能正文
 * 执行步骤...
 * </pre>
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/skills")
@RequiredArgsConstructor
public class SkillController {

    /**
     * 工作区路径（由 {@link com.agent.scope.framework.config.WorkspaceConfig} 装配）。
     * <p>技能文件存储在 {@code <workspace>/skills/} 目录下。工作区未启用时为空。</p>
     */
    private final Optional<Path> agentWorkspacePath;

    /**
     * 上传技能 Markdown 文件。
     * <p>
     * 接收前端上传的 {@code .md} 文件，校验格式后保存到技能目录。
     * 文件名即为技能名（去除 {@code .md} 后缀），需符合命名规范。
     * </p>
     *
     * @param file     技能 Markdown 文件
     * @param override 是否覆盖已存在技能（默认 false）
     * @return 上传成功的技能摘要
     */
    @PostMapping("/upload")
    public Response<SkillVO> uploadSkill(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "override", defaultValue = "false") boolean override) {
        log.info("[Skill] 上传技能: fileName={}, size={}, override={}",
                file.getOriginalFilename(), file.getSize(), override);

        // 1. 校验工作区是否启用
        Path skillsDir = resolveSkillsDirOrThrow();

        // 2. 校验文件大小
        if (file.getSize() > SKILL_UPLOAD_MAX_SIZE_BYTES) {
            throw new BusinessException(SKILL_FILE_TOO_LARGE,
                    "文件大小 " + file.getSize() + "B 超过上限 " + SKILL_UPLOAD_MAX_SIZE_BYTES + "B");
        }

        // 3. 从原始文件名提取技能名称
        String originalFilename = file.getOriginalFilename();
        String skillName = extractSkillName(originalFilename);

        // 4. 校验技能名称合法性
        if (!Pattern.matches(SKILL_NAME_REGEX, skillName)) {
            throw new BusinessException(SKILL_NAME_INVALID, "技能名称: " + skillName);
        }

        // 5. 检查是否已存在
        Path skillFile = skillsDir.resolve(skillName + SKILL_FILE_SUFFIX);
        if (Files.exists(skillFile) && !override) {
            throw new BusinessException(SKILL_ALREADY_EXISTS, "技能已存在: " + skillName);
        }

        // 6. 读取文件内容并校验 frontmatter 格式
        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException(SKILL_UPLOAD_FAILED, "读取上传文件失败: " + e.getMessage());
        }

        Map<String, String> frontmatter = parseFrontmatter(content);
        if (frontmatter == null || !frontmatter.containsKey(SKILL_META_KEY_NAME)) {
            throw new BusinessException(SKILL_FILE_INVALID, "缺少 YAML frontmatter 或 name 字段");
        }

        // 7. 写入文件
        try {
            Files.createDirectories(skillsDir);
            Files.writeString(skillFile, content, StandardCharsets.UTF_8);
            log.info("[Skill] 技能上传成功: name={}, path={}", skillName, skillFile.toAbsolutePath());
        } catch (IOException e) {
            throw new BusinessException(SKILL_UPLOAD_FAILED,
                    "写入技能文件失败: " + skillName + ", error=" + e.getMessage());
        }

        // 8. 构建响应 VO
        SkillVO vo = SkillVO.builder()
                .name(skillName)
                .description(frontmatter.get(SKILL_META_KEY_DESCRIPTION))
                .triggers(frontmatter.get(SKILL_META_KEY_TRIGGERS))
                .fileSize(file.getSize())
                .lastModified(System.currentTimeMillis())
                .build();

        return Response.success(vo, "技能上传成功");
    }

    /**
     * 上传技能压缩包（.zip），保持目录结构批量导入技能包。
     * <p>
     * 接收前端上传的 {@code .zip} 压缩包，以压缩包名作为技能包名称，
     * 解压到 {@code skills/<packageName>/} 子目录下，<b>保持压缩包内的完整目录结构</b>。
     * 支持技能文件（.md）和辅助配置文件（.env/.txt/.json/.yml/.yaml）。
     * </p>
     * <p>
     * <b>规范校验</b>：
     * <ul>
     *   <li>解压后根目录必须存在 {@code SKILL.md} 文件，否则拒绝导入</li>
     *   <li>所有 {@code SKILL.md} 文件必须包含 YAML frontmatter（name 字段必填）</li>
     *   <li>逐个输出每个文件的校验结果（通过/失败+原因）</li>
     * </ul>
     * </p>
     * <p>
     * <b>安全防护</b>：
     * <ul>
     *   <li>压缩包大小上限 {@link BusinessConst#SKILL_PACKAGE_MAX_SIZE_BYTES}（默认 10MB）</li>
     *   <li>压缩包内文件数量上限 {@link BusinessConst#SKILL_PACKAGE_MAX_FILE_COUNT}（默认 100，防 zip bomb）</li>
     *   <li>Zip Slip 路径遍历防护：校验解压目标路径必须在技能包目录内</li>
     *   <li>文件类型白名单：仅允许 .md/.env/.txt/.json/.yml/.yaml</li>
     * </ul>
     * </p>
     *
     * @param file     技能压缩包文件（.zip）
     * @param override 是否覆盖已存在技能包（默认 false）
     * @return 批量导入结果（成功列表 + 跳过/失败列表）
     */
    @PostMapping("/upload-package")
    public Response<SkillPackageVO> uploadSkillPackage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "override", defaultValue = "false") boolean override) {
        log.info("[Skill] 上传技能压缩包: fileName={}, size={}, override={}",
                file.getOriginalFilename(), file.getSize(), override);

        // 1. 校验工作区是否启用
        Path skillsDir = resolveSkillsDirOrThrow();

        // 2. 校验文件大小
        if (file.getSize() > SKILL_PACKAGE_MAX_SIZE_BYTES) {
            throw new BusinessException(SKILL_PACKAGE_TOO_LARGE,
                    "压缩包大小 " + file.getSize() + "B 超过上限 " + SKILL_PACKAGE_MAX_SIZE_BYTES + "B");
        }

        // 3. 校验文件扩展名，并提取技能包名称
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(SKILL_PACKAGE_SUFFIX_ZIP)) {
            throw new BusinessException(SKILL_PACKAGE_FORMAT_INVALID,
                    "仅支持 .zip 格式, 收到: " + originalFilename);
        }
        // 压缩包名去掉 .zip 后缀作为技能包目录名
        String packageName = originalFilename.substring(0,
                originalFilename.length() - SKILL_PACKAGE_SUFFIX_ZIP.length());
        if (!Pattern.matches(SKILL_NAME_REGEX, packageName)) {
            throw new BusinessException(SKILL_NAME_INVALID,
                    "技能包名称非法（仅允许字母、数字、下划线、短横线）: " + packageName);
        }

        // 4. 检查技能包目录是否已存在
        Path packageDir = skillsDir.resolve(packageName);
        if (Files.exists(packageDir) && !override) {
            throw new BusinessException(SKILL_ALREADY_EXISTS,
                    "技能包已存在: " + packageName + "，请先删除或使用 override=true 覆盖");
        }

        // 5. 解压到 skills/<packageName>/ 目录，保持目录结构
        List<SkillVO> imported = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        int fileCount = 0;

        try (InputStream is = file.getInputStream();
             ZipInputStream zis = new ZipInputStream(is)) {
            // 如果是覆盖模式，先删除旧目录
            if (Files.exists(packageDir)) {
                deleteRecursively(packageDir);
            }
            Files.createDirectories(packageDir);

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                fileCount++;
                // 防止 zip bomb：文件数量超限
                if (fileCount > SKILL_PACKAGE_MAX_FILE_COUNT) {
                    throw new BusinessException(SKILL_PACKAGE_TOO_MANY_FILES,
                            "压缩包内文件数量超过上限 " + SKILL_PACKAGE_MAX_FILE_COUNT);
                }

                String entryName = entry.getName();

                // 跳过目录条目（文件写入时会自动创建父目录）
                if (entry.isDirectory()) {
                    continue;
                }

                // 文件类型白名单校验
                if (!isAllowedFileType(entryName)) {
                    skipped.add(entryName + " - 文件类型不在白名单内，跳过");
                    continue;
                }

                // Zip Slip 防护：校验解压目标路径在 packageDir 内
                Path targetFile = packageDir.resolve(entryName).normalize();
                if (!targetFile.startsWith(packageDir.normalize())) {
                    skipped.add(entryName + " - 路径非法（Zip Slip 防护）");
                    continue;
                }

                // 读取文件内容并写入（保持目录结构）
                byte[] bytes = zis.readAllBytes();
                Files.createDirectories(targetFile.getParent());
                Files.write(targetFile, bytes);
                log.info("[Skill] 压缩包文件解压: entry={}, path={}, size={}",
                        entryName, targetFile.toAbsolutePath(), bytes.length);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(SKILL_PACKAGE_EXTRACT_FAILED,
                    "解压失败: " + e.getMessage());
        }

        // 6. 处理压缩包内包含同名顶层目录的情况
        //    例如：zip -r hdl-hub.zip hdl-hub/ 打包后，解压到 skills/hdl-hub/ 下实际路径为
        //    skills/hdl-hub/hdl-hub/SKILL.md，需要将内层目录内容提升到 packageDir 根目录
        flattenNestedDirectoryIfNeeded(packageDir, packageName);

        // 7. 规范校验：根目录必须存在 SKILL.md
        Path rootSkillFile = packageDir.resolve(SKILL_DEFINITION_FILENAME);
        if (!Files.exists(rootSkillFile)) {
            // 清理已解压的目录，避免残留无效技能包
            deleteRecursively(packageDir);
            throw new BusinessException(SKILL_FILE_INVALID,
                    "根目录缺少 " + SKILL_DEFINITION_FILENAME + " 文件，不符合技能包规范");
        }

        // 8. 遍历所有 SKILL.md 文件，逐个校验 frontmatter 格式
        validateAllSkillFiles(packageDir, imported, skipped);

        // 9. 校验是否至少有一个有效的 SKILL.md
        if (imported.isEmpty()) {
            deleteRecursively(packageDir);
            throw new BusinessException(SKILL_PACKAGE_NO_VALID_SKILLS,
                    "所有 SKILL.md 文件格式校验失败，请检查 skipped 列表中的错误信息");
        }

        log.info("[Skill] 技能压缩包导入完成: packageName={}, imported={}, skipped={}, total={}",
                packageName, imported.size(), skipped.size(), fileCount);

        SkillPackageVO vo = SkillPackageVO.builder()
                .imported(imported)
                .skipped(skipped)
                .successCount(imported.size())
                .skippedCount(skipped.size())
                .build();

        return Response.success(vo, "技能压缩包导入完成: " + packageName);
    }

    /**
     * 列出所有技能摘要。
     * <p>
     * 遍历技能目录下的所有 {@code .md} 文件，解析 frontmatter 返回技能摘要列表
     * （不含正文内容，减少响应体积）。
     * </p>
     *
     * @return 技能摘要列表
     */
    @GetMapping
    public Response<List<SkillVO>> listSkills() {
        log.info("[Skill] 列出所有技能");

        Path skillsDir = resolveSkillsDirOrThrow();
        List<SkillVO> skills = new ArrayList<>();

        try (var stream = Files.list(skillsDir)) {
            stream
                    .filter(path -> path.toString().endsWith(SKILL_FILE_SUFFIX))
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        SkillVO vo = buildSkillSummary(path);
                        if (vo != null) {
                            skills.add(vo);
                        }
                    });
        } catch (IOException e) {
            log.warn("[Skill] 遍历技能目录失败: dir={}, error={}",
                    skillsDir.toAbsolutePath(), e.getMessage());
        }

        log.info("[Skill] 技能列表查询完成: count={}", skills.size());
        return Response.success(skills);
    }

    /**
     * 查询技能完整内容。
     * <p>
     * 读取指定技能的完整 Markdown 内容（含 frontmatter 与正文）。
     * </p>
     *
     * @param name 技能名称
     * @return 技能完整信息（含 content 字段）
     */
    @GetMapping("/{name}")
    public Response<SkillVO> getSkill(@PathVariable String name) {
        log.info("[Skill] 查询技能: name={}", name);

        Path skillsDir = resolveSkillsDirOrThrow();
        Path skillFile = skillsDir.resolve(name + SKILL_FILE_SUFFIX);

        if (!Files.exists(skillFile)) {
            throw new BusinessException(SKILL_NOT_FOUND, "技能不存在: " + name);
        }

        try {
            String content = Files.readString(skillFile, StandardCharsets.UTF_8);
            Map<String, String> frontmatter = parseFrontmatter(content);

            SkillVO.SkillVOBuilder builder = SkillVO.builder()
                    .name(name)
                    .content(content)
                    .fileSize(Files.size(skillFile))
                    .lastModified(Files.getLastModifiedTime(skillFile).toMillis());

            // frontmatter 可能为 null（文件格式不规范但已存在），做安全处理
            if (frontmatter != null) {
                builder.description(frontmatter.get(SKILL_META_KEY_DESCRIPTION))
                        .triggers(frontmatter.get(SKILL_META_KEY_TRIGGERS));
            }

            return Response.success(builder.build());
        } catch (IOException e) {
            throw new BusinessException(SKILL_NOT_FOUND, "读取技能文件失败: " + name);
        }
    }

    /**
     * 删除指定技能。
     * <p>
     * 删除技能目录下的 {@code <name>.md} 文件。
     * 自动沉淀的技能（名称以 {@code auto_} 前缀开头）也可通过此接口删除。
     * </p>
     *
     * @param name 技能名称
     * @return 删除成功响应
     */
    @DeleteMapping("/{name}")
    public Response<Void> deleteSkill(@PathVariable String name) {
        log.info("[Skill] 删除技能: name={}", name);

        Path skillsDir = resolveSkillsDirOrThrow();
        Path skillFile = skillsDir.resolve(name + SKILL_FILE_SUFFIX);

        if (!Files.exists(skillFile)) {
            throw new BusinessException(SKILL_NOT_FOUND, "技能不存在: " + name);
        }

        try {
            Files.delete(skillFile);
            log.info("[Skill] 技能删除成功: name={}, path={}", name, skillFile.toAbsolutePath());
        } catch (IOException e) {
            throw new BusinessException(SKILL_DELETE_FAILED,
                    "删除技能文件失败: " + name + ", error=" + e.getMessage());
        }

        return Response.success();
    }

    // ==================== 私有工具方法 ====================

    /**
     * 解析技能目录路径，工作区未启用时抛异常。
     *
     * @return 技能目录 Path
     */
    private Path resolveSkillsDirOrThrow() {
        if (agentWorkspacePath.isEmpty()) {
            throw new BusinessException(SKILL_REPOSITORY_NOT_ENABLED);
        }
        return agentWorkspacePath.get().resolve(SKILL_DIR_NAME);
    }

    /**
     * 从原始文件名提取技能名称（去除 {@code .md} 后缀）。
     *
     * @param originalFilename 上传文件的原始文件名
     * @return 技能名称
     */
    private String extractSkillName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BusinessException(SKILL_NAME_INVALID, "文件名不能为空");
        }
        // 去除路径前缀（部分浏览器上传时带路径）
        String filename = originalFilename;
        int lastSlash = filename.lastIndexOf('/');
        if (lastSlash >= 0) {
            filename = filename.substring(lastSlash + 1);
        }
        int lastBackslash = filename.lastIndexOf('\\');
        if (lastBackslash >= 0) {
            filename = filename.substring(lastBackslash + 1);
        }
        // 去除 .md 后缀
        if (filename.toLowerCase().endsWith(SKILL_FILE_SUFFIX)) {
            filename = filename.substring(0, filename.length() - SKILL_FILE_SUFFIX.length());
        }
        return filename;
    }

    /**
     * 从 zip entry 路径中提取纯文件名（去除目录前缀）。
     * <p>
     * 例如 {@code subdir/my-skill.md} → {@code my-skill.md}。
     * </p>
     *
     * @param entryName zip entry 名称
     * @return 纯文件名（含扩展名）
     */
    private String extractFileNameFromEntry(String entryName) {
        // 统一处理 / 和 \ 分隔符
        String normalized = entryName.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash >= 0) {
            return normalized.substring(lastSlash + 1);
        }
        return normalized;
    }

    /**
     * 校验文件类型是否在白名单内。
     * <p>白名单：.md / .env / .txt / .json / .yml / .yaml</p>
     *
     * @param fileName 文件名
     * @return true=允许
     */
    private boolean isAllowedFileType(String fileName) {
        String lowerName = fileName.toLowerCase();
        return SKILL_PACKAGE_ALLOWED_SUFFIXES.stream().anyMatch(lowerName::endsWith);
    }

    /**
     * 遍历技能包目录下所有 SKILL.md 文件，逐个校验 frontmatter 格式。
     * <p>
     * 校验规则：每个 SKILL.md 必须包含 YAML frontmatter 且 name 字段必填。
     * 通过的加入 imported 列表，失败的加入 skipped 列表（含失败原因）。
     * </p>
     *
     * @param packageDir 技能包根目录
     * @param imported   成功校验的技能列表（输出参数）
     * @param skipped    校验失败的文件列表（输出参数，格式：路径 - 原因）
     */
    private void validateAllSkillFiles(Path packageDir, List<SkillVO> imported, List<String> skipped) {
        try (var stream = Files.walk(packageDir)) {
            stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(SKILL_DEFINITION_FILENAME))
                    .forEach(skillFile -> {
                        // 计算相对于技能包根目录的相对路径，用于日志展示
                        String relativePath = packageDir.relativize(skillFile).toString();

                        try {
                            String content = Files.readString(skillFile, StandardCharsets.UTF_8);
                            Map<String, String> frontmatter = parseFrontmatter(content);

                            if (frontmatter == null) {
                                skipped.add(relativePath + " - 缺少 YAML frontmatter（需以 --- 开头）");
                                return;
                            }
                            if (!frontmatter.containsKey(SKILL_META_KEY_NAME)) {
                                skipped.add(relativePath + " - frontmatter 缺少 name 字段");
                                return;
                            }

                            // 校验通过，构建摘要
                            String skillName = frontmatter.get(SKILL_META_KEY_NAME);
                            SkillVO vo = SkillVO.builder()
                                    .name(skillName)
                                    .description(frontmatter.get(SKILL_META_KEY_DESCRIPTION))
                                    .triggers(frontmatter.get(SKILL_META_KEY_TRIGGERS))
                                    .fileSize(Files.size(skillFile))
                                    .lastModified(Files.getLastModifiedTime(skillFile).toMillis())
                                    .build();
                            imported.add(vo);
                            log.info("[Skill] SKILL.md 校验通过: path={}, name={}", relativePath, skillName);
                        } catch (IOException e) {
                            skipped.add(relativePath + " - 读取文件失败: " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warn("[Skill] 遍历技能包目录失败: dir={}, error={}",
                    packageDir.toAbsolutePath(), e.getMessage());
        }
    }

    /**
     * 递归删除目录及其内容（用于清理失败的解压残留）。
     *
     * @param path 要删除的目录路径
     */
    private void deleteRecursively(Path path) {
        try (var stream = Files.walk(path)) {
            // 按逆序删除（先文件后目录）
            stream.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            log.warn("[Skill] 删除文件失败: path={}, error={}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warn("[Skill] 递归删除目录失败: path={}, error={}", path, e.getMessage());
        }
    }

    /**
     * 处理压缩包内包含同名顶层目录的情况。
     * <p>
     * 当用户在目录外执行 {@code zip -r hdl-hub.zip hdl-hub/} 打包时，zip 内路径为
     * {@code hdl-hub/SKILL.md}，解压到 {@code skills/hdl-hub/} 后实际路径变为
     * {@code skills/hdl-hub/hdl-hub/SKILL.md}。本方法检测到这种情况后，将内层目录
     * 的所有内容移动到技能包根目录。
     * </p>
     * <p>
     * 判断条件：如果 packageDir 根目录下没有 SKILL.md，但存在唯一一个同名子目录，
     * 且该子目录内有 SKILL.md，则将子目录内容提升到根目录。
     * </p>
     *
     * @param packageDir 技能包根目录
     * @param packageName 技能包名称（用于匹配同名子目录）
     */
    private void flattenNestedDirectoryIfNeeded(Path packageDir, String packageName) {
        // 根目录已有 SKILL.md，无需处理
        if (Files.exists(packageDir.resolve(SKILL_DEFINITION_FILENAME))) {
            return;
        }

        // 检查是否存在同名子目录
        Path nestedDir = packageDir.resolve(packageName);
        if (!Files.isDirectory(nestedDir)) {
            return;
        }

        // 内层目录是否有 SKILL.md
        if (!Files.exists(nestedDir.resolve(SKILL_DEFINITION_FILENAME))) {
            return;
        }

        log.info("[Skill] 检测到压缩包内嵌同名目录，提升内容到根目录: nestedDir={}", nestedDir);

        // 将内层目录的所有内容移动到 packageDir 根目录
        try (var stream = Files.list(nestedDir)) {
            stream.forEach(source -> {
                Path target = packageDir.resolve(source.getFileName());
                try {
                    Files.move(source, target);
                } catch (IOException e) {
                    log.warn("[Skill] 移动文件失败: source={}, target={}, error={}",
                            source, target, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("[Skill] 遍历内层目录失败: nestedDir={}, error={}",
                    nestedDir, e.getMessage());
            return;
        }

        // 删除空的内层目录
        try {
            Files.delete(nestedDir);
            log.info("[Skill] 已删除空的内层目录: {}", nestedDir);
        } catch (IOException e) {
            log.warn("[Skill] 删除内层目录失败: nestedDir={}, error={}",
                    nestedDir, e.getMessage());
        }
    }

    /**
     * 解析 Markdown 文件中的 YAML frontmatter。
     * <p>
     * frontmatter 格式：
     * <pre>
     * ---
     * name: xxx
     * description: xxx
     * triggers: xxx
     * ---
     * </pre>
     * </p>
     *
     * @param content 文件完整内容
     * @return frontmatter 键值对 Map；文件无 frontmatter 时返回 null
     */
    private Map<String, String> parseFrontmatter(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }

        String[] lines = content.split("\n", -1);
        if (lines.length < 2 || !lines[0].trim().equals(SKILL_FRONTMATTER_DELIMITER)) {
            return null;
        }

        Map<String, String> frontmatter = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            // 遇到第二个 "---" 表示 frontmatter 结束
            if (line.equals(SKILL_FRONTMATTER_DELIMITER)) {
                return frontmatter;
            }
            // 解析 key: value
            int separatorIndex = line.indexOf(SKILL_FRONTMATTER_KV_SEPARATOR);
            if (separatorIndex > 0) {
                String key = line.substring(0, separatorIndex).trim();
                String value = line.substring(separatorIndex + 1).trim();
                frontmatter.put(key, value);
            }
        }

        // 没有找到结束的 "---"，frontmatter 不完整
        return frontmatter.isEmpty() ? null : frontmatter;
    }

    /**
     * 从技能文件构建摘要 VO（不含正文内容）。
     *
     * @param skillFile 技能文件 Path
     * @return 技能摘要 VO；解析失败时返回 null
     */
    private SkillVO buildSkillSummary(Path skillFile) {
        try {
            String content = Files.readString(skillFile, StandardCharsets.UTF_8);
            Map<String, String> frontmatter = parseFrontmatter(content);

            // 技能名：文件名去除 .md 后缀
            String fileName = skillFile.getFileName().toString();
            String skillName = fileName.substring(0, fileName.length() - SKILL_FILE_SUFFIX.length());

            SkillVO.SkillVOBuilder builder = SkillVO.builder()
                    .name(skillName)
                    .fileSize(Files.size(skillFile))
                    .lastModified(Files.getLastModifiedTime(skillFile).toMillis());

            if (frontmatter != null) {
                builder.description(frontmatter.get(SKILL_META_KEY_DESCRIPTION))
                        .triggers(frontmatter.get(SKILL_META_KEY_TRIGGERS));
            }

            return builder.build();
        } catch (IOException e) {
            log.warn("[Skill] 读取技能文件失败，跳过: file={}, error={}",
                    skillFile.getFileName(), e.getMessage());
            return null;
        }
    }
}
