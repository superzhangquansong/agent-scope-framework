package com.agent.scope.framework.hdl.port;

import java.util.List;
import java.util.Map;

/**
 * 属性互斥规则端口接口。
 *
 * <p>提供设备控制互斥规则（如 RGB 与 colorful 互斥）、炫彩关键词等配置，
 * 支持 Nacos 热重载。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
public interface AttributeMutexRules {

    /**
     * 获取触发互斥的关键词（如 "炫彩"）。
     *
     * @return 炫彩关键词
     */
    String getColorfulKeyword();

    /**
     * 获取属性互斥规则映射。
     *
     * <p>如 {"colorful": ["rgb", "on_off"]} 表示当 colorful 属性存在时，
     * 应移除 rgb 和 on_off 属性。</p>
     *
     * @return 互斥规则映射
     */
    Map<String, List<String>> getAttrMutexRules();
}
