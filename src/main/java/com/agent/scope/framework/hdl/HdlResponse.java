package com.agent.scope.framework.hdl;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * HDL API 统一响应
 *
 * <p>HDL 网关返回结构：{ isSuccess, code, message, data }。</p>
 *
 * <p>注意：HDL 后端错误信息字段名为 {@code message}（如 "设备不存在"），
 * 通过 {@link JsonAlias} 同时兼容 {@code msg} 字段，确保错误信息正确反序列化。</p>
 *
 * <p><b>data 字段类型说明：</b>HDL 不同接口返回的 data 类型不一致：</p>
 * <ul>
 *   <li>登录/刷新Token：data 是对象（Map），如 { accessToken, refreshToken, expiresIn }</li>
 *   <li>房屋列表：data 是数组（List），如 [{ homeId, homeName }, ...]</li>
 *   <li>设备列表：data 是数组（List）</li>
 *   <li>产品列表：data 是对象（Map），如 { list, total }</li>
 *   <li>产品详情：data 是对象（Map）</li>
 * </ul>
 *
 * <p>因此 data 字段定义为 {@link Object}，由调用方通过 {@link #getDataAsMap()} 或
 * {@link #getDataAsList()} 按 need 转型，避免反序列化失败。</p>
 *
 * @author zqs
 * @since 1.0.0
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
     * 提示信息（HDL 后端返回的错误描述，如"设备不存在"）。
     *
     * <p>HDL 后端实际返回字段名为 {@code message}，通过 {@link JsonAlias}
     * 同时兼容 {@code msg} 字段名，确保错误信息能正确反序列化。</p>
     */
    @JsonAlias({"message", "msg"})
    private String msg;

    /** 业务数据（可能是 Map 或 List，按接口而定） */
    private Object data;

    /**
     * 综合判断是否成功（兼容 isSuccess / code 两种风格）
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
     * 将 data 当作 Map 读取（适用于登录、产品列表、产品详情等接口）
     *
     * @return Map 形式的 data；若 data 不是 Map 则返回 null
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getDataAsMap() {
        if (data == null) {
            return null;
        }
        if (data instanceof Map<?, ?> m) {
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                result.put(String.valueOf(e.getKey()), e.getValue());
            }
            return result;
        }
        return null;
    }

    /**
     * 将 data 当作 List 读取（适用于房屋列表、设备列表等接口）
     *
     * @return List 形式的 data；若 data 不是 List 则返回 null
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getDataAsList() {
        if (data == null) {
            return null;
        }
        if (data instanceof List<?> l) {
            List<Map<String, Object>> result = new java.util.ArrayList<>();
            for (Object item : l) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> map = new java.util.LinkedHashMap<>();
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
