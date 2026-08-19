package com.agent.scope.framework.hdl;

import com.agent.scope.framework.model.UserSession;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * HDL IoT 业务服务。
 *
 * <p>封装 HDL 后端 API 调用，提供登录、房屋查询/选择、设备查询等能力。
 * 对标原 hdl-agent 项目的 HdlIotService，适配当前项目架构。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HdlIotService {

    /** accessToken 字段名 */
    private static final String FIELD_ACCESS_TOKEN = "accessToken";
    /** refreshToken 字段名 */
    private static final String FIELD_REFRESH_TOKEN = "refreshToken";
    /** 查询全部类型 */
    private static final String SEARCH_TYPE_ALL = "ALL";
    /** 列表字段名 */
    private static final String FIELD_HOMES = "homes";
    private static final String FIELD_LIST = "list";
    private static final String FIELD_RECORDS = "records";
    /** 默认刷新令牌有效期（秒），HDL 未返回时兜底 */
    private static final long DEFAULT_REFRESH_EXPIRES_IN_SECONDS = 7 * 24 * 3600L;

    /** HDL API HTTP 客户端 */
    private final HdlApiClient apiClient;

    /**
     * 用户登录（对标 login.js，调用 HDL 后端 /basis-footstone/user/oauth/login）。
     *
     * @param loginName 登录名
     * @param loginPwd  登录密码
     * @param session   用户会话（登录成功后注入 hdlAccessToken）
     * @return 登录结果
     */
    public LoginResult login(String loginName, String loginPwd, UserSession session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(HdlApiConstants.FIELD_LOGIN_NAME, loginName);
        data.put(HdlApiConstants.FIELD_LOGIN_PWD, loginPwd);
        data.put(HdlApiConstants.FIELD_GRANT_TYPE, HdlApiConstants.GRANT_TYPE_PASSWORD);

        // 登录接口不需要鉴权（needAuth=false），但需要签名（needSign=true）
        HdlResponse resp = apiClient.post(HdlApiConstants.LOGIN, data, true, false, null);
        LoginResult result = new LoginResult();
        Map<String, Object> dataMap = resp.getDataAsMap();
        if (resp.success() && dataMap != null) {
            Object accessToken = dataMap.get(FIELD_ACCESS_TOKEN);
            Object refreshToken = dataMap.get(FIELD_REFRESH_TOKEN);
            if (accessToken != null) {
                long now = System.currentTimeMillis();
                long expiresIn = toLong(dataMap.get(HdlApiConstants.FIELD_EXPIRES_IN));
                long refreshExpiresIn = toLong(dataMap.get(HdlApiConstants.FIELD_REFRESH_EXPIRES_IN));
                // HDL 未返回 refreshExpiresIn 时使用默认值（7 天）
                if (refreshExpiresIn <= 0) {
                    refreshExpiresIn = DEFAULT_REFRESH_EXPIRES_IN_SECONDS;
                }
                session.setHdlAccessToken(accessToken.toString());
                if (refreshToken != null) {
                    session.setHdlRefreshToken(refreshToken.toString());
                }
                // 计算并保存绝对过期时间戳（毫秒），供 TokenAspect 判断是否需要刷新/重新登录
                session.setHdlAccessTokenExpiresAt(now + expiresIn * 1000L);
                session.setHdlRefreshTokenExpiresAt(now + refreshExpiresIn * 1000L);
                session.setLoginName(loginName);
                result.setSuccess(true);
                result.setExpiresIn(expiresIn);
                result.setRefreshExpiresIn(refreshExpiresIn);
                log.info("[HdlIotService] 登录成功: loginName={}, expiresIn={}s, refreshExpiresIn={}s",
                        loginName, expiresIn, refreshExpiresIn);
                return result;
            }
        }
        result.setSuccess(false);
        result.setMsg(resp.getMsg() != null ? resp.getMsg() : "用户名或密码不正确");
        return result;
    }

    /**
     * 使用刷新令牌获取新的访问令牌。
     *
     * <p>当 TokenAspect 检测到访问令牌过期但刷新令牌仍有效时调用此方法。
     * 调用 HDL 后端 {@code /basis-footstone/user/oauth/login}，grantType=refresh_token，
     * 成功后更新 session 中的 hdlAccessToken / hdlRefreshToken 及其过期时间。</p>
     *
     * @param session 用户会话（需包含 hdlRefreshToken）
     * @return 刷新结果（success=true 表示刷新成功）
     */
    public LoginResult refreshToken(UserSession session) {
        String refreshToken = session.getHdlRefreshToken();
        if (refreshToken == null || refreshToken.isEmpty()) {
            LoginResult result = new LoginResult();
            result.setSuccess(false);
            result.setMsg("刷新令牌不存在");
            return result;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(HdlApiConstants.FIELD_GRANT_TYPE, HdlApiConstants.GRANT_TYPE_REFRESH_TOKEN);
        data.put(FIELD_REFRESH_TOKEN, refreshToken);

        // 刷新接口不需要鉴权（needAuth=false），但需要签名（needSign=true）
        HdlResponse resp = apiClient.post(HdlApiConstants.REFRESH_TOKEN, data, true, false, null);
        LoginResult result = new LoginResult();
        Map<String, Object> dataMap = resp.getDataAsMap();
        if (resp.success() && dataMap != null) {
            Object accessToken = dataMap.get(FIELD_ACCESS_TOKEN);
            if (accessToken != null) {
                long now = System.currentTimeMillis();
                long expiresIn = toLong(dataMap.get(HdlApiConstants.FIELD_EXPIRES_IN));
                long refreshExpiresIn = toLong(dataMap.get(HdlApiConstants.FIELD_REFRESH_EXPIRES_IN));
                if (refreshExpiresIn <= 0) {
                    refreshExpiresIn = DEFAULT_REFRESH_EXPIRES_IN_SECONDS;
                }
                session.setHdlAccessToken(accessToken.toString());
                // HDL 可能返回新的 refreshToken，若未返回则保留原有
                Object newRefreshToken = dataMap.get(FIELD_REFRESH_TOKEN);
                if (newRefreshToken != null) {
                    session.setHdlRefreshToken(newRefreshToken.toString());
                }
                session.setHdlAccessTokenExpiresAt(now + expiresIn * 1000L);
                session.setHdlRefreshTokenExpiresAt(now + refreshExpiresIn * 1000L);
                result.setSuccess(true);
                result.setExpiresIn(expiresIn);
                result.setRefreshExpiresIn(refreshExpiresIn);
                log.info("[HdlIotService] 刷新令牌成功: loginName={}, expiresIn={}s, refreshExpiresIn={}s",
                        session.getLoginName(), expiresIn, refreshExpiresIn);
                return result;
            }
        }
        result.setSuccess(false);
        result.setMsg(resp.getMsg() != null ? resp.getMsg() : "刷新令牌已过期");
        log.warn("[HdlIotService] 刷新令牌失败: loginName={}, msg={}", session.getLoginName(), result.getMsg());
        return result;
    }

    /**
     * 查询房屋列表。
     *
     * @param session 用户会话
     * @return 房屋列表
     */
    public List<HdlHome> queryHomeList(UserSession session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(HdlApiConstants.FIELD_HOME_TYPE, SEARCH_TYPE_ALL);
//        data.put(HdlApiConstants.FIELD_AUTO_GENERATE, false);

        HdlResponse resp = apiClient.post(HdlApiConstants.HOME_LIST, data, true,
                session.getHdlAccessToken());
        if (resp.success() && resp.getData() != null) {
            List<HdlHome> homes = parseHomes(resp.getData());
            session.setHomeCache(homes);
            session.setHomeCacheTime(System.currentTimeMillis());
            log.info("[HdlIotService] 查询房屋列表: {} 个", homes.size());
            return homes;
        }
        log.warn("[HdlIotService] 查询房屋失败: {}", resp.getMsg());
        return new ArrayList<>();
    }

    /**
     * 选择房屋（切换当前房屋）。
     *
     * @param homeName 房屋名称
     * @param session  用户会话
     * @return true 表示选择成功
     */
    public boolean selectHome(String homeName, UserSession session) {
        if (session.getHomeCache() == null) {
            return false;
        }
        for (HdlHome home : session.getHomeCache()) {
            if (Objects.equals(homeName, home.getHomeName())) {
                session.setCurrentHomeId(home.getHomeId());
                session.setCurrentHomeName(home.getHomeName());
                session.setDeviceCache(null);
                log.info("[HdlIotService] 切换房屋: {}", homeName);
                return true;
            }
        }
        return false;
    }

    /** 将 HDL 响应数据解析为房屋列表 */
    @SuppressWarnings("unchecked")
    private List<HdlHome> parseHomes(Object data) {
        List<HdlHome> result = new ArrayList<>();
        if (data instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) data;
            Object list = m.get(FIELD_HOMES);
            if (list == null) list = m.get(FIELD_LIST);
            if (list == null) list = m.get(FIELD_RECORDS);
            if (list instanceof List) {
                result.addAll(parseHomeList((List<?>) list));
            }
        } else if (data instanceof List) {
            // HDL 直接返回数组 [{homeId:..., homeName:...}]，未被 Map 包装
            result.addAll(parseHomeList((List<?>) data));
        }
        return result;
    }

    /** 逐条解析 List 中的房屋对象 */
    @SuppressWarnings("unchecked")
    private List<HdlHome> parseHomeList(List<?> list) {
        List<HdlHome> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map) {
                Map<String, Object> homeMap = (Map<String, Object>) item;
                HdlHome home = new HdlHome();
                home.setHomeId(String.valueOf(homeMap.getOrDefault("homeId", "")));
                home.setHomeName(String.valueOf(homeMap.getOrDefault("homeName", "")));
                home.setHomeType(String.valueOf(homeMap.getOrDefault("homeType", "")));
                home.setDeviceCount(toInt(homeMap.get("deviceCount")));
                home.setRemoteControl("true".equals(String.valueOf(homeMap.getOrDefault("isRemoteControl", "false")))
                        || "1".equals(String.valueOf(homeMap.getOrDefault("isRemoteControl", "0"))));
                result.add(home);
            }
        }
        return result;
    }

    private long toLong(Object val) {
        if (val instanceof Number n) return n.longValue();
        if (val instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }

    private int toInt(Object val) {
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }

    /**
     * HDL 登录结果。
     */
    @Data
    public static class LoginResult {
        private boolean success;
        private String msg;
        private long expiresIn;
        /** 刷新令牌有效期（秒），用于计算刷新令牌过期时间 */
        private long refreshExpiresIn;
    }
}
