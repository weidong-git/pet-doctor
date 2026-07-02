package com.petdoctor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petdoctor.config.DeepSeekProperties;
import com.petdoctor.config.DifyProperties;
import com.petdoctor.config.LlmProperties;
import com.petdoctor.dto.ChatCompletionRequest;
import com.petdoctor.dto.ChatSyncResponse;
import com.petdoctor.dto.dify.DifyChatRequest;
import com.petdoctor.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AI 问诊流式服务：支持 DeepSeek / Dify
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String RESPONSE_MODE_STREAMING = "streaming";
    private static final String RESPONSE_MODE_BLOCKING = "blocking";
    private static final String FILE_TYPE_IMAGE = "image";
    private static final String TRANSFER_METHOD_REMOTE_URL = "remote_url";
    private static final String PROVIDER_DEEPSEEK = "deepseek";
    private static final String PROVIDER_DIFY = "dify";
    private static final Duration SYNC_TIMEOUT = Duration.ofSeconds(55);

    private final WebClient difyWebClient;
    private final LlmProperties llmProperties;
    private final DeepSeekProperties deepSeekProperties;
    private final DifyProperties difyProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 流式问诊主入口
     */
    public Flux<ServerSentEvent<String>> streamChat(ChatCompletionRequest request) {
        String petProfile = getPetProfile(request.getPetId());
        if (PROVIDER_DEEPSEEK.equalsIgnoreCase(llmProperties.getProvider())) {
            return streamDeepSeekChat(request, petProfile);
        }
        return streamDifyChat(request, petProfile);
    }

    /**
     * 同步问诊（微信小程序专用，一次返回完整回复）
     */
    public Mono<ChatSyncResponse> chatSync(ChatCompletionRequest request) {
        String petProfile = getPetProfile(request.getPetId());
        if (PROVIDER_DEEPSEEK.equalsIgnoreCase(llmProperties.getProvider())) {
            return chatSyncDeepSeek(request, petProfile);
        }
        return chatSyncDify(request, petProfile);
    }

    private Mono<ChatSyncResponse> chatSyncDeepSeek(ChatCompletionRequest request, String petProfile) {
        if (!deepSeekProperties.isConfigured()) {
            return Mono.error(new BusinessException(503, "DeepSeek 未配置，请设置 DEEPSEEK_API_KEY"));
        }

        log.info("发起 DeepSeek 同步问诊, userId={}, petId={}, model={}",
                request.getUserId(), request.getPetId(), deepSeekProperties.getModel());

        Map<String, Object> body = Map.of(
                "model", deepSeekProperties.getModel(),
                "stream", false,
                "messages", buildDeepSeekMessages(request, petProfile)
        );

        return difyWebClient.post()
                .uri(deepSeekProperties.getChatCompletionsUrl())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + deepSeekProperties.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(body))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(SYNC_TIMEOUT)
                .map(this::extractDeepSeekContent)
                .onErrorMap(this::mapSyncError);
    }

    private Mono<ChatSyncResponse> chatSyncDify(ChatCompletionRequest request, String petProfile) {
        DifyChatRequest difyRequest = buildDifyRequest(request, petProfile, RESPONSE_MODE_BLOCKING);

        log.info("发起 Dify 同步问诊, userId={}, petId={}", request.getUserId(), request.getPetId());

        return difyWebClient.post()
                .uri(difyProperties.getChatMessagesUrl())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + difyProperties.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(difyRequest))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(SYNC_TIMEOUT)
                .map(node -> {
                    String answer = node.path("answer").asText("");
                    if (!StringUtils.hasText(answer)) {
                        throw new BusinessException(422, "Dify 未返回有效回复");
                    }
                    return new ChatSyncResponse(answer);
                })
                .onErrorMap(ex -> mapSyncError(ex, PROVIDER_DIFY));
    }

    private ChatSyncResponse extractDeepSeekContent(JsonNode node) {
        String content = node.path("choices").path(0).path("message").path("content").asText("");
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(422, "DeepSeek 未返回有效回复");
        }
        return new ChatSyncResponse(content);
    }

    private Throwable mapSyncError(Throwable throwable) {
        return mapSyncError(throwable, PROVIDER_DEEPSEEK);
    }

    private Throwable mapSyncError(Throwable throwable, String provider) {
        if (throwable instanceof BusinessException) {
            return throwable;
        }
        if (isTimeout(throwable)) {
            return new BusinessException(504, provider + " 响应超时，请稍后重试或检查服务是否正常运行");
        }
        if (throwable instanceof WebClientResponseException webEx) {
            log.error("{} 同步问诊失败, status={}, body={}", provider, webEx.getStatusCode(), webEx.getResponseBodyAsString());
            if (webEx.getStatusCode().value() == 401) {
                return new BusinessException(401, PROVIDER_DIFY.equals(provider)
                        ? "Dify 鉴权失败：请检查 app- 开头的应用密钥"
                        : "DeepSeek 鉴权失败：API Key 无效或已吊销，请到 platform.deepseek.com/api_keys 重新创建并更新 application-local.yml");
            }
            return new BusinessException(webEx.getStatusCode().value(),
                    provider + " 服务异常: HTTP " + webEx.getStatusCode().value());
        }
        String networkMessage = resolveNetworkErrorMessage(throwable, provider);
        if (networkMessage != null) {
            log.error("{} 同步问诊网络异常: {}", provider, networkMessage, throwable);
            return new BusinessException(503, networkMessage);
        }
        log.error("同步问诊异常", throwable);
        return new BusinessException(500, "问诊服务暂时不可用，请稍后重试");
    }

    private String resolveNetworkErrorMessage(Throwable throwable, String provider) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (isTimeout(current)) {
                return provider + " 响应超时，请稍后重试";
            }
            if (current instanceof UnknownHostException) {
                return provider + " 域名解析失败，请检查网络/DNS；或在 application-local.yml 改用 llm.provider=dify";
            }
            if (current instanceof ConnectException) {
                return provider + " 连接失败，请确认网络畅通且服务地址可访问";
            }
        }
        return null;
    }

    private boolean isTimeout(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("timeout")) {
                return true;
            }
        }
        return false;
    }

    /**
     * DeepSeek 流式问诊
     */
    private Flux<ServerSentEvent<String>> streamDeepSeekChat(ChatCompletionRequest request, String petProfile) {
        if (!deepSeekProperties.isConfigured()) {
            return handleStreamError(
                    new BusinessException(503, "DeepSeek 未配置，请设置 DEEPSEEK_API_KEY"),
                    PROVIDER_DEEPSEEK);
        }

        log.info("发起 DeepSeek 流式问诊, userId={}, petId={}, model={}",
                request.getUserId(), request.getPetId(), deepSeekProperties.getModel());

        Map<String, Object> body = Map.of(
                "model", deepSeekProperties.getModel(),
                "stream", true,
                "messages", buildDeepSeekMessages(request, petProfile)
        );

        return difyWebClient.post()
                .uri(deepSeekProperties.getChatCompletionsUrl())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + deepSeekProperties.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .body(BodyInserters.fromValue(body))
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .flatMap(this::parseOpenAiSseEvent)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(this::buildMessageEvent)
                .concatWith(doneEvent())
                .onErrorResume(ex -> handleStreamError(ex, PROVIDER_DEEPSEEK));
    }

    /**
     * Dify 流式问诊
     */
    private Flux<ServerSentEvent<String>> streamDifyChat(ChatCompletionRequest request, String petProfile) {
        DifyChatRequest difyRequest = buildDifyRequest(request, petProfile);

        log.info("发起 Dify 流式问诊, userId={}, petId={}", request.getUserId(), request.getPetId());

        return difyWebClient.post()
                .uri(difyProperties.getChatMessagesUrl())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + difyProperties.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .body(BodyInserters.fromValue(difyRequest))
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .flatMap(this::parseDifySseEvent)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(this::buildMessageEvent)
                .concatWith(doneEvent())
                .onErrorResume(ex -> handleStreamError(ex, PROVIDER_DIFY));
    }

    /**
     * Mock：根据宠物 ID 获取宠物档案信息
     */
    public String getPetProfile(Long petId) {
        return switch (petId.intValue()) {
            case 1 -> "宠物名：小贝，品种：金毛，年龄：3岁，体重：28kg";
            case 2 -> "宠物名：富贵，品种：布偶猫，年龄：2岁，体重：4.5kg";
            default -> "宠物名：未知，品种：未知，年龄：未知";
        };
    }

    private List<Map<String, String>> buildDeepSeekMessages(ChatCompletionRequest request, String petProfile) {
        String systemContent = deepSeekProperties.getSystemPrompt() + "\n\n当前宠物档案：" + petProfile;
        StringBuilder userContent = new StringBuilder(request.getQuery());
        if (StringUtils.hasText(request.getImageUrl())) {
            userContent.append("\n\n【用户上传了患处图片】").append(request.getImageUrl())
                    .append("\n请结合图片链接给出初步观察建议（当前模型为文本模式，无法直接识图）。");
        }

        return List.of(
                Map.of("role", "system", "content", systemContent),
                Map.of("role", "user", "content", userContent.toString())
        );
    }

    private DifyChatRequest buildDifyRequest(ChatCompletionRequest request, String petProfile) {
        return buildDifyRequest(request, petProfile, RESPONSE_MODE_STREAMING);
    }

    private DifyChatRequest buildDifyRequest(ChatCompletionRequest request, String petProfile, String responseMode) {
        List<DifyChatRequest.DifyFileItem> files = StringUtils.hasText(request.getImageUrl())
                ? List.of(DifyChatRequest.DifyFileItem.builder()
                .type(FILE_TYPE_IMAGE)
                .transferMethod(TRANSFER_METHOD_REMOTE_URL)
                .url(request.getImageUrl())
                .build())
                : Collections.emptyList();

        return DifyChatRequest.builder()
                .inputs(Map.of("pet_profile", petProfile))
                .query(request.getQuery())
                .responseMode(responseMode)
                .user(request.getUserId())
                .files(files.isEmpty() ? null : files)
                .build();
    }

    private Flux<Optional<String>> parseOpenAiSseEvent(ServerSentEvent<String> sse) {
        String data = sse.data();
        if (!StringUtils.hasText(data) || "[DONE]".equals(data)) {
            return Flux.empty();
        }

        try {
            JsonNode node = objectMapper.readTree(data);
            String content = node.path("choices").path(0).path("delta").path("content").asText("");
            if (StringUtils.hasText(content)) {
                return Flux.just(Optional.of(content));
            }
        } catch (Exception e) {
            log.warn("解析 DeepSeek SSE 事件失败: {}", data, e);
        }
        return Flux.empty();
    }

    private Flux<Optional<String>> parseDifySseEvent(ServerSentEvent<String> sse) {
        String data = sse.data();
        if (!StringUtils.hasText(data) || "[DONE]".equals(data)) {
            return Flux.empty();
        }

        try {
            JsonNode node = objectMapper.readTree(data);
            String event = node.path("event").asText();

            if ("message".equals(event) || "agent_message".equals(event)) {
                String answer = node.path("answer").asText("");
                if (StringUtils.hasText(answer)) {
                    return Flux.just(Optional.of(answer));
                }
            }
            if ("text_chunk".equals(event)) {
                String text = node.path("data").path("text").asText("");
                if (StringUtils.hasText(text)) {
                    return Flux.just(Optional.of(text));
                }
            }
        } catch (Exception e) {
            log.warn("解析 Dify SSE 事件失败: {}", data, e);
        }

        return Flux.empty();
    }

    private ServerSentEvent<String> buildMessageEvent(String content) {
        return ServerSentEvent.<String>builder()
                .event("message")
                .data(content)
                .build();
    }

    private Flux<ServerSentEvent<String>> doneEvent() {
        return Flux.just(ServerSentEvent.<String>builder()
                .event("done")
                .data("[DONE]")
                .build());
    }

    private Flux<ServerSentEvent<String>> handleStreamError(Throwable throwable, String provider) {
        String errorMessage;
        if (throwable instanceof BusinessException businessException) {
            errorMessage = businessException.getMessage();
        } else if (throwable instanceof WebClientResponseException webEx) {
            if (webEx.getStatusCode().value() == 401) {
                errorMessage = PROVIDER_DIFY.equals(provider)
                        ? "Dify 鉴权失败：请检查 app- 开头的应用密钥"
                        : "DeepSeek 鉴权失败：API Key 无效或已吊销，请到 platform.deepseek.com/api_keys 重新创建";
            } else {
                errorMessage = PROVIDER_DIFY.equals(provider)
                        ? "Dify 服务异常: HTTP " + webEx.getStatusCode().value()
                        : "DeepSeek 服务异常: HTTP " + webEx.getStatusCode().value();
            }
            log.error("{} 请求失败, status={}, body={}", provider, webEx.getStatusCode(), webEx.getResponseBodyAsString());
        } else {
            String networkMessage = resolveNetworkErrorMessage(throwable, provider);
            if (networkMessage != null) {
                errorMessage = networkMessage;
            } else {
                errorMessage = "问诊服务暂时不可用，请稍后重试";
            }
            log.error("流式问诊异常", throwable);
        }

        return Flux.just(
                ServerSentEvent.<String>builder().event("error").data(errorMessage).build(),
                ServerSentEvent.<String>builder().event("done").data("[DONE]").build()
        );
    }
}
