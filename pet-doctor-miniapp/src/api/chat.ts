/** 后端 API 基础地址（微信开发者工具建议用 127.0.0.1，真机改为局域网 IP） */
const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080'

import type { ChatCompletionParams, StreamCallbacks } from '@/types'

interface ChatSyncResponse {
  content: string
}

interface ApiErrorBody {
  message?: string
}

/** 打字机定时器，用于组件卸载时清理 */
let typewriterTimer: ReturnType<typeof setTimeout> | null = null

function clearTypewriter() {
  if (typewriterTimer) {
    clearTimeout(typewriterTimer)
    typewriterTimer = null
  }
}

function parseApiError(data: unknown, fallback: string): string {
  if (typeof data === 'string') {
    try {
      const parsed = JSON.parse(data) as ApiErrorBody
      return parsed.message || fallback
    } catch {
      return data || fallback
    }
  }
  if (data && typeof data === 'object' && 'message' in data) {
    return String((data as ApiErrorBody).message)
  }
  return fallback
}

/**
 * 客户端打字机效果（模拟流式输出）
 */
function playTypewriter(text: string, callbacks: StreamCallbacks): void {
  clearTypewriter()
  let index = 0

  const tick = () => {
    if (index >= text.length) {
      typewriterTimer = null
      callbacks.onDone()
      return
    }

    const char = text[index]
    const step = char.charCodeAt(0) > 127 ? 2 : 1
    callbacks.onChunk(text.slice(index, index + step))
    index += step
    typewriterTimer = setTimeout(tick, 24)
  }

  tick()
}

/**
 * 微信小程序：普通 HTTP 同步接口 + 打字机效果（避免 enableChunked WebSocket 1006 错误）
 */
function sendMessageWeixin(
  params: ChatCompletionParams,
  callbacks: StreamCallbacks,
): void {
  clearTypewriter()

  uni.request({
    url: `${BASE_URL}/api/v1/chat/completions/sync`,
    method: 'POST',
    timeout: 60000,
    header: {
      'Content-Type': 'application/json',
    },
    data: params,
    success: (res) => {
      if (res.statusCode !== 200) {
        callbacks.onError(parseApiError(res.data, `请求失败: ${res.statusCode}`))
        return
      }

      let payload: unknown = res.data
      if (typeof payload === 'string') {
        if (payload.includes('event:error')) {
          const match = payload.match(/data:(.+)/m)
          callbacks.onError(match?.[1]?.trim() || 'AI 服务异常，请检查 DeepSeek API Key')
          return
        }
        try {
          payload = JSON.parse(payload) as ChatSyncResponse
        } catch {
          callbacks.onError('服务器返回格式异常，请重启后端后重试')
          return
        }
      }

      const data = payload as ChatSyncResponse
      if (!data?.content?.trim()) {
        callbacks.onError('未获取到 AI 回复')
        return
      }

      playTypewriter(data.content, callbacks)
    },
    fail: (err) => {
      const msg = err.errMsg || ''
      if (/timeout/i.test(msg)) {
        callbacks.onError('请求超时：请确认后端已启动，且 API 地址为 127.0.0.1:8080（非 localhost）')
        return
      }
      callbacks.onError(msg || '网络请求失败，请检查后端地址与 Dify/DeepSeek 配置')
    },
  })
}

/**
 * 解析 SSE 文本块
 */
function parseSseChunk(raw: string, callbacks: StreamCallbacks): boolean {
  let hasError = false
  raw.split('\n\n').forEach((block) => {
    if (!block.trim()) return

    let eventType = 'message'
    let data = ''
    block.split('\n').forEach((line) => {
      const trimmed = line.trim()
      if (trimmed.startsWith('event:')) {
        eventType = trimmed.slice(6).trim()
      } else if (trimmed.startsWith('data:')) {
        data = trimmed.slice(5).trim()
      }
    })

    if (!data || data === '[DONE]') return
    if (eventType === 'error') {
      hasError = true
      callbacks.onError(data)
      return
    }
    callbacks.onChunk(data)
  })
  return hasError
}

/**
 * H5 环境：fetch + ReadableStream 流式接收 SSE
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
    let streamError = false

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const parts = buffer.split('\n\n')
      buffer = parts.pop() || ''
      parts.forEach((part) => {
        streamError = parseSseChunk(part, callbacks) || streamError
      })
    }

    if (buffer.trim()) {
      streamError = parseSseChunk(buffer, callbacks) || streamError
    }

    if (!streamError) {
      callbacks.onDone()
    }
  } catch (err) {
    callbacks.onError(err instanceof Error ? err.message : '网络异常')
  }
}

/**
 * 发送问诊消息（微信走同步接口，H5 走 SSE 流式）
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

/** 取消进行中的打字机效果 */
export function cancelActiveChat(): void {
  clearTypewriter()
}
