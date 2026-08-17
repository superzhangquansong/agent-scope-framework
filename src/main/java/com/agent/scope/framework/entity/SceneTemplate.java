package com.agent.scope.framework.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 场景模板实体。
 *
 * <p>预置通用场景模板（观影模式、睡眠模式等），用于场景推荐引擎根据用户已有设备
 * 组合进行匹配推荐。用户确认推荐后，模板中的 device_actions 会被转换为
 * HDL 场景创建请求。</p>
 *
 * <p>字段说明：</p>
 * <ul>
 *   <li>{@code requiredSpks}：必备设备种类码 JSON 数组，用户设备必须全覆盖才能匹配此模板</li>
 *   <li>{@code optionalSpks}：可选设备种类码 JSON 数组，部分匹配也纳入推荐（仅推荐用户已有的设备动作）</li>
 *   <li>{@code deviceActions}：设备动作 JSON，key 为 spk，value 为 [{key, value}] 属性列表</li>
 *   <li>{@code timeSlots}：适用时段（morning/daytime/evening/night），用于时段加权排序</li>
 * </ul>
 *
 * @author zqs
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("scene_template")
public class SceneTemplate implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID（自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 模板编码（唯一标识，如 movie_mode）
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
     * 必备设备种类码 JSON 数组（如 ["light.rgbcw","hvac.ac"]）
     */
    private String requiredSpks;

    /**
     * 可选设备种类码 JSON 数组（部分匹配也纳入推荐）
     */
    private String optionalSpks;

    /**
     * 设备动作 JSON（key 为 spk，value 为 [{key, value}] 属性列表）
     */
    private String deviceActions;

    /**
     * 适用时段（如 evening/night/morning/daytime），用于时段加权匹配
     */
    private String timeSlots;

    /**
     * 优先级基础分（越小越高，范围 1-100）
     */
    private Integer priority;

    /**
     * 是否启用：0 禁用，1 启用
     */
    private Integer enabled;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标识：0 未删除，1 已删除
     */
    @TableLogic
    private Integer deleted;
}
