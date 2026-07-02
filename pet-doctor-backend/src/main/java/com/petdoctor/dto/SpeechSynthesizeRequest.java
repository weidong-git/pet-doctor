package com.petdoctor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 语音合成请求
 */
@Data
public class SpeechSynthesizeRequest {

    @NotBlank(message = "text 不能为空")
    private String text;
}
