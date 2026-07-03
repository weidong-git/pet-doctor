package com.petdoctor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 文件上传响应
 */
@Data
@AllArgsConstructor
public class FileUploadResponse {

    /** 文件 ID（含扩展名） */
    private String fileId;

    /** 可访问的完整 URL */
    private String url;

    /** MIME 类型 */
    private String contentType;
}
