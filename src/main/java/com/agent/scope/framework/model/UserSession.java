package com.agent.scope.framework.model;

import com.agent.scope.framework.hdl.HdlHome;
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

    /** 登录用户名 */
    private String loginName;

    /** 登录时间戳 */
    private long loginTime;

    /** 当前房屋 ID（同时也是储能电站 ID） */
    private String currentHomeId;

    /** 当前房屋名称 */
    private String currentHomeName;

    /** 房屋列表缓存 */
    private List<HdlHome> homeCache;

    /** 房屋缓存时间戳 */
    private long homeCacheTime;

    /** 设备列表缓存 */
    private List<Object> deviceCache;

    /** 设备缓存时间戳 */
    private long deviceCacheTime;

    /**
     * 是否已登录（有 HDL Token 视为登录）。
     */
    public boolean isLoggedIn() {
        return hdlAccessToken != null && !hdlAccessToken.isEmpty();
    }

    /** 清空 Token（保留会话和缓存） */
    public void clearToken() {
        this.hdlAccessToken = null;
        this.hdlRefreshToken = null;
    }
}
