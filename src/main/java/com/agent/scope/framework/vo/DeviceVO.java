package com.agent.scope.framework.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 设备信息视图对象。
 * <p>
 * 对应云端设备列表与设备详情接口的返回数据结构。
 * 包含设备 ID、名称、功能类型、在线状态等核心字段。
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Data
public class DeviceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 设备 ID（大数转字符串，避免 JSON 精度丢失） */
    private String deviceId;

    /** 设备名称 */
    private String name;

    /** 功能类型（空间前缀，标识设备品类） */
    private String spk;

    /** 在线状态（true=在线，false=离线） */
    private Boolean online;

    /** 设备在总线中的标识（用于场景控制） */
    private String sid;

    /** 网关 ID */
    private String gatewayId;
}
