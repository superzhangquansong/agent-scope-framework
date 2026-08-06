package com.agent.scope.framework.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author zqs
 * @description:
 * @date 13:48 2026/8/6
 */
@Getter
@AllArgsConstructor
public enum ImageTypeEnum {
    BASE64(0, "base64"),
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