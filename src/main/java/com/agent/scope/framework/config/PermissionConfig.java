package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.core.permission.PermissionBehavior;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * AgentScope 2.0 GA 特性十四/十五：权限系统 + HITL 人机交互配置
 * <p>
 * 权限系统拦截 agent 的每一次工具调用，给出三种决策之一：
 * - ALLOW（允许）：直接执行工具
 * - ASK（需用户批准）：暂停执行，等待人工确认
 * - DENY（拒绝）：拒绝执行
 * </p>
 * <p>
 * 三态决策基于三个组件综合判断：
 * - Rules：针对每个 tool 的显式 allow/deny/ask 规则（最高优先级）
 * - Mode：全局静态策略（DEFAULT/ACCEPT_EDITS/EXPLORE/BYPASS/DONT_ASK）
 * - Built-in Checks：工具自身的运行时安全检查（不可绕过）
 * </p>
 * <p>
 * HITL（Human-in-the-Loop）与权限系统的 ASK 状态联动：
 * 敏感工具调用时暂停执行，等待人工确认/拒绝，支持精确恢复。
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class PermissionConfig {

    private final AgentScopeProperties properties;

    /**
     * 权限上下文状态 Bean（始终创建，根据 enabled 配置决定行为）。
     * <p>
     * 官方文档：<a href="https://java.agentscope.io/v2/zh/docs/building-blocks/permission-system.html">Permission System</a>
     * </p>
     * <p>
     * 权限引擎评估流程：deny → ask → tool-checks → allow → ... → BYPASS → DONT_ASK → otherwise ASK。
     * </p>
     * <p>
     * <b>设计策略——BYPASS 白名单模式</b>：
     * <ul>
     *   <li>BYPASS 模式：默认放行所有工具（步骤7），"放行一切（deny / ask 规则仍生效）"</li>
     *   <li>ASK 规则（步骤2）先于 BYPASS（步骤7）评估，因此 Nacos ask-tools 中的敏感工具
     *       仍会触发 HITL 人机交互确认</li>
     *   <li>Deny 规则不可绕过，即使在 BYPASS 模式下也照常生效</li>
     * </ul>
     * </p>
     * <p>
     * <b>enabled=false 时</b>：返回纯 BYPASS 模式（无任何 ASK 规则），所有工具直接放行。
     * 必须始终创建 Bean，否则框架退回默认 DEFAULT 模式（ASK 所有工具），
     * 导致 enabled=false 不生效。
     * </p>
     *
     * @return PermissionContextState 权限上下文状态
     */
    @Bean
    public PermissionContextState permissionContextState() {
        AgentScopeProperties.Permission permission = properties.getPermission();

        // enabled=false：纯 BYPASS，所有工具直接放行（含内置安全检查工具如 batch_control_device）
        // 注意：BYPASS 模式本身无法绕过框架内置安全检查，
        // 真正的自动批准由 RequireUserConfirmHandler 根据本配置实现
        if (!permission.isEnabled()) {
            log.info("[PermissionConfig] 权限已禁用 (enabled=false)，所有工具直接放行");
            return PermissionContextState.builder()
                    .mode(PermissionMode.BYPASS)
                    .build();
        }

        List<String> askTools = permission.getAskTools();

        // BYPASS 模式：除显式 deny/ask 规则外，其余工具默认直接放行
        PermissionContextState.Builder builder = PermissionContextState.builder()
                .mode(PermissionMode.BYPASS);

        // 为 Nacos ask-tools 中的敏感工具注册 ASK 规则，
        // ASK 步骤先于 BYPASS 步骤，敏感工具仍会触发 HITL 确认
        if (askTools != null) {
            for (String toolName : askTools) {
                builder.addAskRule(toolName,
                        new PermissionRule(toolName, null, PermissionBehavior.ASK, "nacos"));
            }
        }

        log.info("[PermissionConfig] 权限上下文: mode=BYPASS, askTools={}, 其余工具默认放行", askTools);
        return builder.build();
    }
}
