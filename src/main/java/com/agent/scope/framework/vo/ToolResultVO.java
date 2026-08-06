package com.agent.scope.framework.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 通用工具结果 VO。
 *
 * <p>所有 @Tool 方法返回此对象，统一结构便于 LLM 解析。
 * 替代直接返回 Map&lt;String, Object&gt;，符合阿里巴巴规范"使用强类型对象而非 Map"。</p>
 *
 * <p>结构：</p>
 * <ul>
 *   <li>{@code success}：工具调用是否成功</li>
 *   <li>{@code message}：成功/失败消息（LLM 据此生成自然语言回复）</li>
 *   <li>{@code data}：业务数据（Object 类型，兼容任意 HDL API 响应结构）</li>
 *   <li>{@code errorCode}：错误码（失败时填充）</li>
 *   <li>{@code routePath}：前端路由路径（用于 HarnessAgent 模式下前端渲染对应业务界面）</li>
 *   <li>{@code broadcastText}：成功播报文本（用于 SSE 推送时的语音播报）</li>
 * </ul>
 *
 * <p>新增 routePath 和 broadcastText 字段，让 @Tool 方法在返回业务数据时同时告知前端
 * 应该渲染哪个界面（如 /device/list、/scene/list）。这样 HarnessAgent 模式下前端
 * 也能拿到与 YML 管道模式相同结构的 SseResponse，保证前后端一致性。</p>
 *
 * <p>data 字段类型为 {@code Object}。原因：AgentScope 2.0.0 JAR 内部使用 Jackson 2.x ObjectMapper 序列化 ToolResultVO，
 * 遇到 Jackson 3.x 的 JsonNode 类型时会回退到 POJO Bean 序列化，
 * 输出 {@code {"nodeType":"ARRAY","null":false,...}} 等元数据而非真实业务数据，
 * 导致 LLM 在 ReAct 第二轮推理中看不到工具返回的真实数据，生成幻觉回复。
 * 使用 Object 类型后，AgentScope JAR 能正确序列化 List/Map 等普通 Java 对象。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ToolResultVO {

    /**
     * 纯文本路由：无工具调用场景下的默认路由（前端通用结果页）
     */
    public static final String ROUTE_TEXT_ONLY = "/result/generic";

    /**
     * 错误路由：工具调用失败时使用（前端错误结果页）
     */
    public static final String ROUTE_ERROR = "/result/error";

    /**
     * 调用是否成功
     */
    private boolean success;

    /**
     * 消息（成功提示或错误描述）
     */
    private String message;

    /**
     * 业务数据（Object 类型，兼容任意 HDL API 响应结构）。
     *
     * <p>Object 类型解决 AgentScope JAR 内部 Jackson 2.x
     * 无法正确序列化 Jackson 3.x JsonNode 的问题。</p>
     */
    private Object data;

    /**
     * 错误码（失败时填充）
     */
    private Integer errorCode;

    /**
     * 前端路由路径。
     *
     * <p>用于 HarnessAgent 模式下，@Tool 方法告知 AgentExecutor
     * 应该让前端渲染哪个界面。与 frontend-routes.yml 中的 path 对应。</p>
     *
     * <p>常见值：</p>
     * <ul>
     *   <li>/device/list —— 设备列表界面</li>
     *   <li>/device/detail —— 设备详情界面</li>
     *   <li>/device/status —— 设备控制界面</li>
     *   <li>/scene/list —— 场景列表界面</li>
     *   <li>/home/list —— 房屋列表界面</li>
     *   <li>/product/list —— 产品列表界面</li>
     *   <li>/product/detail —— 产品详情界面</li>
     *   <li>/cart/list —— 购物车界面</li>
     *   <li>/result/generic —— 通用结果页（纯文本回复）</li>
     * </ul>
     */
    private String routePath;

    /**
     * 成功播报文本。
     *
     * <p>用于 SSE 推送时的语音播报。若为 null，AgentExecutor 会使用
     * LLM 生成的 reply 作为播报文本。</p>
     */
    private String broadcastText;

    /**
     * 是否需要用户确认。
     *
     * <p>为 true 时，前端展示待确认数据界面，用户点击"确认提交"后
     * 才真正执行创建/修改操作，避免数据错误直接落库。</p>
     *
     * <p>使用场景：场景创建等需要二次确认的业务。</p>
     */
    private Boolean needConfirm;

    /**
     * 确认提示文本（配合 needConfirm 使用）。
     *
     * <p>前端展示确认界面时的提示文案，如"请确认场景信息后提交"。</p>
     */
    private String askUser;

    /**
     * 确认后执行的 Agent ID（配合 needConfirm 使用）。
     *
     * <p>当 needConfirm=true 时，ChatController 将 data 中的业务参数保存到 Redis 待确认操作，
     * 用户说"确认"/"保存"后，ChatController 消费待确认操作并调用此 agentId 执行实际业务操作。</p>
     *
     * <p>使用场景：AgentScope 模式下 SceneTool.createScene 设置 needConfirm=true 时，
     * 同时设置 confirmAgentId="scene-create-confirm"，让 ChatController 知道确认后执行哪个 YML Agent。
     * YML 管道模式不需要此字段（YmlPipelineExecutor 内部通过 AgentDefinition.confirmAgentId 处理）。</p>
     */
    private String confirmAgentId;

    /**
     * 前端附加数据（不序列化给 LLM）。
     *
     * <p>使用 {@link JsonIgnore} 注解，AgentScope 框架序列化 ToolResultVO 为 observation
     * 返回给 LLM 时，此字段被忽略，LLM 看不到其中的内容（如文件下载链接）。
     * AgentScopeIntegration 会提取此字段，合并到 SseResponse.data 中供前端使用。</p>
     *
     * <p>使用场景：知识库检索返回的文件下载链接，前端需要展示下载入口，
     * 但 LLM 不应看到链接 URL，避免在回复文本中拼接链接。</p>
     */
    @JsonIgnore
    private Map<String, Object> frontendExtra;

    /**
     * 构建成功结果（含路由路径和播报文本）。
     *
     * @param message       成功消息
     * @param data          业务数据
     * @param routePath     前端路由路径
     * @param broadcastText 成功播报文本
     * @return ToolResultVO 实例
     */
    public static ToolResultVO success(String message, Object data, String routePath, String broadcastText) {
        return ToolResultVO.builder()
                .success(true)
                .message(message)
                .data(data)
                .routePath(routePath)
                .broadcastText(broadcastText)
                .build();
    }

    /**
     * 构建成功结果（兼容旧调用，routePath 默认 /result/generic）。
     *
     * @param message 成功消息
     * @param data    业务数据
     * @return ToolResultVO 实例
     */
    public static ToolResultVO success(String message, Object data) {
        return ToolResultVO.builder()
                .success(true)
                .message(message)
                .data(data)
                .routePath(ROUTE_TEXT_ONLY)
                .build();
    }

    /**
     * 构建失败结果。
     *
     * @param errorCode 错误码
     * @param message   错误消息
     * @return ToolResultVO 实例
     */
    public static ToolResultVO failure(int errorCode, String message) {
        return ToolResultVO.builder()
                .success(false)
                .errorCode(errorCode)
                .message(message)
                .routePath(ROUTE_ERROR)
                .build();
    }
}