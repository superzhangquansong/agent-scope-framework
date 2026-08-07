package com.agent.scope.framework.config;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import com.agent.scope.framework.constant.BusinessConst;
import com.agent.scope.framework.exception.BusinessException;
import com.agent.scope.framework.exception.ErrorCode;
import io.agentscope.extensions.channel.feishu.FeishuChannel;
import io.agentscope.extensions.channel.feishu.FeishuChannelRegistry;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.gateway.channel.ChannelConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * AgentScope 2.0 GA 特性二十四：Channel 通信配置
 * <p>
 * 接入外部消息平台（飞书），实现 Agent 与 IM 平台的双向通信：
 * <ul>
 *   <li>入站：飞书 Webhook 消息 → FeishuCallbackController → FeishuChannel → Gateway → HarnessAgent</li>
 *   <li>出站：HarnessAgent 响应 → Gateway → FeishuChannel → 飞书 OpenAPI</li>
 * </ul>
 * </p>
 * <p>
 * Channel 系统支持 7 层路由绑定：peer > parentPeer > guild+roles > guild > team > account > channel，
 * 按 (userId, sessionId) 自动隔离会话。
 * </p>
 * <p>
 * 注意：本类命名为 {@code ChannelGatewayConfig}，以避免与 AgentScope 框架内置的
 * {@code io.agentscope.harness.agent.gateway.channel.ChannelConfig} 类型同名冲突。
 * </p>
 * <p>
 * 配置示例（Nacos application-config.yml）：
 * <pre>
 * scope:
 *   agentscope:
 *     advanced:
 *       channel-enabled: true
 *     channel:
 *       feishu:
 *         channel-id: feishu-main
 *         app-id: cli_xxxx
 *         app-secret: xxxx
 *         encrypt-key: xxxx
 *         verification-token: xxxx
 *         callback-path: /feishu/callback
 *         api-base: https://open.feishu.cn
 * </pre>
 * </p>
 * <p>
 * 装配的 Bean：
 * <ul>
 *   <li>{@link ChannelConfig}：渠道路由配置，defaultAgentId 指向 HarnessAgent</li>
 *   <li>{@link FeishuChannelRegistry}：飞书渠道注册表（单例），供 FeishuCallbackController 查找渠道</li>
 *   <li>{@link FeishuChannel}：飞书渠道适配器，通过 {@link FeishuChannel#fromProperties} 工厂创建</li>
 * </ul>
 * </p>
 * <p>
 * 说明：{@code ChannelRouter} 无需单独装配 Bean —— {@link FeishuChannel#fromProperties} 内部已基于
 * {@link ChannelConfig#defaultAgentId()} 自行创建 {@code ChannelRouter} 实例并注入到渠道中。
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.advanced", name = "channel-enabled", havingValue = "true")
public class ChannelGatewayConfig {

    private final AgentScopeProperties properties;

    /**
     * HarnessAgent（由 HarnessAgentConfig 装配的系统主入口 Agent）。
     * <p>
     * Channel 的 defaultAgentId 指向该 Agent，入站消息经路由后由其处理。
     * </p>
     */
    private final HarnessAgent harnessAgent;

    /** Feishu 渠道 Channel ID（从 Nacos 配置读取，默认 feishu-main） */
    @Value("${scope.agentscope.channel.feishu.channel-id:" + BusinessConst.FEISHU_CHANNEL_DEFAULT_ID + "}")
    private String feishuChannelId;

    /** Feishu 应用 App ID（从 Nacos 配置读取，必填） */
    @Value("${scope.agentscope.channel.feishu.app-id:}")
    private String feishuAppId;

    /** Feishu 应用 App Secret（从 Nacos 配置读取，必填） */
    @Value("${scope.agentscope.channel.feishu.app-secret:}")
    private String feishuAppSecret;

    /** Feishu 事件订阅加密密钥（从 Nacos 配置读取，可选，配置后启用回调加密） */
    @Value("${scope.agentscope.channel.feishu.encrypt-key:}")
    private String feishuEncryptKey;

    /** Feishu 事件订阅验证 Token（从 Nacos 配置读取，可选） */
    @Value("${scope.agentscope.channel.feishu.verification-token:}")
    private String feishuVerificationToken;

    /** Feishu Webhook 回调路径（从 Nacos 配置读取，默认 /feishu/callback） */
    @Value("${scope.agentscope.channel.feishu.callback-path:" + BusinessConst.FEISHU_DEFAULT_CALLBACK_PATH + "}")
    private String feishuCallbackPath;

    /** Feishu 开放平台 API 基址（从 Nacos 配置读取，默认 https://open.feishu.cn） */
    @Value("${scope.agentscope.channel.feishu.api-base:" + BusinessConst.FEISHU_DEFAULT_API_BASE + "}")
    private String feishuApiBase;

    /**
     * ChannelConfig Bean：渠道路由配置。
     * <p>
     * 设置 channelId 与 defaultAgentId（指向 HarnessAgent），ChannelRouter 据此将入站消息
     * 路由到对应 Agent。defaultAgentId 取自 {@link AgentScopeProperties#getHarnessAgentName()}，
     * 与 HarnessAgentConfig 中装配的 Agent 名称保持一致。
     * </p>
     *
     * @return 渠道路由配置
     */
    @Bean
    public ChannelConfig channelConfig() {
        try {
            String defaultAgentId = properties.getHarnessAgentName();
            ChannelConfig config = ChannelConfig.of(feishuChannelId, defaultAgentId);
            log.info("[ChannelGatewayConfig] ChannelConfig 装配完成: channelId={}, defaultAgentId={}",
                    feishuChannelId, defaultAgentId);
            return config;
        } catch (Exception e) {
            log.error("[ChannelGatewayConfig] ChannelConfig 装配失败", e);
            throw new BusinessException(ErrorCode.CONFIG_LOAD_FAILED, "ChannelConfig 装配失败", e);
        }
    }

    /**
     * FeishuChannelRegistry Bean：飞书渠道注册表（单例）。
     * <p>
     * 暴露 {@link FeishuChannelRegistry#instance()} 为 Spring Bean，供
     * {@code FeishuCallbackController} 注入并按 channelId 查找渠道，
     * 将 Webhook 回调消息分发到对应 {@link FeishuChannel}。
     * </p>
     *
     * @return 飞书渠道注册表单例
     */
    @Bean
    public FeishuChannelRegistry feishuChannelRegistry() {
        FeishuChannelRegistry registry = FeishuChannelRegistry.instance();
        log.info("[ChannelGatewayConfig] FeishuChannelRegistry 装配完成（单例）");
        return registry;
    }

    /**
     * FeishuChannel Bean：飞书渠道适配器。
     * <p>
     * 通过 {@link FeishuChannel#fromProperties} 工厂方法创建，避免直接调用 private 构造函数。
     * 工厂内部完成以下装配：
     * <ul>
     *   <li>从 properties Map 解析 {@code FeishuChannelProperties}（appId/appSecret 必填）</li>
     *   <li>构建 FeishuCrypto（启用加密时）、FeishuAccessTokenProvider、FeishuOutboundClient</li>
     *   <li>构建 FeishuInboundMapper、IdempotencyStore、BotLoopGuard</li>
     *   <li>基于 {@link ChannelConfig#defaultAgentId()} 创建 ChannelRouter（无需外部注入）</li>
     *   <li>获取 {@link FeishuChannelRegistry#instance()} 单例</li>
     * </ul>
     * </p>
     * <p>
     * 创建后调用 {@link FeishuChannelRegistry#register} 注册到注册表，
     * 使 {@code FeishuCallbackController} 能按 channelId 路由 Webhook 回调。
     * </p>
     *
     * @param channelConfig        渠道路由配置
     * @param feishuChannelRegistry 飞书渠道注册表
     * @return 飞书渠道适配器实例
     */
    @Bean
    @ConditionalOnExpression("'${scope.agentscope.channel.feishu.app-id:}' != ''")
    public FeishuChannel feishuChannel(ChannelConfig channelConfig,
                                       FeishuChannelRegistry feishuChannelRegistry) {
        try {
            // 构建 properties Map（键名与 FeishuChannelProperties 字段一致，camelCase）
            Map<String, Object> propsMap = new HashMap<>(8);
            propsMap.put(BusinessConst.FEISHU_PROP_KEY_APP_ID, feishuAppId);
            propsMap.put(BusinessConst.FEISHU_PROP_KEY_APP_SECRET, feishuAppSecret);
            propsMap.put(BusinessConst.FEISHU_PROP_KEY_ENCRYPT_KEY, feishuEncryptKey);
            propsMap.put(BusinessConst.FEISHU_PROP_KEY_VERIFICATION_TOKEN, feishuVerificationToken);
            propsMap.put(BusinessConst.FEISHU_PROP_KEY_CALLBACK_PATH, feishuCallbackPath);
            propsMap.put(BusinessConst.FEISHU_PROP_KEY_API_BASE, feishuApiBase);

            FeishuChannel channel = FeishuChannel.fromProperties(feishuChannelId, channelConfig, propsMap);
            // 注册到注册表，供 FeishuCallbackController 按 channelId 路由 Webhook 回调
            feishuChannelRegistry.register(channel);

            boolean encrypted = feishuEncryptKey != null && !feishuEncryptKey.isBlank();
            log.info("[ChannelGatewayConfig] FeishuChannel 装配完成: channelId={}, appId={}, callbackPath={}, encrypted={}",
                    feishuChannelId, feishuAppId, feishuCallbackPath, encrypted);
            return channel;
        } catch (Exception e) {
            log.error("[ChannelGatewayConfig] FeishuChannel 装配失败: channelId={}", feishuChannelId, e);
            throw new BusinessException(ErrorCode.CONFIG_LOAD_FAILED, "FeishuChannel 装配失败", e);
        }
    }
}
