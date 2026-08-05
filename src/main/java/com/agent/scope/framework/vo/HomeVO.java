package com.agent.scope.framework.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 房屋信息视图对象。
 * <p>
 * 对应云端房屋列表接口的返回数据结构。
 * 包含房屋 ID、名称、类型、设备数量等核心字段。
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Data
public class HomeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 房屋 ID（大数转字符串） */
    private String homeId;

    /** 房屋名称 */
    private String homeName;

    /** 房屋类型（如 ALL、VILLA、APARTMENT） */
    private String homeType;

    /** 设备数量 */
    private Integer deviceCount;
}
