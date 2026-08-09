package com.agent.scope.framework.hdl;

import com.agent.scope.framework.hdl.port.AttributeMutexRules;
import lombok.Data;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 属性互斥规则实现。
 *
 * <p>从 Nacos 配置热重载，提供设备控制互斥规则（如 RGB 与 colorful 互斥）。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Data
@Component
@RefreshScope
public class AttributeMutexRulesImpl implements AttributeMutexRules {

    /** 炫彩关键词（默认"炫彩"，可通过 Nacos 热重载） */
    private String colorfulKeyword = "炫彩";

    /** 属性互斥规则（默认 colorful 与 rgb、on_off 互斥） */
    private Map<String, List<String>> attrMutexRules = new LinkedHashMap<>();

    /**
     * 构造方法，初始化默认互斥规则。
     */
    public AttributeMutexRulesImpl() {
        // 默认互斥规则：当 colorful 属性存在时，移除 rgb 和 on_off
        attrMutexRules.put("colorful", Arrays.asList("rgb", "on_off"));
    }

    @Override
    public String getColorfulKeyword() {
        return colorfulKeyword;
    }

    @Override
    public Map<String, List<String>> getAttrMutexRules() {
        return attrMutexRules;
    }
}
