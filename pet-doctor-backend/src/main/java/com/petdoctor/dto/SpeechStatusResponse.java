package com.petdoctor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 语音服务可用性
 */
@Data
@AllArgsConstructor
public class SpeechStatusResponse {

    private boolean available;

    private String message;
}
