package com.agent.scope.framework.dto;

import com.agent.scope.framework.enums.ImageTypeEnum;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 聊天流式请求 DTO。
 * <p>
 * 支持纯文本消息和多模态消息（文本 + 图片）。当 {@link #images} 非空时，
 * 将与 {@link #userMessage} 一起构建为多模态 {@code UserMessage} 传递给 AgentScope。
 * </p>
 *
 * @author zqs
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatStreamDTO implements Serializable {

    /**
     * 会话 ID（必填）
     */
    @NotBlank(message = "会话信息不能为空")
    private String sessionId;

    /**
     * 用户 ID（必填）
     */
    @NotBlank(message = "用户信息不能为空")
    private String userId;

    /**
     * 家庭 ID（可选，业务隔离用）
     */
    private String houseId;

    /**
     * 用户文本消息（必填）
     */
    @NotBlank(message = "信息不能为空")
    private String userMessage;

    /**
     * 访问令牌（可选，用于下游工具鉴权）
     */
    private String accessToken;

    /**
     * 图片列表（可选）。
     * <p>
     * 元素内容取决于 {@link #imageType}：
     * <ul>
     *   <li>{@code imageType="url"}：元素为图片可访问 URL（http/https/file）</li>
     *   <li>{@code imageType="base64"}：元素为 Base64 编码的图片数据（不含 data: 前缀）</li>
     * </ul>
     * 为空或 null 时表示纯文本消息。
     * </p>
     */
    private List<String> images;

    /**
     * 图片数据类型，可选值 {@code "url"} 或 {@code "base64"}，默认 {@code "url"}。
     */
    @Builder.Default
    private ImageTypeEnum imageType = ImageTypeEnum.URL;
}