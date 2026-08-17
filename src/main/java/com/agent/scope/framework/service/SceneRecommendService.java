package com.agent.scope.framework.service;

import com.agent.scope.framework.entity.SceneTemplate;
import com.agent.scope.framework.vo.SceneRecommendVO;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 场景推荐引擎核心服务。
 *
 * <p>根据用户已有设备组合匹配场景模板，按匹配度、时段、优先级加权排序，
 * 返回 Top N 推荐方案给用户。用户确认后才创建场景（推荐阶段不执行任何设备控制）。</p>
 *
 * <p>推荐流水线：</p>
 * <ol>
 *   <li><b>模板匹配</b>：用户设备种类码集合必须覆盖模板的 required_spks（全覆盖），
 *       optional_spks 部分匹配也纳入</li>
 *   <li><b>加权排序</b>：基础分 = 100 - priority；时段匹配加分；可选设备覆盖加分</li>
 *   <li><b>结果构建</b>：将模板的 device_actions 与用户实际设备关联，
 *       构建 SceneRecommendVO 展示给用户</li>
 * </ol>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SceneRecommendService {

    /**
     * 场景模板服务
     */
    private final SceneTemplateService sceneTemplateService;

    /**
     * 推荐结果数量上限
     */
    private static final int MAX_RECOMMEND_COUNT = 5;

    /**
     * 时段匹配加分
     */
    private static final double TIME_SLOT_BONUS = 20.0;

    /**
     * 每个可选设备覆盖加分
     */
    private static final double OPTIONAL_DEVICE_BONUS = 5.0;

    /**
     * 根据用户已有设备推荐场景方案。
     *
     * <p>推荐流程：模板匹配 → 加权排序 → 结果构建。推荐结果仅展示给用户，
     * 不执行任何设备控制，用户确认后 LLM 调用 create_scene_from_template 创建场景。</p>
     *
     * @param userDevices 用户设备列表（从 Redis 缓存或 query_device_list 获取）
     * @return 推荐方案列表（按分数降序，最多 {@value #MAX_RECOMMEND_COUNT} 个）
     */
    public List<SceneRecommendVO> recommend(List<DeviceContextService.DeviceBrief> userDevices) {
        if (userDevices == null || userDevices.isEmpty()) {
            log.info("[SceneRecommend] 用户设备列表为空，无推荐");
            return List.of();
        }

        // 提取用户设备的 spk 集合，并建立 spk → 设备列表的映射（同 spk 可能有多台设备）
        Map<String, List<DeviceContextService.DeviceBrief>> spkToDeviceMap = new HashMap<>();
        for (DeviceContextService.DeviceBrief device : userDevices) {
            spkToDeviceMap.computeIfAbsent(device.getSpk(), k -> new ArrayList<>()).add(device);
        }
        log.info("[SceneRecommend] 用户设备 spk 分布: {}", spkToDeviceMap.keySet());

        // 当前时段
        String currentTimeSlot = resolveTimeSlot(LocalTime.now());

        List<SceneTemplate> templates = sceneTemplateService.getAllEnabledTemplates();
        List<ScoredTemplate> scoredList = new ArrayList<>();

        // Step 1: 模板匹配 + Step 2: 加权打分
        for (SceneTemplate template : templates) {
            List<String> requiredSpks = sceneTemplateService.parseSpkArray(template.getRequiredSpks());
            List<String> optionalSpks = sceneTemplateService.parseSpkArray(template.getOptionalSpks());

            // 必备设备全覆盖才能匹配
            if (!spkToDeviceMap.keySet().containsAll(requiredSpks)) {
                continue;
            }

            // 计算分数：基础分 = 100 - priority（priority 越小分数越高）
            double score = 100.0 - (template.getPriority() != null ? template.getPriority() : 50);

            // 时段匹配加分
            if (currentTimeSlot.equals(template.getTimeSlots())) {
                score += TIME_SLOT_BONUS;
            }

            // 可选设备覆盖加分（每覆盖一个 optional spk 加分）
            for (String optionalSpk : optionalSpks) {
                if (spkToDeviceMap.containsKey(optionalSpk)) {
                    score += OPTIONAL_DEVICE_BONUS;
                }
            }

            scoredList.add(new ScoredTemplate(template, score));
            log.info("[SceneRecommend] 模板匹配成功: {} (score={})", template.getTemplateCode(), score);
        }

        // 按分数降序排序，取 Top N
        scoredList.sort((a, b) -> Double.compare(b.score, a.score));
        List<ScoredTemplate> topN = scoredList.stream()
                .limit(MAX_RECOMMEND_COUNT)
                .toList();

        // Step 3: 构建推荐 VO
        List<SceneRecommendVO> result = new ArrayList<>(topN.size());
        for (ScoredTemplate scored : topN) {
            SceneRecommendVO vo = buildRecommendVO(scored.template, scored.score, spkToDeviceMap);
            result.add(vo);
        }

        log.info("[SceneRecommend] 推荐完成: 匹配模板={}, 返回推荐={}", scoredList.size(), result.size());
        return result;
    }

    /**
     * 构建单个推荐方案 VO。
     * <p>
     * 将模板的 device_actions 与用户实际设备关联，提取用户已有的设备动作，
     * 过滤掉用户未拥有的设备（optional_spks 中未匹配的设备不展示）。
     * </p>
     *
     * @param template     场景模板
     * @param score        推荐分数
     * @param spkToDeviceMap spk → 用户设备列表映射
     * @return 推荐方案 VO
     */
    private SceneRecommendVO buildRecommendVO(SceneTemplate template, double score,
                                               Map<String, List<DeviceContextService.DeviceBrief>> spkToDeviceMap) {
        // 解析模板的 device_actions JSON
        JSONObject actionsJson = JSON.parseObject(template.getDeviceActions());

        List<SceneRecommendVO.MatchedDevice> matchedDevices = new ArrayList<>();
        List<SceneRecommendVO.DeviceActionDetail> deviceActions = new ArrayList<>();

        // 遍历模板中每个 spk 的动作，仅保留用户拥有的设备
        for (String spk : actionsJson.keySet()) {
            List<DeviceContextService.DeviceBrief> devices = spkToDeviceMap.get(spk);
            if (devices == null || devices.isEmpty()) {
                // 用户没有此 spk 的设备，跳过（optional 设备可能不匹配）
                continue;
            }

            JSONArray attrsArray = actionsJson.getJSONArray(spk);
            List<SceneRecommendVO.AttributeDetail> attributes = new ArrayList<>(attrsArray.size());

            // 解析属性列表
            for (int i = 0; i < attrsArray.size(); i++) {
                JSONObject attrObj = attrsArray.getJSONObject(i);
                attributes.add(SceneRecommendVO.AttributeDetail.builder()
                        .key(attrObj.getString("key"))
                        .value(attrObj.get("value"))
                        .desc(attrObj.getString("key"))
                        .build());
            }

            // 为用户拥有的每台此 spk 设备构建匹配信息和动作详情
            for (DeviceContextService.DeviceBrief device : devices) {
                matchedDevices.add(SceneRecommendVO.MatchedDevice.builder()
                        .deviceId(device.getDeviceId())
                        .name(device.getName())
                        .spk(device.getSpk())
                        .gatewayId(device.getGatewayId())
                        .build());

                deviceActions.add(SceneRecommendVO.DeviceActionDetail.builder()
                        .deviceName(device.getName())
                        .spk(spk)
                        .attributes(attributes)
                        .actionSummary(buildActionSummary(spk, attrsArray))
                        .build());
            }
        }

        return SceneRecommendVO.builder()
                .templateCode(template.getTemplateCode())
                .sceneName(template.getSceneName())
                .description(template.getDescription())
                .icon(template.getIcon())
                .matchedDevices(matchedDevices)
                .deviceActions(deviceActions)
                .score(score)
                .build();
    }

    /**
     * 根据设备 spk 和属性列表构建动作效果文字摘要。
     * <p>
     * 将属性 key-value 转为用户可读的文字描述（如"开灯，亮度20%，色温3000K"），
     * 用于前端展示和 LLM 回复用户。
     * </p>
     *
     * @param spk       设备种类码
     * @param attrsArray 属性 JSON 数组
     * @return 动作效果文字描述
     */
    private String buildActionSummary(String spk, JSONArray attrsArray) {
        // 属性 key → 中文描述映射（避免硬编码魔法值，集中维护）
        Map<String, String> attrDescMap = Map.ofEntries(
                Map.entry("on_off", "开关"),
                Map.entry("brightness", "亮度"),
                Map.entry("cct", "色温"),
                Map.entry("set_temp", "温度"),
                Map.entry("mode", "模式"),
                Map.entry("fan", "风速"),
                Map.entry("position", "位置"),
                Map.entry("volume", "音量"),
                Map.entry("colorful", "色彩"),
                Map.entry("rgb", "RGB")
        );

        // on_off 的 value → 中文映射
        Map<String, String> onOffDescMap = Map.of("on", "开", "off", "关");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < attrsArray.size(); i++) {
            JSONObject attr = attrsArray.getJSONObject(i);
            String key = attr.getString("key");
            Object value = attr.get("value");

            if (i > 0) {
                sb.append("，");
            }

            String desc = attrDescMap.getOrDefault(key, key);
            // on_off 特殊处理：显示"开"/"关"
            if ("on_off".equals(key) && value instanceof String strVal) {
                sb.append(onOffDescMap.getOrDefault(strVal, strVal));
            } else {
                sb.append(desc).append(value);
                // 附加单位
                if ("brightness".equals(key)) {
                    sb.append("%");
                } else if ("cct".equals(key)) {
                    sb.append("K");
                } else if ("set_temp".equals(key)) {
                    sb.append("度");
                } else if ("position".equals(key)) {
                    sb.append("%");
                }
            }
        }
        return sb.toString();
    }

    /**
     * 根据当前时间解析时段标识。
     * <p>
     * 时段划分：morning(6-11) / daytime(11-17) / evening(17-22) / night(22-6)
     * </p>
     *
     * @param now 当前时间
     * @return 时段标识字符串
     */
    private String resolveTimeSlot(LocalTime now) {
        int hour = now.getHour();
        if (hour >= 6 && hour < 11) {
            return "morning";
        } else if (hour >= 11 && hour < 17) {
            return "daytime";
        } else if (hour >= 17 && hour < 22) {
            return "evening";
        } else {
            return "night";
        }
    }

    /**
     * 带分数的模板内部类（用于排序）。
     */
    private record ScoredTemplate(SceneTemplate template, double score) {}
}
