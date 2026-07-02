package com.petdoctor.dto.dify;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Dify 聊天消息请求体
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DifyChatRequest {

    /** Dify 工作流变量输入 */
    private Map<String, String> inputs;

    /** 用户病情描述 */
    private String query;

    /** 响应模式：streaming 流式 */
    @JsonProperty("response_mode")
    private String responseMode;

    /** 终端用户标识 */
    private String user;

    /** 多模态附件（图片等） */
    private List<DifyFileItem> files;

    /**
     * Dify 文件附件项
     */
    @Data
    @Builder
    public static class DifyFileItem {

        private String type;

        @JsonProperty("transfer_method")
        private String transferMethod;

        private String url;
    }
}
