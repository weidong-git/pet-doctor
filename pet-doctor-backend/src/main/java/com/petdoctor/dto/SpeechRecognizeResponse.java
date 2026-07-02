package com.petdoctor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 语音识别响应
 */
@Data
@AllArgsConstructor
public class SpeechRecognizeResponse {

    private String text;
}
