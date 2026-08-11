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
 * 产品/商城 REST 代理控制器。
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {

    private final HdlApiPort hdlApiPort;
    private final SessionManager sessionManager;

    private SessionContext resolveContext(String sessionToken) {
        UserSession session = sessionManager.getSession(sessionToken);
        if (session == null || !session.isLoggedIn()) return null;
        return SessionContext.builder()
                .userId(session.getLoginName())
                .houseId(session.getCurrentHomeId())
                .accessToken(session.getHdlAccessToken())
                .build();
    }

    @PostMapping("/detail")
    public ToolResultVO getDetail(@RequestHeader("X-Session-Token") String sessionToken,
                                   @RequestBody Map<String, Object> body) {
        SessionContext ctx = resolveContext(sessionToken);
        if (ctx == null) return ToolResultVO.failure(401, "未登录或会话已过期");
        String productId = (String) body.get("productId");
        if (productId == null || productId.isBlank()) return ToolResultVO.failure(400, "缺少 productId");
        return hdlApiPort.queryProductDetail(productId, ctx);
    }

    @PostMapping("/search")
    public ToolResultVO search(@RequestHeader("X-Session-Token") String sessionToken,
                                @RequestBody Map<String, Object> body) {
        SessionContext ctx = resolveContext(sessionToken);
        if (ctx == null) return ToolResultVO.failure(401, "未登录或会话已过期");
        String productName = (String) body.get("productName");
        if (productName == null || productName.isBlank()) return ToolResultVO.failure(400, "缺少 productName");
        return hdlApiPort.searchProductList(productName, ctx);
    }
}
