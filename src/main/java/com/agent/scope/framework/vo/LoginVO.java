package com.agent.scope.framework.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 登录返回视图对象。
 * <p>
 * 对应登录接口成功后返回的认证信息，包含访问令牌、刷新令牌、用户 ID 等。
 * 前端据此持久化 Token，后续请求携带 accessToken 进行鉴权。
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Data
public class LoginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 访问令牌（调用云端 API 时携带） */
    private String accessToken;

    /** 刷新令牌（用于无感续期） */
    private String refreshToken;

    /** 用户 ID */
    private String userId;

    /** 用户昵称 */
    private String userName;
}
