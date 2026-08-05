package com.agent.scope.framework.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建会话请求 DTO。
 * <p>
 * 前端通过 axios 以 JSON 请求体方式提交用户 ID 与房屋 ID，
 * 后端据此创建新的聊天会话。
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Data
public class CreateSessionRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private String userId;

    /** 房屋 ID（可选） */
    private String houseId;
}
