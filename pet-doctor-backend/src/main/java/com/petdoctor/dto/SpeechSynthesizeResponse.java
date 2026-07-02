package com.petdoctor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 语音合成响应
 */
@Data
@AllArgsConstructor
public class SpeechSynthesizeResponse {

    private String audioUrl;
}
