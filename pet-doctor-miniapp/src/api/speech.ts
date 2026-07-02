/** 后端 API 基础地址（微信开发者工具建议用 127.0.0.1） */
const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080'

export interface SpeechRecognizeResult {
  text: string
}

export interface SpeechSynthesizeResult {
  audioUrl: string
}

export interface SpeechStatusResult {
  available: boolean
  message: string
}

/** 查询后端语音服务是否已配置 */
export function fetchSpeechStatus(): Promise<SpeechStatusResult> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/v1/speech/status`,
      method: 'GET',
      timeout: 5000,
      success: (res) => {
        if (res.statusCode !== 200) {
          reject('语音服务状态查询失败')
          return
        }
        resolve(res.data as SpeechStatusResult)
      },
      fail: (err) => reject(err.errMsg || '语音服务状态查询失败'),
    })
  })
}

/**
 * 上传录音并识别为文字
 */
export function recognizeSpeech(filePath: string): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${BASE_URL}/api/v1/speech/recognize`,
      filePath,
      name: 'file',
      success: (res) => {
        if (res.statusCode !== 200) {
          reject(parseErrorMessage(res.data, '语音识别失败'))
          return
        }
        try {
          const data = JSON.parse(res.data) as SpeechRecognizeResult
          if (!data.text?.trim()) {
            reject('未识别到语音，请重试')
            return
          }
          resolve(data.text.trim())
        } catch {
          reject('语音识别响应解析失败')
        }
      },
      fail: (err) => reject(err.errMsg || '语音上传失败'),
    })
  })
}

/**
 * 文字转语音，返回可播放的音频地址
 */
export function synthesizeSpeech(text: string): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/v1/speech/synthesize`,
      method: 'POST',
      header: { 'Content-Type': 'application/json' },
      data: { text },
      success: (res) => {
        if (res.statusCode !== 200) {
          reject(parseErrorMessage(res.data, '语音合成失败'))
          return
        }
        const data = (typeof res.data === 'string' ? JSON.parse(res.data) : res.data) as SpeechSynthesizeResult
        if (!data.audioUrl) {
          reject('未获取到语音地址')
          return
        }
        resolve(data.audioUrl.startsWith('http') ? data.audioUrl : `${BASE_URL}${data.audioUrl}`)
      },
      fail: (err) => reject(err.errMsg || '语音合成请求失败'),
    })
  })
}

function parseErrorMessage(data: string | unknown, fallback: string): string {
  if (typeof data === 'string') {
    try {
      const parsed = JSON.parse(data) as { message?: string }
      return parsed.message || fallback
    } catch {
      return data || fallback
    }
  }
  if (data && typeof data === 'object' && 'message' in data) {
    return String((data as { message: string }).message)
  }
  return fallback
}
