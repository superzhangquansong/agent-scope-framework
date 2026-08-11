package com.agent.scope.framework.hdl;

import com.agent.scope.framework.hdl.port.SpkAttributeResolver;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.nacos.api.config.ConfigService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SPK 物模型属性解析器实现。
 *
 * <p>从 classpath:config/resources/spk-schemas.json 加载物模型 schema，
 * 基于确定性关键词匹配（不调 LLM）将用户自然语言指令解析为设备属性 Map。</p>
 *
 * <p>支持属性类型：enum（枚举）、number（数值）、color（颜色）、bool（布尔）、string（字符串）。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Component
public class SpkAttributeResolverImpl implements SpkAttributeResolver {

    /** schema 配置文件在 classpath 中的路径 */
    private static final String SCHEMAS_PATH = "config/resources/spk-schemas.json";

    /** Nacos 中 spk-schemas 的 Data ID */
    private static final String NACOS_DATA_ID = "spk-schemas.json";

    /** Nacos 分组 */
    @Value("${spring.cloud.nacos.config.group:SCOPE_GROUP}")
    private String nacosGroup;

    /** 匹配整数或小数的正则 */
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");

    /** 匹配 "R,G,B" 格式颜色的正则 */
    private static final Pattern RGB_PATTERN = Pattern.compile("(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})");

    /** 所有 schema 定义（key = spk, value = schema 对象） */
    private JSONObject schemas;

    /** fallback schema（未命中任何 spk 时使用） */
    private JSONObject fallbackSchema;

    /** 颜色预设表（中文颜色名 -> "R,G,B"） */
    private JSONObject colorPresets;

    /** Nacos 配置服务（可选，不可用时降级到 classpath） */
    @Autowired(required = false)
    private ConfigService configService;

    /**
     * 加载并解析 spk-schemas.json。
     * <p>优先从 Nacos 配置中心加载（Data ID: spk-schemas.json），
     * Nacos 不可用或无此配置时降级到 classpath 本地文件。</p>
     * 在 Bean 初始化后自动调用。
     */
    @PostConstruct
    public void loadSchemas() {
        String content = loadFromNacos();
        if (content != null) {
            log.info("SPK schema 从 Nacos 加载成功, dataId={}, group={}", NACOS_DATA_ID, nacosGroup);
        } else {
            content = loadFromClasspath();
            log.info("SPK schema 从 classpath 加载: path={}", SCHEMAS_PATH);
        }

        if (content == null || content.isBlank()) {
            log.error("无法加载 spk-schemas.json（Nacos 和 classpath 均不可用）");
            this.schemas = new JSONObject();
            this.fallbackSchema = new JSONObject();
            this.colorPresets = new JSONObject();
            return;
        }

        try {
            JSONObject root = JSON.parseObject(content);
            this.schemas = root.getJSONObject("schemas");
            this.fallbackSchema = root.getJSONObject("_fallback");
            this.colorPresets = root.getJSONObject("_color_presets");
            log.info("SPK schema 解析完成，schema 数量: {}", schemas != null ? schemas.size() : 0);
        } catch (Exception e) {
            log.error("解析 spk-schemas.json 失败: {}", e.getMessage(), e);
            this.schemas = new JSONObject();
            this.fallbackSchema = new JSONObject();
            this.colorPresets = new JSONObject();
        }
    }

    /** 从 Nacos 加载配置 */
    private String loadFromNacos() {
        if (configService == null) {
            return null;
        }
        try {
            return configService.getConfig(NACOS_DATA_ID, nacosGroup, 3000);
        } catch (Exception e) {
            log.warn("从 Nacos 加载 spk-schemas 失败: {}", e.getMessage());
            return null;
        }
    }

    /** 从 classpath 加载配置 */
    private String loadFromClasspath() {
        try (InputStream is = new ClassPathResource(SCHEMAS_PATH).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("从 classpath 加载 spk-schemas.json 失败: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public Map<String, Object> resolveAttributes(String spk, String userInput) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (spk == null || spk.isBlank() || userInput == null || userInput.isBlank()) {
            return result;
        }

        JSONObject schema = findSchema(spk);
        if (schema == null) {
            log.warn("未找到 spk={} 对应的 schema", spk);
            return result;
        }

        JSONArray attributes = schema.getJSONArray("attributes");
        if (attributes == null || attributes.isEmpty()) {
            return result;
        }

        for (int i = 0; i < attributes.size(); i++) {
            JSONObject attr = attributes.getJSONObject(i);
            String key = attr.getString("key");
            String type = attr.getString("type");
            Object value = resolveAttributeValue(attr, type, userInput);
            if (value != null) {
                result.put(key, value);
            }
        }

        log.debug("属性解析完成 spk={}, userInput={}, result={}", spk, userInput, result);
        return result;
    }

    /**
     * 查找 schema，支持前缀回退匹配。
     * 例如 "light.rgb.jinmao" → "light.rgb" → "light" → "_fallback"
     *
     * @param spk 设备种类码
     * @return 匹配到的 schema 对象，若全部未命中则返回 fallback
     */
    private JSONObject findSchema(String spk) {
        if (spk == null || spk.isBlank()) {
            return fallbackSchema;
        }
        // 精确匹配
        if (schemas != null && schemas.containsKey(spk)) {
            return schemas.getJSONObject(spk);
        }
        // 前缀回退：逐段去掉最后一级再匹配
        String current = spk;
        while (current.contains(".")) {
            int lastDot = current.lastIndexOf('.');
            current = current.substring(0, lastDot);
            if (schemas != null && schemas.containsKey(current)) {
                return schemas.getJSONObject(current);
            }
        }
        // 最终回退到 _fallback
        return fallbackSchema;
    }

    /**
     * 根据属性类型分发解析。
     *
     * @param attr      属性定义 JSON 对象
     * @param type      属性类型（enum/number/color/bool/string）
     * @param userInput 用户输入
     * @return 解析出的值，未匹配返回 null
     */
    private Object resolveAttributeValue(JSONObject attr, String type, String userInput) {
        if (type == null) {
            return null;
        }
        switch (type) {
            case "enum":
                return resolveEnumAttribute(attr, userInput);
            case "number":
                return resolveNumberAttribute(attr, userInput);
            case "color":
                return resolveColorAttribute(attr, userInput, colorPresets);
            case "bool":
                return resolveBoolAttribute(attr, userInput);
            case "string":
                return resolveStringAttribute(attr, userInput);
            default:
                log.debug("未知的属性类型: {}", type);
                return null;
        }
    }

    /**
     * 解析枚举类型属性。
     * 遍历别名，若用户输入包含某别名，则匹配对应的枚举值。
     * 匹配策略：先精确匹配别名与枚举 desc，再模糊匹配（别名包含某枚举 desc）。
     *
     * <p>兜底策略：当标准匹配失败但 userInput 是"开"或"关"时，
     * 尝试在枚举值中查找对应的"启用/禁用"或"开/关"映射。
     * 这解决了传感器等设备的 enable 属性（aliases=["使能","启用","禁用"]）
     * 无法匹配"开/关"关键词的问题。</p>
     *
     * @param attr      属性定义
     * @param userInput 用户输入
     * @return 枚举值字符串，未匹配返回 null
     */
    private Object resolveEnumAttribute(JSONObject attr, String userInput) {
        JSONArray aliases = attr.getJSONArray("aliases");
        JSONArray enumerations = attr.getJSONArray("enumerations");
        if (aliases == null || enumerations == null) {
            return null;
        }

        // 构建 desc -> value 映射
        Map<String, String> descToValue = new LinkedHashMap<>();
        for (int i = 0; i < enumerations.size(); i++) {
            JSONObject enumObj = enumerations.getJSONObject(i);
            String value = enumObj.getString("value");
            String desc = enumObj.getString("desc");
            if (desc != null && value != null) {
                descToValue.put(desc, value);
            }
        }

        // 按长度降序排列别名，优先匹配更具体（更长）的关键词
        List<String> sortedAliases = new ArrayList<>();
        for (int i = 0; i < aliases.size(); i++) {
            sortedAliases.add(aliases.getString(i));
        }
        sortedAliases.sort((a, b) -> b.length() - a.length());

        for (String alias : sortedAliases) {
            if (!userInput.contains(alias)) {
                continue;
            }
            // 精确匹配 desc
            if (descToValue.containsKey(alias)) {
                return descToValue.get(alias);
            }
            // 模糊匹配：别名包含某枚举 desc（如 "打开" 包含 "开"）
            for (Map.Entry<String, String> entry : descToValue.entrySet()) {
                if (alias.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }

        // 兜底：userInput 是"开"或"关"，在枚举值和 desc 中查找映射
        if ("开".equals(userInput.trim())) {
            for (String desc : descToValue.keySet()) {
                if ("开".equals(desc) || "启用".equals(desc) || "on".equalsIgnoreCase(desc)
                        || "true".equalsIgnoreCase(desc)) {
                    return descToValue.get(desc);
                }
            }
            // 都找不到时返回第一个枚举值（大多数设备的第一个枚举是"开/启用"）
            if (!descToValue.isEmpty()) {
                return descToValue.values().iterator().next();
            }
        }
        if ("关".equals(userInput.trim())) {
            for (String desc : descToValue.keySet()) {
                if ("关".equals(desc) || "禁用".equals(desc) || "off".equalsIgnoreCase(desc)
                        || "false".equalsIgnoreCase(desc)) {
                    return descToValue.get(desc);
                }
            }
            // 都找不到时返回最后一个枚举值（大多数设备的最后一个枚举是"关/禁用"）
            if (!descToValue.isEmpty()) {
                String lastValue = null;
                for (String v : descToValue.values()) {
                    lastValue = v;
                }
                return lastValue;
            }
        }

        return null;
    }

    /**
     * 解析数值类型属性。
     * 在别名关键词附近提取数字，并做 min/max 范围约束。
     *
     * @param attr      属性定义
     * @param userInput 用户输入
     * @return 数值（Integer 或 Double），未匹配返回 null
     */
    private Object resolveNumberAttribute(JSONObject attr, String userInput) {
        JSONArray aliases = attr.getJSONArray("aliases");
        if (aliases == null) {
            return null;
        }
        List<String> aliasList = new ArrayList<>();
        for (int i = 0; i < aliases.size(); i++) {
            aliasList.add(aliases.getString(i));
        }

        Number num = extractNumberNearKeyword(userInput, aliasList);
        if (num == null) {
            return null;
        }

        double value = num.doubleValue();
        // 范围约束
        Double min = attr.getDouble("min");
        Double max = attr.getDouble("max");
        if (min != null && value < min) {
            value = min;
        }
        if (max != null && value > max) {
            value = max;
        }

        // 整数返回 Integer，小数返回 Double
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return (int) value;
        }
        return value;
    }

    /**
     * 解析颜色类型属性。
     * 优先匹配 _color_presets 中的中文颜色名，其次尝试解析 "R,G,B" 格式。
     *
     * @param attr         属性定义
     * @param userInput    用户输入
     * @param colorPresets 颜色预设表
     * @return 颜色字符串（如 "255,0,0"），未匹配返回 null
     */
    private Object resolveColorAttribute(JSONObject attr, String userInput, JSONObject colorPresets) {
        if (colorPresets != null && !colorPresets.isEmpty()) {
            // 按长度降序排列颜色名，优先匹配更长的（如 "草绿色" 优先于 "绿色"）
            List<String> colorNames = new ArrayList<>(colorPresets.keySet());
            colorNames.sort((a, b) -> b.length() - a.length());
            for (String colorName : colorNames) {
                // 跳过以 _ 开头的元数据字段（如 _comment）
                if (colorName.startsWith("_")) {
                    continue;
                }
                if (userInput.contains(colorName)) {
                    return colorPresets.getString(colorName);
                }
            }
        }

        // 尝试解析直接 RGB 格式 "R,G,B"
        Matcher m = RGB_PATTERN.matcher(userInput);
        if (m.find()) {
            return m.group(1) + "," + m.group(2) + "," + m.group(3);
        }
        return null;
    }

    /**
     * 解析布尔类型属性。
     * 根据别名关键词推断 true/false（类似 enum）。
     *
     * @param attr      属性定义
     * @param userInput 用户输入
     * @return Boolean 值，未匹配返回 null
     */
    private Object resolveBoolAttribute(JSONObject attr, String userInput) {
        JSONArray aliases = attr.getJSONArray("aliases");
        if (aliases == null) {
            return null;
        }
        // 按长度降序排列
        List<String> sortedAliases = new ArrayList<>();
        for (int i = 0; i < aliases.size(); i++) {
            sortedAliases.add(aliases.getString(i));
        }
        sortedAliases.sort((a, b) -> b.length() - a.length());

        for (String alias : sortedAliases) {
            if (!userInput.contains(alias)) {
                continue;
            }
            if (alias.contains("开") || alias.contains("启用") || alias.contains("true") || alias.contains("是")) {
                return true;
            }
            if (alias.contains("关") || alias.contains("禁用") || alias.contains("false") || alias.contains("否")) {
                return false;
            }
        }
        return null;
    }

    /**
     * 解析字符串类型属性。
     * 提取别名关键词附近的文本作为值。
     *
     * @param attr      属性定义
     * @param userInput 用户输入
     * @return 字符串值，未匹配返回 null
     */
    private Object resolveStringAttribute(JSONObject attr, String userInput) {
        JSONArray aliases = attr.getJSONArray("aliases");
        if (aliases == null) {
            return null;
        }
        for (int i = 0; i < aliases.size(); i++) {
            String alias = aliases.getString(i);
            int idx = userInput.indexOf(alias);
            if (idx >= 0) {
                // 提取别名后的文本作为值
                int start = idx + alias.length();
                if (start < userInput.length()) {
                    String after = userInput.substring(start).trim();
                    if (!after.isEmpty()) {
                        return after;
                    }
                }
                return alias;
            }
        }
        return null;
    }

    /**
     * 从用户输入中提取别名关键词附近的数字。
     * 先在别名之后查找，再在别名之前查找，查找窗口为 10 个字符。
     *
     * @param userInput 用户输入
     * @param aliases   别名列表
     * @return 提取到的数字（Integer 或 Double），未找到返回 null
     */
    private Number extractNumberNearKeyword(String userInput, List<String> aliases) {
        // 按长度降序排列，优先匹配更具体的关键词
        List<String> sorted = new ArrayList<>(aliases);
        sorted.sort((a, b) -> b.length() - a.length());

        for (String alias : sorted) {
            int idx = userInput.indexOf(alias);
            if (idx < 0) {
                continue;
            }
            // 在别名之后查找数字（如 "亮度98"）
            int afterStart = idx + alias.length();
            int windowEnd = Math.min(userInput.length(), afterStart + 10);
            String after = userInput.substring(afterStart, windowEnd);
            Matcher m = NUMBER_PATTERN.matcher(after);
            if (m.find()) {
                return parseNumber(m.group(1));
            }
            // 在别名之前查找数字（如 "98亮度"）
            int beforeStart = Math.max(0, idx - 10);
            String before = userInput.substring(beforeStart, idx);
            m = NUMBER_PATTERN.matcher(before);
            if (m.find()) {
                return parseNumber(m.group(1));
            }
        }
        return null;
    }

    /**
     * 将数字字符串解析为 Integer 或 Double。
     *
     * @param numStr 数字字符串
     * @return Number 实例
     */
    private Number parseNumber(String numStr) {
        if (numStr.contains(".")) {
            return Double.parseDouble(numStr);
        }
        return Integer.parseInt(numStr);
    }
}
