package com.petdoctor.controller;

import com.petdoctor.dto.ChatCompletionRequest;
import com.petdoctor.dto.ChatSyncResponse;
import com.petdoctor.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * AI 问诊流式接口
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 多模态 AI 问诊流式接口（SSE）
     *
     * @param request 问诊请求参数
     * @return SSE 流式响应
     */
    @PostMapping(value = "/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> completions(
            @Valid @RequestBody ChatCompletionRequest request,
            ServerHttpResponse response) {
        response.getHeaders().setContentType(MediaType.TEXT_EVENT_STREAM);
        response.getHeaders().setCacheControl("no-cache, no-store, must-revalidate");
        response.getHeaders().set("Connection", "keep-alive");
        response.getHeaders().set("X-Accel-Buffering", "no");
        return chatService.streamChat(request);
    }

    /**
     * 同步问诊接口（JSON 一次返回，供微信小程序使用）
     */
    @PostMapping(value = "/completions/sync", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ChatSyncResponse> completionsSync(@Valid @RequestBody ChatCompletionRequest request) {
        return chatService.chatSync(request);
    }
}
