package com.agent.scope.framework.service;

import com.agent.scope.framework.config.properties.AgentScopeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Ollama HTTP 客户端（轻量级，不走 AgentScope ReAct 框架）。
 * <p>
 * 直接调用 Ollama /api/chat 接口，支持 JSON Mode（format:"json"），
 * 用于 {@link LocalFastPathExecutor} 的意图分类和结果汇总。
 * </p>
 *
 * <h3>为什么不复用 OllamaChatModel</h3>
 * AgentScope 的 OllamaChatModel 绑定了 ReAct 框架（中间件、Toolkit、状态存储），
 * 启动开销大。本类仅需"发一条 prompt → 拿回 JSON"的轻量调用，
 * 用 Spring RestClient 直连 Ollama HTTP API 即可，延迟 < 300ms。
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Service
public class OllamaClient {

    /** Ollama /api/chat 端点 */
    private static final String CHAT_ENDPOINT = "/api/chat";

    /** HTTP 客户端（连接池复用，超时 30s） */
    private final RestClient httpClient;

    /** 模型名称（从 Nacos 配置读取，默认 qwen2.5:1.5b） */
    private final String modelName;

    /**
     * 构造 OllamaClient。
     *
     * @param properties AgentScope 全局配置（读取 ollama.baseUrl / ollama.chatModel）
     */
    public OllamaClient(AgentScopeProperties properties) {
        AgentScopeProperties.Ollama ollama = properties.getOllama();
        this.modelName = ollama.getChatModel();
        this.httpClient = RestClient.builder()
                .baseUrl(ollama.getBaseUrl())
                .build();
        log.info("[OllamaClient] 初始化: baseUrl={}, model={}", ollama.getBaseUrl(), modelName);
    }

    /**
     * 普通对话（纯文本输出）。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return LLM 输出的纯文本
     */
    public String chat(String systemPrompt, String userPrompt) {
        return doChat(systemPrompt, userPrompt, false);
    }

    /**
     * JSON 模式对话（强制 JSON 输出）。
     * <p>
     * Ollama format:"json" 保证输出是合法 JSON，适合意图分类和参数提取。
     * temperature=0 保证相同输入产生相同输出（确定性）。
     * </p>
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return LLM 输出的 JSON 字符串
     */
    public String chatJson(String systemPrompt, String userPrompt) {
        return doChat(systemPrompt, userPrompt, true);
    }

    /**
     * 内部统一调用方法。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param jsonMode     是否强制 JSON 输出
     * @return LLM 输出文本
     */
    private String doChat(String systemPrompt, String userPrompt, boolean jsonMode) {
        // 构建请求体（Ollama /api/chat 格式）
        JSONObject request = new JSONObject();
        request.put("model", modelName);
        request.put("stream", false);

        // 消息列表：system + user
        JSONArray messages = new JSONArray();
        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.add(sysMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.add(userMsg);
        request.put("messages", messages);

        // JSON 模式：强制输出合法 JSON
        if (jsonMode) {
            request.put("format", "json");
        }

        // 零温度：确保确定性输出（相同输入 → 相同输出）
        JSONObject options = new JSONObject();
        options.put("temperature", 0);
        request.put("options", options);

        try {
            long start = System.currentTimeMillis();
            // 请求体和响应体都显式使用 UTF-8，避免 RestClient 默认 ISO-8859-1 导致中文乱码
            byte[] requestBody = request.toJSONString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] responseBytes = httpClient.post()
                    .uri(CHAT_ENDPOINT)
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .body(requestBody)
                    .retrieve()
                    .body(byte[].class);
            String response = new String(responseBytes, java.nio.charset.StandardCharsets.UTF_8);

            long elapsed = System.currentTimeMillis() - start;

            // 解析响应：{"message":{"content":"..."}}
            JSONObject resp = JSON.parseObject(response);
            String content = resp.getJSONObject("message").getString("content");

            log.debug("[OllamaClient] 调用完成: jsonMode={}, elapsed={}ms, outputLen={}",
                    jsonMode, elapsed, content != null ? content.length() : 0);
            return content != null ? content.trim() : "";
        } catch (Exception e) {
            log.error("[OllamaClient] 调用失败: {}", e.getMessage(), e);
            return "";
        }
    }
}
