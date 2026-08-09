package com.agent.scope.framework.controller;

import com.agent.scope.framework.exception.BusinessException;
import com.agent.scope.framework.exception.ErrorCode;
import com.agent.scope.framework.hdl.HdlHome;
import com.agent.scope.framework.hdl.HdlIotService;
import com.agent.scope.framework.model.LoginResult;
import com.agent.scope.framework.model.SessionStatus;
import com.agent.scope.framework.model.UserSession;
import com.agent.scope.framework.service.SessionManager;
import com.agent.scope.framework.vo.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Auth Controller —— HDL 登录认证（直连 HDL 后端）。
 *
 * <p>登录流程：
 * 1. 前端 POST /api/auth/login {loginName, loginPwd}
 * 2. AuthController 调 HdlIotService.login() → HDL 后端
 * 3. 成功后 sessionToken 返回前端，hdlAccessToken/hdlRefreshToken 存入 UserSession
 * 4. 前端后续请求带 X-Session-Token 头</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String HEADER_SESSION_TOKEN = "X-Session-Token";

    private final SessionManager sessionManager;
    private final HdlIotService hdlIotService;

    public AuthController(SessionManager sessionManager, HdlIotService hdlIotService) {
        this.sessionManager = sessionManager;
        this.hdlIotService = hdlIotService;
    }

    /**
     * 登录：直接调 HDL 后端 /basis-footstone/user/oauth/login。
     */
    @PostMapping("/login")
    public Response<LoginResult> login(@RequestBody Map<String, String> body) {
        String loginName = body.get("loginName");
        String loginPwd = body.get("loginPwd");

        if (loginName == null || loginPwd == null || loginName.isEmpty() || loginPwd.isEmpty()) {
            return Response.error(400, "用户名和密码不能为空");
        }

        UserSession session = sessionManager.createSession();
        HdlIotService.LoginResult hdlLoginResult = hdlIotService.login(loginName, loginPwd, session);

        if (hdlLoginResult.isSuccess()) {
            sessionManager.save(session);
            LoginResult data = new LoginResult()
                    .setSessionToken(session.getSessionToken())
                    .setLoginName(loginName)
                    .setExpiresIn(hdlLoginResult.getExpiresIn());
            log.info("[AuthController] 登录成功: loginName={}, token={}", loginName, session.getSessionToken());
            return Response.success(data);
        }

        sessionManager.destroy(session.getSessionToken());
        String msg = hdlLoginResult.getMsg() != null ? hdlLoginResult.getMsg() : "登录失败";
        log.warn("[AuthController] 登录失败: loginName={}, msg={}", loginName, hdlLoginResult.getMsg());
        return Response.error(20001, msg);
    }

    /**
     * 登出：销毁本地会话。
     */
    @PostMapping("/logout")
    public Response<Void> logout(@RequestHeader(value = HEADER_SESSION_TOKEN, required = false) String token) {
        if (token != null) {
            UserSession session = sessionManager.getSession(token);
            if (session != null) {
                session.clearToken();
            }
            sessionManager.destroy(token);
        }
        return Response.success();
    }

    /**
     * 状态查询：返回登录状态和房屋信息。
     *
     * <p>已登录但无房屋缓存 → 自动查询房屋列表；
     * 已登录但未选房屋 → needSelectHome=true。</p>
     */
    @GetMapping("/status")
    public Response<SessionStatus> status(@RequestHeader(value = HEADER_SESSION_TOKEN, required = false) String token) {
        if (token == null || token.isEmpty()) {
            return Response.success(new SessionStatus().setLoggedIn(false));
        }

        UserSession session = sessionManager.getSession(token);
        SessionStatus status = new SessionStatus();
        if (session != null && session.isLoggedIn()) {
            status.setLoggedIn(true)
                    .setLoginName(session.getLoginName())
                    .setCurrentHomeId(session.getCurrentHomeId())
                    .setCurrentHomeName(session.getCurrentHomeName());

            // 未选房屋时需要提示用户选择
            boolean needSelect = session.getCurrentHomeId() == null;

            // 自动查询房屋列表（如未缓存）
            if (session.getHomeCache() == null || session.getHomeCache().isEmpty()) {
                try {
                    List<HdlHome> homes = hdlIotService.queryHomeList(session);
                    status.setHomes(toHomeMaps(homes));
                    if (homes == null || homes.isEmpty()) {
                        status.setHomeQueryFailed(true);
                    }
                    sessionManager.save(session);
                } catch (Exception e) {
                    log.warn("[AuthController] 查询房屋列表失败: {}", e.getMessage());
                    status.setHomeQueryFailed(true);
                }
            } else {
                status.setHomes(toHomeMaps(session.getHomeCache()));
            }

            if (needSelect) {
                status.setNeedSelectHome(true);
            }
        } else {
            status.setLoggedIn(false);
        }
        return Response.success(status);
    }

    /**
     * 切换房屋。
     */
    @PostMapping("/switchHome")
    public Response<SessionStatus> switchHome(@RequestBody Map<String, String> body,
                                               @RequestHeader(value = HEADER_SESSION_TOKEN, required = false) String token) {
        UserSession session = requireSession(token);
        String homeName = body.get("homeName");
        if (homeName == null || homeName.isEmpty()) {
            return Response.error(400, "房屋名称不能为空");
        }

        boolean switched = hdlIotService.selectHome(homeName, session);
        if (switched) {
            sessionManager.save(session);
            SessionStatus status = new SessionStatus()
                    .setLoggedIn(true)
                    .setCurrentHomeId(session.getCurrentHomeId())
                    .setCurrentHomeName(session.getCurrentHomeName());
            log.info("[AuthController] 切换房屋: {}", homeName);
            return Response.success(status);
        }
        return Response.error(10004, "未找到房屋: " + homeName);
    }

    /** 校验会话 Token */
    private UserSession requireSession(String token) {
        if (token == null || token.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "Token 无效");
        }
        UserSession session = sessionManager.getSession(token);
        if (session == null || !session.isLoggedIn()) {
            throw new BusinessException(ErrorCode.SESSION_CONTEXT_MISSING, "Token 已过期或会话不存在");
        }
        return session;
    }

    /** HdlHome 列表 → 前端 Map 列表 */
    private List<Map<String, Object>> toHomeMaps(List<HdlHome> homes) {
        if (homes == null || homes.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>(homes.size());
        for (HdlHome home : homes) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("homeId", home.getHomeId());
            map.put("homeName", home.getHomeName());
            map.put("homeType", home.getHomeType());
            map.put("deviceCount", home.getDeviceCount());
            map.put("remoteControl", home.isRemoteControl());
            result.add(map);
        }
        return result;
    }
}
