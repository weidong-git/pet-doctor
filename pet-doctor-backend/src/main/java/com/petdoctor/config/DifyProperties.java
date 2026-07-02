package com.petdoctor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Dify API 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "dify")
public class DifyProperties {

    /** Dify API 基础地址 */
    private String baseUrl = "https://api.dify.ai/v1";

    /** Dify API 密钥 */
    private String apiKey;

    /** 聊天消息接口路径 */
    private String chatMessagesPath = "/chat-messages";

    /** 连接超时（毫秒） */
    private int connectTimeoutMs = 10000;

    /** 读取超时（毫秒） */
    private int readTimeoutMs = 120000;

    /**
     * 获取完整的聊天消息接口 URL
     */
    public String getChatMessagesUrl() {
        return baseUrl + chatMessagesPath;
    }
}
