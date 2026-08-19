package com.agent.scope.framework.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * HDL 登录结果 VO。
 *
 * @author zqs
 * @since 2.0.0
 */
@Data
@Accessors(chain = true)
public class LoginResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 会话 Token（前端持有，每次请求携带） */
    private String sessionToken;

    /** 登录名 */
    private String loginName;

    /** 过期时间（秒） */
    private long expiresIn;

    /** 刷新令牌过期时间（秒），刷新令牌过期后需重新登录 */
    private long refreshExpiresIn;
}
