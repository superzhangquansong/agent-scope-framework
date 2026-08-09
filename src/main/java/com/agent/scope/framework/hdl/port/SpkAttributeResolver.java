package com.agent.scope.framework.hdl.port;

import java.util.Map;

/**
 * SPK 物模型属性解析器端口接口。
 *
 * <p>基于 spk-schemas.json 做确定性关键词匹配（不调 LLM），
 * 把用户自然语言指令转换为 HDL 后端期望的 {key, value} 列表。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
public interface SpkAttributeResolver {

    /**
     * 解析设备属性。
     *
     * @param spk       设备种类码（如 light.rgb、light.dimming）
     * @param userInput 用户对该设备的控制描述（如 "RGB开蓝色亮度98"）
     * @return 解析出的属性 Map（如 {on_off:"on", brightness:98, rgb:"0,0,255"}）
     */
    Map<String, Object> resolveAttributes(String spk, String userInput);
}
