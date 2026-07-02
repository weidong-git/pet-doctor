package com.petdoctor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 聊天问诊请求 DTO
 */
@Data
public class ChatCompletionRequest {

    /** 用户 ID */
    @NotBlank(message = "userId 不能为空")
    private String userId;

    /** 宠物 ID */
    @NotNull(message = "petId 不能为空")
    private Long petId;

    /** 用户输入的病情描述 */
    @NotBlank(message = "query 不能为空")
    private String query;

    /** 用户上传的图片地址（可选） */
    private String imageUrl;
}
