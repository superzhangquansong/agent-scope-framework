package com.agent.scope.framework.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 场景信息视图对象。
 * <p>
 * 对应云端场景列表与场景详情接口的返回数据结构。
 * 包含场景 ID、名称、执行动作列表等核心字段。
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Data
public class SceneVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 场景 ID（设备总线中的标识） */
    private String sid;

    /** 场景名称 */
    private String name;

    /** 场景动作列表（JSON 格式，包含设备控制指令集合） */
    private String actions;
}
