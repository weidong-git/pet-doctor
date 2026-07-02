package com.petdoctor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petdoctor.config.DifyProperties;
import com.petdoctor.dto.ChatCompletionRequest;
import com.petdoctor.dto.dify.DifyChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AI 问诊流式服务：组装宠物上下文，对接 Dify 并以 SSE 转发
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String RESPONSE_MODE_STREAMING = "streaming";
    private static final String FILE_TYPE_IMAGE = "image";
    private static final String TRANSFER_METHOD_REMOTE_URL = "remote_url";

    private final WebClient difyWebClient;
    private final DifyProperties difyProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 流式问诊主入口
     */
    public Flux<ServerSentEvent<String>> streamChat(ChatCompletionRequest request) {
        String petProfile = getPetProfile(request.getPetId());
        DifyChatRequest difyRequest = buildDifyRequest(request, petProfile);

        log.info("发起 Dify 流式问诊, userId={}, petId={}", request.getUserId(), request.getPetId());

        return difyWebClient.post()
                .uri(difyProperties.getChatMessagesUrl())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + difyProperties.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .body(BodyInserters.fromValue(difyRequest))
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(this::parseDifyChunk)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(content -> ServerSentEvent.<String>builder()
                        .event("message")
                        .data(content)
                        .build())
                .concatWith(Flux.just(ServerSentEvent.<String>builder()
                        .event("done")
                        .data("[DONE]")
                        .build()))
                .onErrorResume(this::handleStreamError);
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

    /**
     * 组装 Dify 请求体
     */
    private DifyChatRequest buildDifyRequest(ChatCompletionRequest request, String petProfile) {
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
                .responseMode(RESPONSE_MODE_STREAMING)
                .user(request.getUserId())
                .files(files.isEmpty() ? null : files)
                .build();
    }

    /**
     * 解析 Dify SSE 数据块，提取 answer 字段
     */
    private Flux<Optional<String>> parseDifyChunk(String rawChunk) {
        return Flux.fromArray(rawChunk.split("\n"))
                .map(String::trim)
                .filter(line -> line.startsWith("data:"))
                .map(line -> line.substring(5).trim())
                .filter(data -> !data.isEmpty() && !"[DONE]".equals(data))
                .flatMap(data -> {
                    try {
                        JsonNode node = objectMapper.readTree(data);
                        String event = node.path("event").asText();

                        // Dify 流式消息事件
                        if ("message".equals(event) || "agent_message".equals(event)) {
                            String answer = node.path("answer").asText("");
                            if (StringUtils.hasText(answer)) {
                                return Mono.just(Optional.of(answer));
                            }
                        }
                        // 工作流文本块事件
                        if ("text_chunk".equals(event)) {
                            String text = node.path("data").path("text").asText("");
                            if (StringUtils.hasText(text)) {
                                return Mono.just(Optional.of(text));
                            }
                        }
                        return Mono.just(Optional.<String>empty());
                    } catch (Exception e) {
                        log.warn("解析 Dify 数据块失败: {}", data, e);
                        return Mono.just(Optional.empty());
                    }
                });
    }

    /**
     * 流式异常处理：向前端下发标准错误 SSE
     */
    private Flux<ServerSentEvent<String>> handleStreamError(Throwable throwable) {
        String errorMessage;
        if (throwable instanceof WebClientResponseException webEx) {
            errorMessage = "Dify 服务异常: HTTP " + webEx.getStatusCode().value();
            log.error("Dify 请求失败, status={}, body={}", webEx.getStatusCode(), webEx.getResponseBodyAsString());
        } else {
            errorMessage = "问诊服务暂时不可用，请稍后重试";
            log.error("流式问诊异常", throwable);
        }

        return Flux.just(
                ServerSentEvent.<String>builder()
                        .event("error")
                        .data(errorMessage)
                        .build(),
                ServerSentEvent.<String>builder()
                        .event("done")
                        .data("[DONE]")
                        .build()
        );
    }
}
