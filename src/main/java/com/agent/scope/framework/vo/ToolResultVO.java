package com.agent.scope.framework.vo;

import java.io.Serializable;

/**
 * 工具统一返回格式 —— ToolResultVO
 * <p>
 * 所有业务工具（除 FileTool 外）统一返回此对象。
 * 包含成功状态、提示信息、业务数据、前端路由路径、广播文案、是否需确认等字段。
 * </p>
 *
 * @author agent-scope-start
 * @since 2.0.0
 */
public class ToolResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否成功 */
    private boolean success;

    /** 提示信息（成功或错误描述） */
    private String message;

    /** 业务数据（具体结构因工具而异） */
    private Object data;

    /** 前端路由路径 */
    private String routePath;

    /** 广播文案（用于前端 TTS 播报） */
    private String broadcastText;

    /** 是否需要用户确认 */
    private Boolean needConfirm;

    /** 询问用户的提示语 */
    private String askUser;

    /** 确认后调用的 Agent ID */
    private String confirmAgentId;

    /** 是否多设备操作 */
    private boolean multiDevice;

    /**
     * 默认构造方法
     */
    public ToolResultVO() {
    }

    /**
     * 快速构建成功结果
     *
     * @param data    业务数据
     * @param message 提示信息
     * @return 成功结果对象
     */
    public static ToolResultVO success(Object data, String message) {
        ToolResultVO vo = new ToolResultVO();
        vo.setSuccess(true);
        vo.setData(data);
        vo.setMessage(message);
        return vo;
    }

    /**
     * 快速构建失败结果
     *
     * @param message 错误信息
     * @return 失败结果对象
     */
    public static ToolResultVO fail(String message) {
        ToolResultVO vo = new ToolResultVO();
        vo.setSuccess(false);
        vo.setMessage(message);
        return vo;
    }

    /**
     * 快速构建需确认结果
     *
     * @param data        预览数据
     * @param routePath   前端路由
     * @param broadcastText 广播文案
     * @param askUser     询问提示语
     * @return 需确认结果对象
     */
    public static ToolResultVO confirm(Object data, String routePath, String broadcastText, String askUser) {
        ToolResultVO vo = new ToolResultVO();
        vo.setSuccess(true);
        vo.setData(data);
        vo.setRoutePath(routePath);
        vo.setBroadcastText(broadcastText);
        vo.setNeedConfirm(true);
        vo.setAskUser(askUser);
        return vo;
    }

    // ==================== Getter / Setter ====================

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public String getRoutePath() {
        return routePath;
    }

    public void setRoutePath(String routePath) {
        this.routePath = routePath;
    }

    public String getBroadcastText() {
        return broadcastText;
    }

    public void setBroadcastText(String broadcastText) {
        this.broadcastText = broadcastText;
    }

    public Boolean getNeedConfirm() {
        return needConfirm;
    }

    public void setNeedConfirm(Boolean needConfirm) {
        this.needConfirm = needConfirm;
    }

    public String getAskUser() {
        return askUser;
    }

    public void setAskUser(String askUser) {
        this.askUser = askUser;
    }

    public String getConfirmAgentId() {
        return confirmAgentId;
    }

    public void setConfirmAgentId(String confirmAgentId) {
        this.confirmAgentId = confirmAgentId;
    }

    public boolean isMultiDevice() {
        return multiDevice;
    }

    public void setMultiDevice(boolean multiDevice) {
        this.multiDevice = multiDevice;
    }
}
