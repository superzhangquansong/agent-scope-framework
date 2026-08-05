package com.agent.scope.framework.context;

import java.io.Serializable;

/**
 * 统一会话上下文 POJO —— SessionContext
 * <p>
 * 全流程强制携带：从 HTTP 请求入口（Controller）解析 Token 并构建此对象，
 * 通过 Reactor Context 或 ThreadLocal + 拦截器传递至整个调用链。
 * Agent 执行、中间件（Middleware）、Hook、工具（Tool）方法均可无差别获取该对象。
 * </p>
 * <p>
 * 核心字段：
 * - userId：用户 ID
 * - houseId：当前房屋 ID
 * - orgId：组织 ID（多租户隔离）
 * - sessionId：会话 ID
 * - userName：用户昵称
 * - loginName：登录名
 * - accessToken：访问令牌（调用 HDL API 时使用）
 * - traceId：全链路追踪 ID
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
public class SessionContext implements Serializable {

    /** 序列化版本号 */
    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private String userId;

    /** 当前房屋 ID */
    private String houseId;

    /** 组织 ID（多租户隔离维度） */
    private String orgId;

    /** 会话 ID */
    private String sessionId;

    /** 用户昵称 */
    private String userName;

    /** 登录名 */
    private String loginName;

    /** 访问令牌（调用外部 API 时携带） */
    private String accessToken;

    /** 刷新令牌（用于无感续期） */
    private String refreshToken;

    /** 全链路追踪 ID（关联 OpenTelemetry） */
    private String traceId;

    /** 请求来源（web/app/mini-program） */
    private String source;

    /**
     * 默认构造方法
     */
    public SessionContext() {
    }

    /**
     * 构建会话上下文
     *
     * @param userId      用户 ID
     * @param houseId     房屋 ID
     * @param sessionId   会话 ID
     * @param accessToken 访问令牌
     */
    public SessionContext(String userId, String houseId, String sessionId, String accessToken) {
        this.userId = userId;
        this.houseId = houseId;
        this.sessionId = sessionId;
        this.accessToken = accessToken;
    }

    // ==================== Getter / Setter ====================

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getHouseId() {
        return houseId;
    }

    public void setHouseId(String houseId) {
        this.houseId = houseId;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getLoginName() {
        return loginName;
    }

    public void setLoginName(String loginName) {
        this.loginName = loginName;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    /**
     * 构建 Builder 模式快速构造
     *
     * @return Builder 对象
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder 模式内部类
     */
    public static class Builder {
        private String userId;
        private String houseId;
        private String orgId;
        private String sessionId;
        private String userName;
        private String loginName;
        private String accessToken;
        private String refreshToken;
        private String traceId;
        private String source;

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder houseId(String houseId) {
            this.houseId = houseId;
            return this;
        }

        public Builder orgId(String orgId) {
            this.orgId = orgId;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder userName(String userName) {
            this.userName = userName;
            return this;
        }

        public Builder loginName(String loginName) {
            this.loginName = loginName;
            return this;
        }

        public Builder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Builder refreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public SessionContext build() {
            SessionContext ctx = new SessionContext();
            ctx.userId = this.userId;
            ctx.houseId = this.houseId;
            ctx.orgId = this.orgId;
            ctx.sessionId = this.sessionId;
            ctx.userName = this.userName;
            ctx.loginName = this.loginName;
            ctx.accessToken = this.accessToken;
            ctx.refreshToken = this.refreshToken;
            ctx.traceId = this.traceId;
            ctx.source = this.source;
            return ctx;
        }
    }

    @Override
    public String toString() {
        return "SessionContext{" +
                "userId='" + userId + '\'' +
                ", houseId='" + houseId + '\'' +
                ", orgId='" + orgId + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", userName='" + userName + '\'' +
                ", loginName='" + loginName + '\'' +
                ", traceId='" + traceId + '\'' +
                '}';
    }
}