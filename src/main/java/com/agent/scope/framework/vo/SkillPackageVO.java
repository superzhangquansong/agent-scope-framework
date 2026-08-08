package com.agent.scope.framework.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 技能压缩包批量导入结果 VO。
 * <p>
 * 用于 {@code POST /api/v1/skills/upload-package} 接口的响应，
 * 返回成功导入的技能列表与跳过的无效文件列表。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillPackageVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 成功导入的技能摘要列表。
     */
    private List<SkillVO> imported;

    /**
     * 跳过的无效文件列表（格式不规范、缺少 frontmatter 等）。
     * <p>每项格式：{@code 文件路径 - 跳过原因}</p>
     */
    private List<String> skipped;

    /**
     * 成功导入数量。
     */
    private int successCount;

    /**
     * 跳过数量。
     */
    private int skippedCount;
}
