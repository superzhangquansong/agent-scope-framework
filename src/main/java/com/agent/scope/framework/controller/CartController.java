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
 * 购物车 REST 代理控制器。
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

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

    @PostMapping("/add")
    public ToolResultVO addToCart(@RequestHeader("X-Session-Token") String sessionToken,
                                   @RequestBody Map<String, Object> body) {
        SessionContext ctx = resolveContext(sessionToken);
        if (ctx == null) return ToolResultVO.failure(401, "未登录或会话已过期");
        String skuId = (String) body.get("skuId");
        String productId = (String) body.get("productId");
        int quantity = body.get("quantity") instanceof Number n ? n.intValue() : 1;
        String erpNo = (String) body.get("erpNo");
        if (skuId == null || productId == null) return ToolResultVO.failure(400, "缺少 skuId 或 productId");
        return hdlApiPort.addCart(skuId, productId, quantity, erpNo, ctx);
    }

    @PostMapping("/list")
    public ToolResultVO listCart(@RequestHeader("X-Session-Token") String sessionToken) {
        SessionContext ctx = resolveContext(sessionToken);
        if (ctx == null) return ToolResultVO.failure(401, "未登录或会话已过期");
        return hdlApiPort.queryCartList(ctx);
    }
}
