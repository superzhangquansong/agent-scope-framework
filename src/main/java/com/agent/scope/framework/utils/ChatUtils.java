package com.agent.scope.framework.utils;


import static com.agent.scope.framework.constant.BusinessConst.BEARER_PREFIX;

/**
 * @author zqs
 * @title: ChatUtils
 * @projectName agent-scope-framework
 * @description:
 * @date 2026/8/6 09:43
 */
public class ChatUtils {

    /**
     * 从 Authorization 请求头中提取 accessToken。
     * <p>
     * 支持 {@code Bearer {token}} 格式，自动去除前缀。
     * </p>
     *
     * @param authHeader Authorization 请求头原始值
     * @return accessToken，不存在时返回 null
     */
    public static String extractAccessToken(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            return null;
        }
        if (authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length()).trim();
        }
        return authHeader.trim();
    }
}