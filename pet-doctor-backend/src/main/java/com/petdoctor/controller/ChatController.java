package com.petdoctor.controller;

import com.petdoctor.dto.ChatCompletionRequest;
import com.petdoctor.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

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
    public Flux<ServerSentEvent<String>> completions(@Valid @RequestBody ChatCompletionRequest request) {
        return chatService.streamChat(request);
    }
}
