package com.agent.scope.framework.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 场景推荐结果 VO（展示给用户，用户确认后才创建场景）。
 *
 * <p>推荐引擎根据用户已有设备组合匹配场景模板后，构建此 VO 返回给前端/LLM。
 * 用户看到场景名称、效果描述、匹配的设备列表和具体动作后，通过自然语言或
 * 点击界面按钮确认，系统才调用 create_scene 创建场景。</p>
 *
 * <p>关键字段说明：</p>
 * <ul>
 *   <li>{@code templateCode}：模板编码，用户确认后 LLM 据此调用 create_scene</li>
 *   <li>{@code sceneName}：场景名称（如 观影模式），展示给用户</li>
 *   <li>{@code description}：场景效果描述，让用户了解场景会做什么</li>
 *   <li>{@code matchedDevices}：用户设备中匹配到的设备列表（含 deviceId/gatewayId/spk/name）</li>
 *   <li>{@code deviceActions}：每个设备的具体动作（属性 key-value 列表），展示给用户预览</li>
 * </ul>
 *
 * @author zqs
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SceneRecommendVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 模板编码（用户确认后 LLM 据此调用 create_scene_from_template）
     */
    private String templateCode;

    /**
     * 场景名称（展示给用户，如 观影模式）
     */
    private String sceneName;

    /**
     * 场景效果描述（展示给用户看的效果说明）
     */
    private String description;

    /**
     * 前端图标标识
     */
    private String icon;

    /**
     * 匹配的设备列表（用户设备中与模板匹配的设备）
     */
    private List<MatchedDevice> matchedDevices;

    /**
     * 设备动作详情（展示给用户预览每个设备的控制效果）
     */
    private List<DeviceActionDetail> deviceActions;

    /**
     * 推荐分数（用于排序，分数越高推荐优先级越高）
     */
    private Double score;

    /**
     * 匹配的设备信息（用户设备中与模板匹配的设备精简信息）。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchedDevice implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 设备 ID */
        private String deviceId;
        /** 设备名称 */
        private String name;
        /** 设备种类码 */
        private String spk;
        /** 网关 ID */
        private String gatewayId;
    }

    /**
     * 设备动作详情（展示给用户预览）。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceActionDetail implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 设备名称 */
        private String deviceName;
        /** 设备种类码 */
        private String spk;
        /** 属性列表（key-value 对） */
        private List<AttributeDetail> attributes;
        /** 动作效果文字描述（如"开灯，亮度20%，暖色"） */
        private String actionSummary;
    }

    /**
     * 属性详情。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttributeDetail implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 属性 key（如 on_off、brightness） */
        private String key;
        /** 属性值（如 on、20） */
        private Object value;
        /** 属性描述（如"开关""亮度"，从 spk-schemas 读取） */
        private String desc;
    }
}
