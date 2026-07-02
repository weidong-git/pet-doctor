package com.petdoctor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 同步问诊响应（供微信小程序使用，避免 enableChunked WebSocket 问题）
 */
@Data
@AllArgsConstructor
public class ChatSyncResponse {

    private String content;
}
