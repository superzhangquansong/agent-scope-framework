package com.agent.scope.framework.hdl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON 工具（基于 Jackson 3.x）
 *
 * <p>Spring Boot 4.0 使用 Jackson 3.x，包路径由 com.fasterxml.jackson.databind
 * 变更为 tools.jackson.databind。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>所有方法均做空指针防护，永不出 NPE</li>
 *   <li>反序列化失败时返回兜底空对象，不抛异常</li>
 *   <li>集合初始容量按预期大小设定，避免扩容</li>
 * </ul>
 *
 * @author zqs
 * @since 1.0.0
 */
@Slf4j
@Component
public class HdlJsonUtil {

    /** 单例 ObjectMapper（线程安全） */
    private final ObjectMapper mapper = JsonMapper.builder().build();

    /** 默认 List 初始容量 */
    private static final int DEFAULT_LIST_CAPACITY = 16;

    /** 默认 Map 初始容量 */
    private static final int DEFAULT_MAP_CAPACITY = 16;

    /**
     * 对象转 JSON 字符串
     *
     * @param obj 待序列化对象，可为 null
     * @return JSON 字符串，obj 为 null 或失败时返回 "{}"
     */
    public String toJson(Object obj) {
        if (obj == null) {
            return "{}";
        }
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("[HdlJson] 序列化失败: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * JSON 字符串转对象
     *
     * @param json JSON 字符串
     * @param clazz 目标类型
     * @param <T>  泛型
     * @return 反序列化后的对象，失败时尝试返回默认实例，再失败返回 null
     */
    public <T> T parse(String json, Class<T> clazz) {
        if (json == null || json.isEmpty() || clazz == null) {
            return null;
        }
        try {
            return mapper.readValue(json, clazz);
        } catch (Exception e) {
            log.error("[HdlJson] 反序列化失败: {} json={}", e.getMessage(), json);
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception ex) {
                return null;
            }
        }
    }

    /**
     * JSON 字符串转 Map
     *
     * @param json JSON 字符串
     * @return Map，失败返回空 LinkedHashMap
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parseMap(String json) {
        if (json == null || json.isEmpty()) {
            return new LinkedHashMap<>(DEFAULT_MAP_CAPACITY);
        }
        try {
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.error("[HdlJson] parseMap失败: {}", e.getMessage());
            return new LinkedHashMap<>(DEFAULT_MAP_CAPACITY);
        }
    }

    /**
     * JSON 字符串转 List（泛型为 Map<String, Object>）
     *
     * @param json JSON 字符串
     * @return List，失败返回空 ArrayList
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> parseList(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>(DEFAULT_LIST_CAPACITY);
        }
        try {
            return mapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.error("[HdlJson] parseList失败: {}", e.getMessage());
            return new ArrayList<>(DEFAULT_LIST_CAPACITY);
        }
    }

    /**
     * 获取内部 ObjectMapper（供 SpkSchemaRegistry 等需要 readTree 的场景使用）
     *
     * @return ObjectMapper 实例，永不为 null
     */
    public ObjectMapper getMapper() {
        return mapper;
    }
}
