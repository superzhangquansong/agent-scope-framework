package com.agent.scope.framework.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 媒体数据来源类型枚举。
 * <p>
 * 用于标识音频、视频等多模态数据的来源形式，与 {@link ImageTypeEnum} 语义一致，
 * 但独立命名以保持音频/视频场景下的可读性。
 * </p>
 * <ul>
 *   <li>{@link #URL}：媒体为可访问 URL（http/https/file）</li>
 *   <li>{@link #BASE64}：媒体为 Base64 编码字符串（不含 data: 前缀）</li>
 * </ul>
 *
 * @author zqs
 * @since 2.0.0
 */
@Getter
@AllArgsConstructor
public enum MediaTypeEnum {
    /**
     * Base64 编码
     */
    BASE64(0, "base64"),
    /**
     * URL 链接
     */
    URL(1, "url"),
    ;

    /**
     * 编码
     */
    private Integer code;

    /**
     * 描述
     */
    private String desc;
}
