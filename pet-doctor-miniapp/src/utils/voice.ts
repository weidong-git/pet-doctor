import { fetchSpeechStatus, recognizeSpeech, synthesizeSpeech } from '@/api/speech'

export interface VoiceRecognitionCallbacks {
  onStart?: () => void
  onRecordEnd?: () => void
  onPartial?: (text: string) => void
  onResult?: (text: string) => void
  onError?: (message: string) => void
}

const TTS_MAX_LENGTH = 800
const TTS_MAX_SEGMENTS = 4
const TTS_SEGMENT_DELAY_MS = 1500

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

let speechBackendAvailable: boolean | null = null

/** 是否当前平台支持语音能力（不依赖微信插件） */
export function isVoiceSupported(): boolean {
  // #ifdef MP-WEIXIN
  return true
  // #endif
  return false
}

/** 查询后端语音服务是否已配置（force=true 时重新请求） */
export async function checkSpeechAvailable(force = false): Promise<boolean> {
  if (!isVoiceSupported()) return false
  if (!force && speechBackendAvailable !== null) return speechBackendAvailable

  try {
    const status = await fetchSpeechStatus()
    speechBackendAvailable = status.available
    return status.available
  } catch {
    speechBackendAvailable = false
    return false
  }
}

export function showSpeechConfigHint(): void {
  uni.showModal({
    title: '语音服务未配置',
    content: '请在 pet-doctor-backend 的 application-local.yml 中设置 speech.api-key（OpenAI 兼容 Whisper/TTS 密钥），然后重启后端。',
    showCancel: false,
    confirmText: '知道了',
  })
}

function notifySpeechUnavailable(message?: string): void {
  speechBackendAvailable = false
  uni.showToast({
    title: message || '语音合成失败，请检查 speech.api-key 配置',
    icon: 'none',
    duration: 2500,
  })
}
/** 清理适合朗读的文本 */
function sanitizeSpeechText(text: string): string {
  return text
    .replace(/[⚠️🚨ⓘ#*_`>[\]()]/g, '')
    .replace(/\n+/g, '。')
    .replace(/\s+/g, '')
    .trim()
}

/** 按句号分段，避免单次合成过长 */
function splitSpeechSegments(text: string): string[] {
  const cleaned = sanitizeSpeechText(text)
  if (!cleaned) return []

  const segments: string[] = []
  let buffer = ''

  cleaned.split(/[。！？；.!?;]+/).forEach((part) => {
    const piece = part.trim()
    if (!piece) return

    if ((buffer + piece).length <= TTS_MAX_LENGTH) {
      buffer += piece
      return
    }

    if (buffer) segments.push(buffer)
    buffer = piece.length <= TTS_MAX_LENGTH ? piece : piece.slice(0, TTS_MAX_LENGTH)
  })

  if (buffer) segments.push(buffer)
  return segments.slice(0, TTS_MAX_SEGMENTS)
}

/**
 * 语音服务：RecorderManager 录音 + 后端识别/合成（无需微信插件）
 */
export class WechatVoiceService {
  private recorderManager: UniApp.RecorderManager | null = null
  private audioContext: UniApp.InnerAudioContext | null = null
  private callbacks: VoiceRecognitionCallbacks = {}
  private speechQueue: string[] = []
  private isSpeaking = false
  private initialized = false
  private isRecognizing = false
  private onSpeakEndCallback: (() => void) | null = null
  private isFirstSegmentInBatch = true

  onSpeakEnd(callback: () => void): void {
    this.onSpeakEndCallback = callback
  }

  initRecognition(callbacks: VoiceRecognitionCallbacks): void {
    this.callbacks = callbacks
    if (this.initialized) return

    // #ifdef MP-WEIXIN
    this.recorderManager = uni.getRecorderManager()

    this.recorderManager.onStart(() => {
      this.callbacks.onStart?.()
    })

    this.recorderManager.onStop((res) => {
      this.callbacks.onRecordEnd?.()
      void this.handleRecordedFile(res.tempFilePath)
    })

    this.recorderManager.onError((err) => {
      this.isRecognizing = false
      this.callbacks.onError?.(err.errMsg || '录音失败')
    })

    this.initialized = true
    // #endif
  }

  startRecognition(): void {
    if (!isVoiceSupported()) {
      uni.showToast({ title: '语音功能仅支持微信小程序', icon: 'none' })
      return
    }

    // #ifdef MP-WEIXIN
    if (!this.recorderManager) {
      this.callbacks.onError?.('语音服务未初始化')
      return
    }

    uni.authorize({
      scope: 'scope.record',
      success: () => this.startRecording(),
      fail: () => {
        uni.showModal({
          title: '需要麦克风权限',
          content: '请在设置中允许使用麦克风，以便语音描述宠物症状',
          confirmText: '去设置',
          success: (modalRes) => {
            if (modalRes.confirm) {
              uni.openSetting({})
            }
          },
        })
      },
    })
    // #endif
  }

  stopRecognition(): void {
    // #ifdef MP-WEIXIN
    this.recorderManager?.stop()
    // #endif
  }

  speak(text: string): void {
    if (!isVoiceSupported()) return

    void checkSpeechAvailable().then((available) => {
      if (!available) {
        notifySpeechUnavailable()
        this.onSpeakEndCallback?.()
        return
      }

      const segments = splitSpeechSegments(text)
      if (segments.length === 0) return

      this.stopSpeak()
      this.speechQueue = segments
      this.isFirstSegmentInBatch = true
      void this.playNextSegment()
    })
  }
  stopSpeak(): void {
    this.speechQueue = []
    this.isSpeaking = false
    if (this.audioContext) {
      this.audioContext.stop()
      this.audioContext.destroy()
      this.audioContext = null
    }
  }

  get speaking(): boolean {
    return this.isSpeaking
  }

  destroy(): void {
    this.stopSpeak()
    this.recorderManager = null
    this.initialized = false
  }

  private startRecording(): void {
    this.recorderManager?.start({
      duration: 60000,
      sampleRate: 16000,
      numberOfChannels: 1,
      encodeBitRate: 96000,
      format: 'mp3',
    })
  }

  private async handleRecordedFile(tempFilePath: string): Promise<void> {
    if (this.isRecognizing) return
    this.isRecognizing = true

    try {
      uni.showLoading({ title: '识别中...', mask: true })
      const text = await recognizeSpeech(tempFilePath)
      this.callbacks.onResult?.(text)
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err)
      this.callbacks.onError?.(message)
    } finally {
      this.isRecognizing = false
      uni.hideLoading()
    }
  }

  private async playNextSegment(): Promise<void> {
    const segment = this.speechQueue.shift()
    if (!segment) {
      this.isSpeaking = false
      this.onSpeakEndCallback?.()
      return
    }

    try {
      if (!this.isFirstSegmentInBatch) {
        await sleep(TTS_SEGMENT_DELAY_MS)
      }
      this.isFirstSegmentInBatch = false

      const audioUrl = await synthesizeSpeech(segment)
      this.audioContext = uni.createInnerAudioContext()
      this.audioContext.src = audioUrl
      this.isSpeaking = true

      this.audioContext.onEnded(() => {
        this.audioContext?.destroy()
        this.audioContext = null
        void this.playNextSegment()
      })

      this.audioContext.onError(() => {
        this.isSpeaking = false
        this.speechQueue = []
        this.onSpeakEndCallback?.()
      })

      this.audioContext.play()
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err)
      if (/503|未配置|语音服务/.test(message)) {
        notifySpeechUnavailable(message)
      } else if (/配额|insufficient_quota|quota/i.test(message)) {
        uni.showToast({
          title: 'OpenAI 语音配额已用尽，请充值或更换 Key',
          icon: 'none',
          duration: 3500,
        })
      } else if (/429|过于频繁|Too Many/i.test(message)) {
        uni.showToast({ title: '朗读请求过快，请稍后再试', icon: 'none', duration: 3000 })
      }
      this.isSpeaking = false
      this.speechQueue = []
      this.onSpeakEndCallback?.()
    }
  }
}