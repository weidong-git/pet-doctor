/** 后端 API 基础地址（小程序真机调试请改为局域网 IP） */
const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

import type { ChatCompletionParams, StreamCallbacks } from '@/types'

/**
 * 将 ArrayBuffer 解码为 UTF-8 字符串
 */
function arrayBufferToString(buffer: ArrayBuffer): string {
  const uint8Array = new Uint8Array(buffer)
  let result = ''
  for (let i = 0; i < uint8Array.length; i++) {
    result += String.fromCharCode(uint8Array[i])
  }
  try {
    return decodeURIComponent(escape(result))
  } catch {
    return result
  }
}

/**
 * 解析 SSE 文本块，提取 data 字段内容
 */
function parseSseChunk(raw: string, onChunk: (text: string) => void): void {
  const lines = raw.split('\n')
  lines.forEach((line) => {
    const trimmed = line.trim()
    if (!trimmed.startsWith('data:')) return
    const data = trimmed.slice(5).trim()
    if (!data || data === '[DONE]') return
    onChunk(data)
  })
}

/**
 * 微信小程序：使用 wx.request + enableChunked 流式接收 SSE
 */
function sendMessageWeixin(
  params: ChatCompletionParams,
  callbacks: StreamCallbacks,
): void {
  const requestTask = wx.request({
    url: `${BASE_URL}/api/v1/chat/completions`,
    method: 'POST',
    enableChunked: true,
    header: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    data: params,
    success: () => callbacks.onDone(),
    fail: (err) => callbacks.onError(err.errMsg || '网络请求失败'),
  })

  requestTask.onChunkReceived((res) => {
    const text = arrayBufferToString(res.data as ArrayBuffer)
    parseSseChunk(text, callbacks.onChunk)
  })
}

/**
 * H5 环境：使用 fetch + ReadableStream 流式接收 SSE
 */
async function sendMessageH5(
  params: ChatCompletionParams,
  callbacks: StreamCallbacks,
): Promise<void> {
  try {
    const response = await fetch(`${BASE_URL}/api/v1/chat/completions`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
      },
      body: JSON.stringify(params),
    })

    if (!response.ok || !response.body) {
      callbacks.onError(`请求失败: ${response.status}`)
      return
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const parts = buffer.split('\n\n')
      buffer = parts.pop() || ''
      parts.forEach((part) => parseSseChunk(part, callbacks.onChunk))
    }

    callbacks.onDone()
  } catch (err) {
    callbacks.onError(err instanceof Error ? err.message : '网络异常')
  }
}

/**
 * 发送问诊消息（自动适配微信/H5 环境）
 */
export function sendMessage(
  params: ChatCompletionParams,
  callbacks: StreamCallbacks,
): void {
  // #ifdef MP-WEIXIN
  sendMessageWeixin(params, callbacks)
  return
  // #endif

  // #ifdef H5
  void sendMessageH5(params, callbacks)
  return
  // #endif

  // #ifndef MP-WEIXIN || H5
  callbacks.onError('当前平台暂不支持流式问诊')
  // #endif
}
