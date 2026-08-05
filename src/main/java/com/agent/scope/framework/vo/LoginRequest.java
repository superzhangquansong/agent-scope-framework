package com.agent.scope.framework.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 登录请求 DTO。
 * <p>
 * 前端通过 axios 以 JSON 请求体方式提交登录名与密码，
 * 后端使用 {@code @RequestBody} 接收并反序列化为此对象。
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Data
public class LoginRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 登录名（手机号 / 邮箱 / 用户名） */
    private String loginName;

    /** 登录密码 */
    private String loginPwd;
}
