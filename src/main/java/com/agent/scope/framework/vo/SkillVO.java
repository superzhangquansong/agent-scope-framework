package com.agent.scope.framework.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 技能信息 VO。
 * <p>
 * 用于技能管理接口（上传/列表/查询）的响应数据封装，
 * 包含技能元数据（name/description/triggers）与正文内容。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 技能名称（唯一标识，对应 frontmatter 中的 name 字段）。
     */
    private String name;

    /**
     * 技能描述（对应 frontmatter 中的 description 字段）。
     * <p>LLM 根据此描述判断是否加载该技能，需清晰表达适用场景。</p>
     */
    private String description;

    /**
     * 触发工具列表（对应 frontmatter 中的 triggers 字段，逗号分隔）。
     */
    private String triggers;

    /**
     * 技能正文内容（frontmatter 之后的 Markdown 正文）。
     * <p>列表接口中可能为 null（仅返回摘要），查询接口中返回完整内容。</p>
     */
    private String content;

    /**
     * 技能文件大小（字节）。
     */
    private Long fileSize;

    /**
     * 技能文件最后修改时间（毫秒时间戳）。
     */
    private Long lastModified;
}
