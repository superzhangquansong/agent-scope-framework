package com.agent.scope.framework.controller;

import com.agent.scope.framework.context.SessionContext;
import com.agent.scope.framework.hdl.port.HdlApiPort;
import com.agent.scope.framework.model.UserSession;
import com.agent.scope.framework.service.SessionManager;
import com.agent.scope.framework.vo.ToolResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 房屋 REST 代理控制器。
 *
 * <p>前端 HomeSelectModal 等组件需要直接调用 HDL API 获取房屋列表，
 * 本 Controller 作为代理转发请求到 HDL 云端 API。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final HdlApiPort hdlApiPort;
    private final SessionManager sessionManager;

    /**
     * 查询房屋列表。
     *
     * @param sessionToken 会话令牌
     * @return 房屋列表
     */
    @PostMapping("/list")
    public ToolResultVO listHomes(@RequestHeader("X-Session-Token") String sessionToken) {
        UserSession session = sessionManager.getSession(sessionToken);
        if (session == null || !session.isLoggedIn()) {
            log.warn("[HomeController] 会话无效: token={}", sessionToken);
            return ToolResultVO.failure(401, "未登录或会话已过期");
        }
        SessionContext ctx = SessionContext.builder()
                .userId(session.getLoginName())
                .houseId(session.getCurrentHomeId())
                .accessToken(session.getHdlAccessToken())
                .build();
        return hdlApiPort.queryHomeList(ctx);
    }
}
