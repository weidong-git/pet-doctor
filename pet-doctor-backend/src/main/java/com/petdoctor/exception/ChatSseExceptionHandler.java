package com.petdoctor.exception;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

/**
 * 聊天 SSE 流式接口参数校验：仅 /completions 返回 SSE 格式错误，sync 接口走 GlobalExceptionHandler
 */
@RestControllerAdvice(assignableTypes = com.petdoctor.controller.ChatController.class)
public class ChatSseExceptionHandler {

    private boolean isSseStreamEndpoint(ServerWebExchange exchange) {
        return exchange.getRequest().getPath().value().endsWith("/completions");
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Flux<ServerSentEvent<String>> handleValidation(WebExchangeBindException ex, ServerWebExchange exchange) {
        if (!isSseStreamEndpoint(exchange)) {
            return Flux.error(ex);
        }
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("请求参数无效");
        return errorFlux(message);
    }

    private Flux<ServerSentEvent<String>> errorFlux(String message) {
        return Flux.just(
                ServerSentEvent.<String>builder()
                        .event("error")
                        .data(message)
                        .build(),
                ServerSentEvent.<String>builder()
                        .event("done")
                        .data("[DONE]")
                        .build()
        );
    }
}
