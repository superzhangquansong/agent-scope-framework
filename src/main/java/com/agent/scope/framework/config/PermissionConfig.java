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
@ConditionalOnProperty(prefix = "scope.agentscope.permission", name = "enabled", havingValue = "true")
public class PermissionConfig {

    private final AgentScopeProperties properties;

    /**
     * 权限上下文状态 Bean
     * <p>
     * 配置权限系统的全局模式和工具级规则：
     * - 模式：DEFAULT（所有操作都需要显式规则或用户确认，最安全）
     * - 拒绝规则：batch_control_device（批量设备控制需审批）
     * </p>
     * <p>
     * 权限规则由 scope.agentscope.permission.ask-tools 配置项指定，
     * 列出需要人工审批的敏感工具名称（List<String> 形式，支持 Nacos 热更新）。
     * </p>
     *
     * @return PermissionContextState 权限上下文状态
     */
    @Bean
    public PermissionContextState permissionContextState() {
        AgentScopeProperties.Permission permission = properties.getPermission();
        List<String> askTools = permission.getAskTools();

        log.info("[PermissionConfig] 创建权限上下文: mode=DEFAULT, askTools={}", askTools);

        PermissionContextState.Builder builder = PermissionContextState.builder()
                .mode(PermissionMode.DEFAULT);

        // 配置需要人工审批的敏感工具（如批量设备控制、下单等）
        if (askTools != null && !askTools.isEmpty()) {
            for (String toolName : askTools) {
                if (toolName == null || toolName.isBlank()) {
                    continue;
                }
                String trimmed = toolName.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                builder.addAskRule(trimmed,
                        new PermissionRule(trimmed, null, PermissionBehavior.ASK, "userSettings"));
                log.info("[PermissionConfig] 注册 ASK 规则: tool={}", trimmed);
            }
        }

        return builder.build();
    }
}
