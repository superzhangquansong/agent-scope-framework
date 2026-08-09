package com.agent.scope.framework.context;

import com.agent.scope.framework.hdl.ImageInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

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
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionContext implements Serializable {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID
     */
    private String userId;

    /**
     * 当前房屋 ID
     */
    private String houseId;

    /**
     * 组织 ID（多租户隔离维度）
     */
    private String orgId;

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 登录名
     */
    private String loginName;

    /**
     * 访问令牌（调用外部 API 时携带）
     */
    private String accessToken;

    /**
     * 刷新令牌（用于无感续期）
     */
    private String refreshToken;

    /**
     * 全链路追踪 ID（关联 OpenTelemetry）
     */
    private String traceId;

    /**
     * 请求来源（web/app/mini-program）
     */
    private String source;

    /**
     * 租户 ID（多租户隔离用）
     */
    private String tenantId;

    /**
     * 多模态图片列表（用户上传的图片，Base64 编码）。
     *
     * <p>由 ChatService 在构建 SessionContext 时从 ChatStreamDTO 注入，
     * FloorPlanTool 等需要图片的工具方法通过 SessionContext 获取。
     * 为 null 或空时表示纯文本请求（无图片）。</p>
     */
    private List<ImageInfo> images;
}