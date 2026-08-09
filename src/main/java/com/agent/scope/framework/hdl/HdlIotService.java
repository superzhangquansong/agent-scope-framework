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
                session.setHdlAccessToken(accessToken.toString());
                if (refreshToken != null) {
                    session.setHdlRefreshToken(refreshToken.toString());
                }
                session.setLoginName(loginName);
                result.setSuccess(true);
                result.setExpiresIn(toLong(dataMap.get(HdlApiConstants.FIELD_EXPIRES_IN)));
                log.info("[HdlIotService] 登录成功: loginName={}", loginName);
                return result;
            }
        }
        result.setSuccess(false);
        result.setMsg(resp.getMsg() != null ? resp.getMsg() : "用户名或密码不正确");
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
    }
}
