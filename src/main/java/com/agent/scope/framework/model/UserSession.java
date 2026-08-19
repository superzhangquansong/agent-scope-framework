package com.agent.scope.framework.model;

import com.agent.scope.framework.hdl.HdlHome;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 用户会话上下文。
 *
 * <p>承载用户身份、HDL 访问令牌、当前房屋等信息，用于 API 调用时的鉴权与状态保持。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Data
public class UserSession implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 平台会话 token（前端持有，每次请求携带 X-Session-Token 头） */
    private String sessionToken;

    /** HDL 访问令牌（登录成功后由 HDL 返回，后续 API 调用鉴权用） */
    private String hdlAccessToken;

    /** HDL 刷新令牌 */
    private String hdlRefreshToken;

    /**
     * HDL 访问令牌绝对过期时间戳（毫秒）。
     * <p>登录/刷新成功后由 {@code loginTime + expiresIn * 1000} 计算得出，
     * {@link #isHdlAccessTokenExpired()} 据此判断是否需要刷新。</p>
     */
    private long hdlAccessTokenExpiresAt;

    /**
     * HDL 刷新令牌绝对过期时间戳（毫秒）。
     * <p>登录/刷新成功后由 {@code loginTime + refreshExpiresIn * 1000} 计算得出，
     * 过期后需通知前端重新登录。</p>
     */
    private long hdlRefreshTokenExpiresAt;

    /** 登录用户名 */
    private String loginName;

    /** 登录时间戳 */
    private long loginTime;

    /** 当前房屋 ID（同时也是储能电站 ID） */
    private String currentHomeId;

    /** 当前房屋名称 */
    private String currentHomeName;

    /** 房屋列表缓存（不持久化，按需重新加载） */
    @JsonIgnore
    private List<HdlHome> homeCache;

    /** 房屋缓存时间戳 */
    @JsonIgnore
    private long homeCacheTime;

    /** 设备列表缓存（不持久化，按需重新加载） */
    @JsonIgnore
    private List<Object> deviceCache;

    /** 设备缓存时间戳 */
    @JsonIgnore
    private long deviceCacheTime;

    /**
     * 是否已登录（有 HDL Token 视为登录）。
     * <p>加 @JsonIgnore 防止 Jackson 将 boolean getter 序列化为 "loggedIn" 字段，
     * 避免反序列化时因类中无此字段抛 UnrecognizedPropertyException。</p>
     */
    @JsonIgnore
    public boolean isLoggedIn() {
        return hdlAccessToken != null && !hdlAccessToken.isEmpty();
    }

    /**
     * 判断 HDL 访问令牌是否已过期。
     * <p>过期时间未设置（=0）时视为未过期，兼容旧会话数据；
     * 提前 60 秒判定过期，避免请求到达 HDL 网关时恰好失效。</p>
     *
     * @return true 表示访问令牌已过期，需调用 refreshToken 刷新
     */
    @JsonIgnore
    public boolean isHdlAccessTokenExpired() {
        if (hdlAccessTokenExpiresAt <= 0) {
            return false;
        }
        return System.currentTimeMillis() >= (hdlAccessTokenExpiresAt - 60_000L);
    }

    /**
     * 判断 HDL 刷新令牌是否已过期。
     * <p>过期时间未设置（=0）时视为未过期，兼容旧会话数据；
     * 刷新令牌过期后无法再获取新的访问令牌，必须通知前端重新登录。</p>
     *
     * @return true 表示刷新令牌已过期，需通知前端重新登录
     */
    @JsonIgnore
    public boolean isHdlRefreshTokenExpired() {
        if (hdlRefreshTokenExpiresAt <= 0) {
            return false;
        }
        return System.currentTimeMillis() >= hdlRefreshTokenExpiresAt;
    }

    /** 清空 Token（保留会话和缓存） */
    public void clearToken() {
        this.hdlAccessToken = null;
        this.hdlRefreshToken = null;
        this.hdlAccessTokenExpiresAt = 0;
        this.hdlRefreshTokenExpiresAt = 0;
    }
}
