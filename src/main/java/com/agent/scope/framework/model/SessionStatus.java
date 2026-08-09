package com.agent.scope.framework.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 会话状态 VO（AuthController 状态查询返回）。
 *
 * @author zqs
 * @since 2.0.0
 */
@Data
@Accessors(chain = true)
public class SessionStatus implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 是否已登录 */
    private boolean loggedIn;

    /** 登录名 */
    private String loginName;

    /** 当前房屋 ID */
    private String currentHomeId;

    /** 当前房屋名称 */
    private String currentHomeName;

    /** 房屋列表 */
    private List<Map<String, Object>> homes;

    /** 是否需要选择房屋 */
    private boolean needSelectHome;

    /** 房屋列表查询是否失败（true 表示 HDL home list API 调用失败或返回空） */
    private boolean homeQueryFailed;
}
