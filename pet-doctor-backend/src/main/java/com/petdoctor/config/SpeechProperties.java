package com.petdoctor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 语音服务配置（OpenAI 兼容 Whisper / TTS）
 */
@Data
@Component
@ConfigurationProperties(prefix = "speech")
public class SpeechProperties {

    /** OpenAI 兼容 API 基础地址 */
    private String baseUrl = "https://api.openai.com/v1";

    /** API Key */
    private String apiKey;

    /** 语音识别模型 */
    private String sttModel = "whisper-1";

    /** 语音合成模型 */
    private String ttsModel = "tts-1";

    /** 语音合成音色 */
    private String ttsVoice = "nova";

    /** 合成音频缓存 TTL（秒） */
    private int audioCacheTtlSeconds = 300;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
