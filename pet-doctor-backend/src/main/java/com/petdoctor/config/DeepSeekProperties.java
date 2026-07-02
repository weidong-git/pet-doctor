package com.petdoctor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DeepSeek API 配置（OpenAI 兼容接口）
 */
@Data
@Component
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekProperties {

    /** API 基础地址 */
    private String baseUrl = "https://api.deepseek.com";

    /** API Key */
    private String apiKey;

    /** 模型名称 */
    private String model = "deepseek-chat";

    /** 系统提示词 */
    private String systemPrompt = """
            你是一位专业、温和的 AI 宠物医生，擅长犬猫常见健康问题科普与居家护理建议。
            请用简体中文回答，条理清晰，必要时分点说明。
            每次回答末尾提醒：以上建议仅供参考，不能替代专业兽医诊断，如有紧急情况请立即就医。
            """;

    /** 连接超时（毫秒） */
    private int connectTimeoutMs = 10000;

    /** 读取超时（毫秒） */
    private int readTimeoutMs = 120000;

    public String getChatCompletionsUrl() {
        return baseUrl + "/chat/completions";
    }

    public String getApiKey() {
        return apiKey == null ? null : apiKey.trim();
    }

    public boolean isConfigured() {
        String key = getApiKey();
        return key != null && !key.isBlank();
    }
}
