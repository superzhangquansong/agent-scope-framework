package com.agent.scope.framework.hdl;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * HDL 设备信息
 *
 * @author zqs
 * @since 1.0.0
 */
@Data
public class HdlDevice implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 设备 ID（字符串化，避免大数精度丢失） */
    private String deviceId;

    /** 网关 ID（字符串化） */
    private String gatewayId;

    /** 设备名称 */
    private String name;

    /** 设备 SID（设备在网关中的标识，场景创建时 functions.sid 使用此字段） */
    private String sid;

    /** 功能类型 SPK（如 light.rgb、hvac.ac、curtain.roller） */
    private String spk;

    /** 是否在线 */
    private boolean online;

    /** 设备属性键值对 */
    private Map<String, Object> attributes;
}
