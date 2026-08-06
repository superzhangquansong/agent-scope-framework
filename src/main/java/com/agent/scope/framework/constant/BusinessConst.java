package com.agent.scope.framework.constant;

/**
 * @author zqs
 * @description:
 * @date 19:01 2026/8/5
 */
public interface BusinessConst {
    String CTX_KEY_SESSION_CONTEXT = "sessionContextCxt";
    /**
     * Bearer Token 前缀
     */
    String BEARER_PREFIX = "Bearer ";

    //SSE Emitter，超时时间 5 分钟
    long SSE_EMITTER_TIMEOUT = 300_000L;
}