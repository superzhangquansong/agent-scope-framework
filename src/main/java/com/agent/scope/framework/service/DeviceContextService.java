package com.agent.scope.framework.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 设备列表上下文缓存服务。
 * <p>
 * 将 query_device_list 的结果缓存到 Redis，并在系统提示词中注入设备列表摘要，
 * 使 LLM 无需每次调用 query_device_list 即可直接使用设备 ID 调用 batch_control_device。
 * </p>
 * <p>
 * <b>核心优化</b>：消除不必要的 LLM 轮次（省去"先查询设备列表"的 ReAct 循环），
 * 每次设备控制可节省约 2 秒（1.8s LLM 推理 + 142ms HDL API 调用）。
 * </p>
 * <p>
 * 缓存策略：
 * <ul>
 *   <li>Key：{@code device_cache:{houseId}}，按房屋隔离</li>
 *   <li>TTL：300 秒（5 分钟），设备变更后自动过期重新查询</li>
 *   <li>格式：精简 JSON 数组，仅保留 LLM 所需字段（deviceId/name/spk/gatewayId/online）</li>
 * </ul>
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceContextService {

    /** Redis 缓存 Key 前缀 */
    private static final String CACHE_KEY_PREFIX = "device_cache:";

    /** 缓存 TTL */
    private static final Duration CACHE_TTL = Duration.ofSeconds(300);

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 缓存设备列表到 Redis。
     * <p>
     * 在 DeviceTool.queryDeviceList 返回结果后调用，提取精简字段并缓存。
     * 原始 HDL API 响应可能包含大量属性、状态等字段，这里仅保留 LLM 控制设备所需的
     * deviceId、name、spk、gatewayId、online 字段，减少缓存体积和 prompt token 消耗。
     * </p>
     *
     * @param houseId    房屋 ID
     * @param deviceData query_device_list 返回的设备列表数据（List 或 JSONArray）
     */
    public void cacheDeviceList(String houseId, Object deviceData) {
        if (houseId == null || deviceData == null) {
            return;
        }
        try {
            List<DeviceBrief> briefs = extractBriefs(deviceData);
            if (briefs.isEmpty()) {
                return;
            }
            String key = CACHE_KEY_PREFIX + houseId;
            redisTemplate.opsForValue().set(key, JSON.toJSONString(briefs), CACHE_TTL);
            log.info("[DeviceContext] 设备列表已缓存: houseId={}, count={}", houseId, briefs.size());
        } catch (Exception e) {
            log.warn("[DeviceContext] 缓存设备列表失败: houseId={}, error={}", houseId, e.getMessage());
        }
    }

    /**
     * 获取缓存的设备列表摘要（用于注入系统提示词）。
     * <p>
     * 从 Redis 读取缓存的设备列表，格式化为 LLM 可直接使用的 Markdown 表格文本。
     * 若缓存不存在返回 null，LLM 将通过 query_device_list 工具正常查询（降级路径）。
     * </p>
     *
     * @param houseId 房屋 ID
     * @return 设备列表摘要文本，或 null（缓存未命中）
     */
    public String getCachedDeviceContext(String houseId) {
        if (houseId == null) {
            return null;
        }
        try {
            String key = CACHE_KEY_PREFIX + houseId;
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached == null) {
                return null;
            }
            List<DeviceBrief> briefs = JSON.parseArray(cached.toString(), DeviceBrief.class);
            if (briefs.isEmpty()) {
                return null;
            }
            return formatDeviceContext(briefs);
        } catch (Exception e) {
            log.debug("[DeviceContext] 读取设备缓存失败: houseId={}, error={}", houseId, e.getMessage());
            return null;
        }
    }

    /**
     * 从原始设备列表数据中提取精简字段。
     * <p>
     * HDL API 返回的设备对象包含大量字段（attributes、status、roomInfos 等），
     * LLM 仅需 deviceId、name、spk、gatewayId、online 即可调用 batch_control_device。
     * </p>
     *
     * @param deviceData 原始设备数据
     * @return 精简设备信息列表
     */
    @SuppressWarnings("unchecked")
    private List<DeviceBrief> extractBriefs(Object deviceData) {
        List<DeviceBrief> briefs = new ArrayList<>();
        String jsonStr = deviceData instanceof String
                ? (String) deviceData
                : JSON.toJSONString(deviceData);
        JSONArray devices = JSON.parseArray(jsonStr);
        for (int i = 0; i < devices.size(); i++) {
            JSONObject dev = devices.getJSONObject(i);
            briefs.add(DeviceBrief.builder()
                    .deviceId(dev.getString("deviceId"))
                    .name(dev.getString("name"))
                    .spk(dev.getString("spk"))
                    .gatewayId(dev.getString("gatewayId"))
                    .online(dev.getBooleanValue("online"))
                    .build());
        }
        return briefs;
    }

    /**
     * 将设备列表格式化为系统提示词可用的 Markdown 文本。
     * <p>
     * 格式示例：
     * <pre>
     * 【当前房屋设备列表（缓存）】
     * | deviceId | name | spk | gatewayId | online |
     * |---|---|---|---|---|
     * | 2080574332908474370 | Lite RGB | light.rgb | 1971027479312728066 | true |
     * ...
     * </pre>
     * </p>
     *
     * @param briefs 设备精简信息列表
     * @return Markdown 格式的设备列表文本
     */
    private String formatDeviceContext(List<DeviceBrief> briefs) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n【当前房屋设备列表（缓存，可直接使用，无需调用 query_device_list）】\n");
        sb.append("| deviceId | name | spk | gatewayId | online |\n");
        sb.append("|---|---|---|---|---|\n");
        for (DeviceBrief b : briefs) {
            sb.append("| ").append(b.deviceId)
                    .append(" | ").append(b.name)
                    .append(" | ").append(b.spk)
                    .append(" | ").append(b.gatewayId)
                    .append(" | ").append(b.online)
                    .append(" |\n");
        }
        return sb.toString();
    }

    /**
     * 设备精简信息（仅保留 LLM 控制设备所需字段）。
     */
    @lombok.Data
    @lombok.Builder
    public static class DeviceBrief {
        /** 设备 ID */
        private String deviceId;
        /** 设备名称 */
        private String name;
        /** 设备种类码（物模型标识） */
        private String spk;
        /** 网关 ID */
        private String gatewayId;
        /** 是否在线 */
        private boolean online;
    }
}
