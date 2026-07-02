package com.petdoctor.controller;

import com.petdoctor.dto.SpeechRecognizeResponse;
import com.petdoctor.dto.SpeechStatusResponse;
import com.petdoctor.dto.SpeechSynthesizeRequest;
import com.petdoctor.dto.SpeechSynthesizeResponse;
import com.petdoctor.service.SpeechService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 语音识别与合成接口
 */
@RestController
@RequestMapping("/api/v1/speech")
@RequiredArgsConstructor
public class SpeechController {

    private final SpeechService speechService;

    /**
     * 语音服务是否可用（是否已配置 API Key）
     */
    @GetMapping("/status")
    public Mono<SpeechStatusResponse> status() {
        return Mono.just(speechService.getStatus());
    }

    /**
     * 上传录音并识别为文字
     */
    @PostMapping(value = "/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<SpeechRecognizeResponse> recognize(@RequestPart("file") FilePart file) {
        return speechService.recognize(file);
    }

    /**
     * 文字转语音，返回音频播放地址
     */
    @PostMapping("/synthesize")
    public Mono<SpeechSynthesizeResponse> synthesize(@Valid @RequestBody SpeechSynthesizeRequest request) {
        return speechService.synthesize(request.getText());
    }

    /**
     * 获取合成的 MP3 音频
     */
    @GetMapping("/audio/{audioId}")
    public Mono<ResponseEntity<byte[]>> getAudio(@PathVariable String audioId) {
        return speechService.getCachedAudio(audioId)
                .map(bytes -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
                        .header(HttpHeaders.CACHE_CONTROL, "no-store")
                        .body(bytes));
    }
}
