package com.petdoctor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.petdoctor.config.SpeechProperties;
import com.petdoctor.dto.SpeechRecognizeResponse;
import com.petdoctor.dto.SpeechStatusResponse;
import com.petdoctor.dto.SpeechSynthesizeResponse;
import com.petdoctor.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 语音识别与合成服务（OpenAI 兼容接口）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpeechService {

    private static final String AUDIO_CONTENT_TYPE = "audio/mpeg";

    private final WebClient difyWebClient;
    private final SpeechProperties speechProperties;
    private final Map<String, CachedAudio> audioCache = new ConcurrentHashMap<>();
    /** 相同文本 TTS 结果缓存，减少重复调用上游 */
    private final Map<String, String> ttsTextToAudioId = new ConcurrentHashMap<>();

    /**
     * 查询语音服务是否已配置
     */
    public SpeechStatusResponse getStatus() {
        if (speechProperties.isConfigured()) {
            return new SpeechStatusResponse(true, "语音服务已就绪");
        }
        return new SpeechStatusResponse(false,
                "语音服务未配置，请在 application-local.yml 设置 speech.api-key（OpenAI 兼容 Whisper/TTS）");
    }

    /**
     * 语音转文字
     */
    public Mono<SpeechRecognizeResponse> recognize(FilePart filePart) {
        validateConfigured();

        return DataBufferUtils.join(filePart.content())
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    MultipartBodyBuilder builder = new MultipartBodyBuilder();
                    builder.part("file", bytes)
                            .filename("voice.mp3")
                            .contentType(MediaType.parseMediaType("audio/mpeg"));
                    builder.part("model", speechProperties.getSttModel());
                    builder.part("language", "zh");

                    return createClient().post()
                            .uri(speechProperties.getBaseUrl() + "/audio/transcriptions")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + speechProperties.getApiKey())
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .body(BodyInserters.fromMultipartData(builder.build()))
                            .retrieve()
                            .bodyToMono(JsonNode.class)
                            .map(node -> {
                                String text = node.path("text").asText("").trim();
                                if (!StringUtils.hasText(text)) {
                                    throw new BusinessException(422, "未识别到有效语音内容");
                                }
                                return new SpeechRecognizeResponse(text);
                            });
                })
                .onErrorMap(this::mapSpeechError);
    }

    /**
     * 文字转语音
     */
    public Mono<SpeechSynthesizeResponse> synthesize(String text) {
        validateConfigured();

        String sanitized = sanitizeText(text);
        if (!StringUtils.hasText(sanitized)) {
            return Mono.error(new BusinessException(400, "无可朗读的文本内容"));
        }

        String cachedAudioId = ttsTextToAudioId.get(sanitized);
        if (cachedAudioId != null) {
            CachedAudio cached = audioCache.get(cachedAudioId);
            if (cached != null && !isExpired(cached)) {
                return Mono.just(new SpeechSynthesizeResponse("/api/v1/speech/audio/" + cachedAudioId));
            }
            ttsTextToAudioId.remove(sanitized);
        }

        Map<String, Object> body = Map.of(
                "model", speechProperties.getTtsModel(),
                "input", sanitized,
                "voice", speechProperties.getTtsVoice(),
                "response_format", "mp3"
        );

        return createClient().post()
                .uri(speechProperties.getBaseUrl() + "/audio/speech")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + speechProperties.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(byte[].class)
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(2))
                        .filter(ex -> ex instanceof WebClientResponseException webEx
                                && webEx.getStatusCode().value() == 429
                                && !isInsufficientQuota(webEx))
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                .map(audioBytes -> {
                    String audioId = UUID.randomUUID().toString().replace("-", "");
                    audioCache.put(audioId, new CachedAudio(audioBytes, Instant.now()));
                    ttsTextToAudioId.put(sanitized, audioId);
                    cleanupExpiredAudio();
                    return new SpeechSynthesizeResponse("/api/v1/speech/audio/" + audioId);
                })
                .onErrorMap(this::mapSpeechError);
    }

    /**
     * 获取缓存的音频数据
     */
    public Mono<byte[]> getCachedAudio(String audioId) {
        CachedAudio cached = audioCache.get(audioId);
        if (cached == null || isExpired(cached)) {
            audioCache.remove(audioId);
            return Mono.error(new BusinessException(404, "语音文件不存在或已过期"));
        }
        return Mono.just(cached.data());
    }

    private WebClient createClient() {
        return difyWebClient;
    }

    private void validateConfigured() {
        if (!speechProperties.isConfigured()) {
            throw new BusinessException(503,
                    "语音服务未配置，请设置环境变量 SPEECH_API_KEY（OpenAI 兼容 Whisper/TTS 密钥）");
        }
    }

    private String sanitizeText(String text) {
        return text.replaceAll("[⚠️🚨ⓘ#*_`>\\[\\]()]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void cleanupExpiredAudio() {
        audioCache.entrySet().removeIf(entry -> isExpired(entry.getValue()));
        ttsTextToAudioId.entrySet().removeIf(entry -> {
            CachedAudio cached = audioCache.get(entry.getValue());
            return cached == null || isExpired(cached);
        });
        if (ttsTextToAudioId.size() > 100) {
            ttsTextToAudioId.clear();
        }
    }

    private boolean isExpired(CachedAudio cached) {
        return cached.createdAt()
                .plusSeconds(speechProperties.getAudioCacheTtlSeconds())
                .isBefore(Instant.now());
    }

    private Throwable mapSpeechError(Throwable throwable) {
        if (throwable instanceof BusinessException businessException) {
            return businessException;
        }
        if (throwable instanceof WebClientResponseException webEx) {
            log.error("语音服务请求失败, status={}, body={}", webEx.getStatusCode(), webEx.getResponseBodyAsString());
            int status = webEx.getStatusCode().value();
            if (isInsufficientQuota(webEx)) {
                return new BusinessException(402,
                        "OpenAI 语音配额已用尽，请前往 platform.openai.com 充值，或更换 speech.api-key / base-url");
            }
            if (status == 429) {
                return new BusinessException(429, "语音合成请求过于频繁，请稍后再试");
            }
            if (status == 401) {
                return new BusinessException(401, "语音服务鉴权失败，请检查 speech.api-key");
            }
            return new BusinessException(status, "语音服务异常: HTTP " + status);
        }
        log.error("语音服务异常", throwable);
        return new BusinessException(500, "语音服务暂时不可用");
    }

    private boolean isInsufficientQuota(WebClientResponseException webEx) {
        String body = webEx.getResponseBodyAsString();
        return body != null && body.contains("insufficient_quota");
    }

    private record CachedAudio(byte[] data, Instant createdAt) {
    }
}
