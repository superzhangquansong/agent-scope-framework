package com.agent.scope.framework.hdl;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 多模态图片 DTO。
 *
 * <p>用于 FloorPlanTool 传递用户上传的户型图图片信息。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 图片 Base64 数据（不含 data: 前缀） */
    private String base64;

    /** 图片 MIME 类型（如 image/png、image/jpeg） */
    private String mimeType;

    /** 原始文件名（可选，用于日志记录） */
    private String fileName;
}
