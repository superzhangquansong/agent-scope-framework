package com.agent.scope.framework.hdl;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * HDL 房屋信息
 *
 * @author zqs
 * @since 1.0.0
 */
@Data
public class HdlHome implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 房屋 ID（字符串化，避免大数精度丢失） */
    private String homeId;

    /** 房屋名称 */
    private String homeName;

    /** 房屋类型：BUSPRO/KNX/ZIGBEE */
    private String homeType;

    /** 设备数量 */
    private int deviceCount;

    /** 是否支持远程控制 */
    private boolean remoteControl;
}
