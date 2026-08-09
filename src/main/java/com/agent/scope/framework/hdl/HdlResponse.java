package com.agent.scope.framework.hdl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HDL API 统一响应。
 *
 * <p>HDL 网关返回结构：{ isSuccess, code, message, data }。</p>
 *
 * <p>data 字段类型不固定（可能是 Map 或 List），定义为 Object，
 * 由调用方通过 {@link #getDataAsMap()} 或 {@link #getDataAsList()} 按需转型。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Data
public class HdlResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 是否成功（部分接口用此字段） */
    private Boolean isSuccess;

    /** 业务状态码（部分接口用 0 表示成功） */
    private Integer code;

    /**
     * 提示信息。
     *
     * <p>HDL 后端实际返回字段名为 message，通过 @JsonAlias 同时兼容 msg 字段名。</p>
     */
    @JsonAlias({"message", "msg"})
    private String msg;

    /** 业务数据（可能是 Map 或 List，按接口而定） */
    private Object data;

    /**
     * 综合判断是否成功（兼容 isSuccess / code 两种风格）。
     *
     * @return true 表示成功
     */
    public boolean success() {
        if (isSuccess != null) {
            return isSuccess;
        }
        if (code != null) {
            return code == 0;
        }
        return false;
    }

    /**
     * 将 data 当作 Map 读取。
     *
     * @return Map 形式的 data；若 data 不是 Map 则返回 null
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getDataAsMap() {
        if (data == null) {
            return null;
        }
        if (data instanceof Map<?, ?> m) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                result.put(String.valueOf(e.getKey()), e.getValue());
            }
            return result;
        }
        // 如果 data 是 String，尝试用 fastjson2 解析
        if (data instanceof String s && !s.isEmpty()) {
            try {
                JSONObject obj = JSON.parseObject(s);
                if (obj != null) {
                    return new LinkedHashMap<>(obj);
                }
            } catch (Exception ignored) {
                // 解析失败返回 null
            }
        }
        return null;
    }

    /**
     * 将 data 当作 List 读取。
     *
     * @return List 形式的 data；若 data 不是 List 则返回 null
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getDataAsList() {
        if (data == null) {
            return null;
        }
        if (data instanceof List<?> l) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : l) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        map.put(String.valueOf(e.getKey()), e.getValue());
                    }
                    result.add(map);
                }
            }
            return result;
        }
        return null;
    }
}
