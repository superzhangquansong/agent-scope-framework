package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.core.permission.PermissionBehavior;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(prefix = "scope.agentscope.permission", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class PermissionConfig {

    private final AgentScopeProperties properties;

    /**
     * 权限上下文状态 Bean
     * <p>
     * 配置权限系统的全局模式：
     * - 模式：ACCEPT_EDITS（只读工具自动放行，非只读工具走默认 ASK）
     * </p>
     * <p>
     * <b>关键设计——不注册显式 ASK 规则的原因</b>：
     * <p>
     * AgentScope 2.0.0 权限引擎的评估顺序为 {@code deny → ask → allow → default}。
     * 若注册了显式 ASK 规则（步骤2），则即使用户确认后通过 ConfirmResult 添加了 ALLOW 规则（步骤4），
     * ASK 规则仍会先匹配，导致同一工具的后续调用再次触发 HITL，形成无限确认循环。
     * </p>
     * <p>
     * 解决方案：不注册显式 ASK 规则。在 ACCEPT_EDITS 模式下：
     * <ul>
     *   <li>只读工具（readOnly=true）：由 checkExploreMode 自动 ALLOW（步骤3）</li>
     *   <li>非只读工具（如 batch_control_device）：无显式规则 → 走 default ASK（步骤6）</li>
     * </ul>
     * 用户首次确认后，ConfirmResult 添加的 ALLOW 规则在步骤4匹配（先于步骤6的 default ASK），
     * 从而实现"首次确认后自动放行后续相同工具调用"。
     * </p>
     *
     * @return PermissionContextState 权限上下文状态
     */
    @Bean
    public PermissionContextState permissionContextState() {
        AgentScopeProperties.Permission permission = properties.getPermission();
        List<String> askTools = permission.getAskTools();

        log.info("[PermissionConfig] 创建权限上下文: mode=ACCEPT_EDITS, askTools={} (不注册显式ASK规则，依赖默认ASK行为)", askTools);

        // 不注册显式 ASK 规则：ACCEPT_EDITS 模式下非只读工具自动走 default ASK，
        // 用户确认后通过 ConfirmResult 添加 ALLOW 规则可在 default ASK 之前匹配，避免循环确认
        return PermissionContextState.builder()
                .mode(PermissionMode.ACCEPT_EDITS)
                .build();
    }
}
