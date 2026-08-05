package com.agent.scope.framework.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 房屋列表查询请求 DTO。
 * <p>
 * 前端通过 axios 以 JSON 请求体方式提交查询条件，
 * 包括房屋类型与是否自动生成等参数。
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
@Data
public class HomeListRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 房屋类型（ALL / HOME / ROOM 等） */
    private String homeType;

    /** 是否自动生成（可选） */
    private Boolean autoGenerate;
}
