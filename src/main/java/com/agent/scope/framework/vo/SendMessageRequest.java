package com.agent.scope.framework.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 发送聊天消息请求 DTO。
 * <p>
 * 前端通过 axios 以 JSON 请求体方式提交会话 ID、用户 ID、房屋 ID 与消息内容，
 * 后端接收后缓存消息供 SSE 流式接口消费。
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Data
public class SendMessageRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话 ID */
    private String sessionId;

    /** 用户 ID */
    private String userId;

    /** 房屋 ID */
    private String houseId;

    /** 消息内容 */
    private String content;
}
