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

    /**
     * 获取设备属性 schema 摘要（用于解析失败时返回给 LLM，引导其自行赋值）。
     *
     * <p>返回该 spk 支持的所有可写属性的 key、描述、类型、取值范围、枚举值等信息，
     * 供 LLM 在处理场景化指令（如"观影模式"）时自行推断属性值。</p>
     *
     * @param spk 设备种类码
     * @return 属性摘要字符串，spk 不存在时返回空字符串
     */
    String getSchemaSummary(String spk);
}
