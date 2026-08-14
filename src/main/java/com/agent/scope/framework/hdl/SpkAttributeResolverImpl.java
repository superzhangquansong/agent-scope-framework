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

    /** 匹配整数或小数的正则（支持英文点"."和中文点"点"，如 24.3 或 24点3） */
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+(?:[.点]\\d+)?)");

    /** 匹配中文数字的正则（如"二十四""二十四点三""二十四点3""一百二十"） */
    private static final Pattern CHINESE_NUMBER_PATTERN = Pattern.compile(
            "[零〇一二三四五六七八九十百千万两壹贰叁肆伍陆柒捌玖拾佰仟]+(?:点[零〇一二三四五六七八九0-9]+)?");

    /** 中文数字字符 → 阿拉伯数字映射表（支持大写和小写形式） */
    private static final Map<Character, Integer> CHINESE_DIGIT_MAP = Map.ofEntries(
            Map.entry('零', 0), Map.entry('〇', 0),
            Map.entry('一', 1), Map.entry('壹', 1),
            Map.entry('二', 2), Map.entry('贰', 2), Map.entry('两', 2),
            Map.entry('三', 3), Map.entry('叁', 3),
            Map.entry('四', 4), Map.entry('肆', 4),
            Map.entry('五', 5), Map.entry('伍', 5),
            Map.entry('六', 6), Map.entry('陆', 6),
            Map.entry('七', 7), Map.entry('柒', 7),
            Map.entry('八', 8), Map.entry('捌', 8),
            Map.entry('九', 9), Map.entry('玖', 9)
    );

    /** 匹配 "R,G,B" 格式颜色的正则 */
    private static final Pattern RGB_PATTERN = Pattern.compile("(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})");

    /** 所有 schema 定义（key = spk, value = schema 对象） */
    private JSONObject schemas;

    /** fallback schema（未命中任何 spk 时使用） */
    private JSONObject fallbackSchema;

    /** 颜色预设表（中文颜色名 -> "R,G,B"） */
    private JSONObject colorPresets;

    /** 模糊感受默认值配置（number/enum 两类，标准解析失败后兜底） */
    private JSONObject fuzzyDefaults;

    /** 相对调整步长配置（key=属性key，value=步长值，如 brightness→10, cct→100） */
    private JSONObject adjustSteps;

    /** 相对调整方向关键词配置（up=增加方向关键词数组，down=减少方向关键词数组） */
    private JSONObject adjustKeywords;

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
            this.fuzzyDefaults = new JSONObject();
            this.adjustSteps = new JSONObject();
            this.adjustKeywords = new JSONObject();
            return;
        }

        try {
            JSONObject root = JSON.parseObject(content);
            this.schemas = root.getJSONObject("schemas");
            this.fallbackSchema = root.getJSONObject("_fallback");
            this.colorPresets = root.getJSONObject("_color_presets");
            this.fuzzyDefaults = root.getJSONObject("_fuzzy_defaults");
            this.adjustSteps = root.getJSONObject("_adjust_steps");
            this.adjustKeywords = root.getJSONObject("_adjust_keywords");
            log.info("SPK schema 解析完成，schema 数量: {}", schemas != null ? schemas.size() : 0);
        } catch (Exception e) {
            log.error("解析 spk-schemas.json 失败: {}", e.getMessage(), e);
            this.schemas = new JSONObject();
            this.fallbackSchema = new JSONObject();
            this.colorPresets = new JSONObject();
            this.fuzzyDefaults = new JSONObject();
            this.adjustSteps = new JSONObject();
            this.adjustKeywords = new JSONObject();
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

    /**
     * 获取设备属性 schema 摘要，用于解析失败时返回给 LLM。
     * <p>
     * 遍历该 spk 支持的可写（access 含 W）属性，拼接 key、描述、类型、取值范围、
     * 枚举值等信息，供 LLM 在处理场景化指令（如"观影模式"）时自行推断属性值。
     * </p>
     */
    @Override
    public String getSchemaSummary(String spk) {
        if (spk == null || spk.isBlank()) {
            return "";
        }
        JSONObject schema = findSchema(spk);
        if (schema == null) {
            return "";
        }
        JSONArray attributes = schema.getJSONArray("attributes");
        if (attributes == null || attributes.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("spk=").append(spk);
        String schemaName = schema.getString("name");
        if (schemaName != null) {
            sb.append("(").append(schemaName).append(")");
        }
        sb.append(" 支持的属性:\n");

        for (int i = 0; i < attributes.size(); i++) {
            JSONObject attr = attributes.getJSONObject(i);
            String access = attr.getString("access");
            // 只返回可写属性（access 含 W），只读属性 LLM 无法控制
            if (access == null || !access.contains("W")) {
                continue;
            }
            String key = attr.getString("key");
            String desc = attr.getString("desc");
            String type = attr.getString("type");
            sb.append("  - key=").append(key)
                    .append(", desc=").append(desc)
                    .append(", type=").append(type);

            // 数值类型：附加 min/max/step/unit
            if ("number".equals(type)) {
                Integer min = attr.getInteger("min");
                Integer max = attr.getInteger("max");
                Integer step = attr.getInteger("step");
                String unit = attr.getString("unit");
                if (min != null) sb.append(", min=").append(min);
                if (max != null) sb.append(", max=").append(max);
                if (step != null) sb.append(", step=").append(step);
                if (unit != null) sb.append(", unit=").append(unit);
            }
            // 枚举类型：附加可选值列表
            if ("enum".equals(type)) {
                JSONArray enums = attr.getJSONArray("enumerations");
                if (enums != null && !enums.isEmpty()) {
                    sb.append(", values=[");
                    for (int j = 0; j < enums.size(); j++) {
                        JSONObject en = enums.getJSONObject(j);
                        if (j > 0) sb.append(", ");
                        sb.append(en.getString("value"))
                                .append("(").append(en.getString("desc")).append(")");
                    }
                    sb.append("]");
                }
            }
            sb.append("\n");
        }
        return sb.toString().trim();
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
        Object value = null;
        switch (type) {
            case "enum":
                value = resolveEnumAttribute(attr, userInput);
                break;
            case "number":
                value = resolveNumberAttribute(attr, userInput);
                break;
            case "color":
                value = resolveColorAttribute(attr, userInput, colorPresets);
                break;
            case "bool":
                value = resolveBoolAttribute(attr, userInput);
                break;
            case "string":
                value = resolveStringAttribute(attr, userInput);
                break;
            default:
                log.debug("未知的属性类型: {}", type);
        }
        // 标准解析失败，尝试模糊默认值兜底
        if (value == null && fuzzyDefaults != null) {
            value = resolveFuzzyDefault(attr, type, userInput);
            if (value != null) {
                log.debug("模糊兜底命中: spk attr key={}, type={}, userInput={}, value={}",
                        attr.getString("key"), type, userInput, value);
            }
        }
        return value;
    }

    /**
     * 模糊感受默认值兜底解析。
     * <p>
     * 当标准关键词匹配失败时，检查 _fuzzy_defaults 中是否有匹配的模糊关键词：
     * <ul>
     *   <li>number 类型：配置值为比例(0-1)，根据属性 min/max 计算实际值</li>
     *   <li>enum 类型：配置值为枚举值字符串，需在属性 enumerations 中存在才生效</li>
     * </ul>
     * 例如："太冷了" → set_temp(16-32) 比例0.7 → 27℃；mode 枚举 → "heat"(制热)
     * </p>
     *
     * @param attr      属性定义
     * @param type      属性类型
     * @param userInput 用户输入
     * @return 兜底默认值，未匹配返回 null
     */
    private Object resolveFuzzyDefault(JSONObject attr, String type, String userInput) {
        JSONObject typeFuzzy = fuzzyDefaults.getJSONObject(type);
        if (typeFuzzy == null) {
            return null;
        }
        // 按关键词长度降序匹配，优先更具体的关键词（如"太冷"优先于"冷"）
        List<String> keywords = new ArrayList<>(typeFuzzy.keySet());
        keywords.removeIf(k -> k.startsWith("_"));
        keywords.sort((a, b) -> b.length() - a.length());

        for (String keyword : keywords) {
            if (!userInput.contains(keyword)) {
                continue;
            }
            Object fuzzyValue = typeFuzzy.get(keyword);
            if ("number".equals(type) && fuzzyValue instanceof Number) {
                // number 类型：比例值 → 根据 min/max 计算实际值
                double ratio = ((Number) fuzzyValue).doubleValue();
                Double min = attr.getDouble("min");
                Double max = attr.getDouble("max");
                if (min != null && max != null && max > min) {
                    double v = min + (max - min) * ratio;
                    return (v == Math.floor(v) && !Double.isInfinite(v)) ? (int) v : v;
                }
            } else if ("enum".equals(type) && fuzzyValue != null) {
                // enum 类型：验证值在属性 enumerations 中存在
                String enumValue = String.valueOf(fuzzyValue);
                JSONArray enumerations = attr.getJSONArray("enumerations");
                if (enumerations != null) {
                    for (int i = 0; i < enumerations.size(); i++) {
                        if (enumValue.equals(enumerations.getJSONObject(i).getString("value"))) {
                            return enumValue;
                        }
                    }
                }
            }
        }
        return null;
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
            // 未提取到数字，检测相对调整指令（如"调高一点""调亮一点""调冷一点"）
            return resolveStepAdjust(attr, userInput, aliasList);
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
     * 检测相对调整指令（如"调高一点""调亮一点""调冷一点"）。
     * <p>
     * 当属性 alias 匹配但未提取到具体数值时，检查 userInput 是否包含
     * 相对调整关键词（关键词来源于 spk-schemas.json 的 _adjust_keywords 配置，
     * 支持 Nacos 热更新）。若匹配则返回 "STEP_UP:{step}" 或 "STEP_DOWN:{step}"，
     * DeviceTool 据此查询设备当前值并计算新值。
     * </p>
     *
     * @param attr      属性定义 JSON 对象
     * @param userInput 用户输入
     * @param aliasList 属性别名列表
     * @return "STEP_UP:{step}" / "STEP_DOWN:{step}" 或 null
     */
    private Object resolveStepAdjust(JSONObject attr, String userInput, List<String> aliasList) {
        // 确认属性被提及：至少一个 alias 在 userInput 中
        boolean aliasMatched = false;
        for (String alias : aliasList) {
            if (userInput.contains(alias)) {
                aliasMatched = true;
                break;
            }
        }
        if (!aliasMatched) {
            return null;
        }

        // 从配置加载方向关键词，检测相对调整方向
        // up=数值增加方向，down=数值减少方向；关键词可热更新，无需改代码
        boolean isUp = containsConfigKeyword(userInput, "up");
        boolean isDown = !isUp && containsConfigKeyword(userInput, "down");
        if (!isUp && !isDown) {
            return null;
        }

        // 获取步长：优先 _adjust_steps 全局配置，fallback 到属性自身 step
        String attrKey = attr.getString("key");
        int step = 1;
        if (adjustSteps != null && adjustSteps.containsKey(attrKey)) {
            step = adjustSteps.getIntValue(attrKey);
        } else {
            Integer attrStep = attr.getInteger("step");
            if (attrStep != null) {
                step = attrStep;
            }
        }

        return (isUp ? "STEP_UP:" : "STEP_DOWN:") + step;
    }

    /**
     * 检查 userInput 是否包含 _adjust_keywords 配置中指定方向的关键词。
     * <p>
     * 从 {@link #adjustKeywords} 的 up/down 数组中读取关键词，遍历检测 userInput 是否包含。
     * 关键词按长度降序匹配，优先匹配更具体（更长）的关键词，避免短词误匹配。
     * </p>
     *
     * @param userInput 用户输入
     * @param direction 方向键名（"up" 或 "down"）
     * @return 命中返回 true，未命中或配置缺失返回 false
     */
    private boolean containsConfigKeyword(String userInput, String direction) {
        if (adjustKeywords == null) {
            return false;
        }
        JSONArray keywords = adjustKeywords.getJSONArray(direction);
        if (keywords == null || keywords.isEmpty()) {
            return false;
        }
        // 转为 List 并按长度降序，优先匹配更具体的关键词
        List<String> sortedKeywords = new ArrayList<>(keywords.size());
        for (int i = 0; i < keywords.size(); i++) {
            sortedKeywords.add(keywords.getString(i));
        }
        sortedKeywords.sort((a, b) -> b.length() - a.length());
        for (String kw : sortedKeywords) {
            if (userInput.contains(kw)) {
                return true;
            }
        }
        return false;
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
            // 在别名之后查找数字（如 "亮度98" 或 "温度二十四"）
            int afterStart = idx + alias.length();
            int windowEnd = Math.min(userInput.length(), afterStart + 10);
            String after = userInput.substring(afterStart, windowEnd);
            // 优先尝试中文数字（如"二十四点三"），避免阿拉伯数字正则误匹配其中的"3"
            Number cnNum = tryExtractChineseNumber(after);
            if (cnNum != null) {
                return cnNum;
            }
            Matcher m = NUMBER_PATTERN.matcher(after);
            if (m.find()) {
                return parseNumber(m.group(1));
            }
            // 在别名之前查找数字（如 "98亮度" 或 "二十四度"）
            int beforeStart = Math.max(0, idx - 10);
            String before = userInput.substring(beforeStart, idx);
            cnNum = tryExtractChineseNumber(before);
            if (cnNum != null) {
                return cnNum;
            }
            m = NUMBER_PATTERN.matcher(before);
            if (m.find()) {
                return parseNumber(m.group(1));
            }
        }
        // 兜底：userInput 是纯数字（如 LLM 截断后的 "28" 或 "二十四"），直接提取
        Number cnNum = tryExtractChineseNumber(userInput);
        if (cnNum != null) {
            return cnNum;
        }
        Matcher m = NUMBER_PATTERN.matcher(userInput);
        if (m.find()) {
            return parseNumber(m.group(1));
        }
        return null;
    }

    /**
     * 将数字字符串解析为 Integer 或 Double。
     * 支持英文小数点"."和中文小数点"点"（如"24.3"或"24点3"）。
     *
     * @param numStr 数字字符串
     * @return Number 实例
     */
    private Number parseNumber(String numStr) {
        // 中文"点"替换为英文小数点
        numStr = numStr.replace("点", ".");
        if (numStr.contains(".")) {
            return Double.parseDouble(numStr);
        }
        return Integer.parseInt(numStr);
    }

    /**
     * 尝试从文本中提取中文数字（如"二十四""二十四点三""二十四点3"）。
     * <p>
     * 支持中文数字（零一二三四五六七八九十百千万，含大写形式）和中文小数点"点"。
     * 小数点后可跟中文数字或阿拉伯数字（如"二十四点三"或"二十四点3"）。
     * </p>
     *
     * @param text 待解析文本
     * @return 解析出的 Number，未匹配到中文数字返回 null
     */
    private Number tryExtractChineseNumber(String text) {
        Matcher m = CHINESE_NUMBER_PATTERN.matcher(text);
        if (!m.find()) {
            return null;
        }
        return parseChineseNumber(m.group());
    }

    /**
     * 将中文数字字符串解析为 Number。
     * <p>
     * 解析算法：遍历中文数字字符，遇到数字字符累加到 current，遇到单位字符（十/百/千/万）
     * 将 current 乘以单位值加到 result。支持小数点"点"，小数部分逐位转换。
     * </p>
     * <p>示例："二十四"→24，"三十"→30，"十二"→12，"十"→10，"二十四点三"→24.3</p>
     *
     * @param str 中文数字字符串
     * @return Number 实例（整数返回 Integer，小数返回 Double）
     */
    private Number parseChineseNumber(String str) {
        // 分割整数部分和小数部分（以"点"为界）
        int dotIdx = str.indexOf('点');
        String intPart = dotIdx >= 0 ? str.substring(0, dotIdx) : str;
        String decPart = dotIdx >= 0 ? str.substring(dotIdx + 1) : "";

        long intVal = parseChineseInteger(intPart);

        if (decPart.isEmpty()) {
            return (int) intVal;
        }

        // 小数部分：逐字符转换（支持中文数字和阿拉伯数字）
        double decVal = 0;
        double factor = 0.1;
        for (char c : decPart.toCharArray()) {
            Integer digit = CHINESE_DIGIT_MAP.get(c);
            if (digit == null) {
                // 阿拉伯数字
                if (c >= '0' && c <= '9') {
                    digit = c - '0';
                } else {
                    break;
                }
            }
            decVal += digit * factor;
            factor *= 0.1;
        }
        return intVal + decVal;
    }

    /**
     * 解析中文整数部分。
     * <p>
     * 算法：result 累加已完成的部分，current 累积当前数字字符。
     * 遇到单位字符时将 current 乘以单位值加到 result，current 清零。
     * 结束后返回 result + current。
     * </p>
     *
     * @param str 中文整数字符串（如"二十四""三十""一百二十三"）
     * @return 解析后的整数值
     */
    private long parseChineseInteger(String str) {
        long result = 0;
        long current = 0;
        for (char c : str.toCharArray()) {
            Integer digit = CHINESE_DIGIT_MAP.get(c);
            if (digit != null) {
                // 数字字符：累积到 current（如"二"→current=2，"四"→current=24）
                current = current * 10 + digit;
            } else if (c == '十' || c == '拾') {
                if (current == 0) current = 1; // "十"单独出现视为 10
                result += current * 10;
                current = 0;
            } else if (c == '百' || c == '佰') {
                if (current == 0) current = 1;
                result += current * 100;
                current = 0;
            } else if (c == '千' || c == '仟') {
                if (current == 0) current = 1;
                result += current * 1000;
                current = 0;
            } else if (c == '万') {
                if (current == 0) current = 1;
                result = (result + current) * 10000;
                current = 0;
            }
        }
        return result + current;
    }
}
