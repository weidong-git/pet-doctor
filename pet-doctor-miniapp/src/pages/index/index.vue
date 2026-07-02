<script setup lang="ts">
import { ref, computed, nextTick, watch, onMounted, onUnmounted } from 'vue'
import { sendMessage, cancelActiveChat } from '@/api/chat'
import { WechatVoiceService, isVoiceSupported, checkSpeechAvailable, showSpeechConfigHint } from '@/utils/voice'
import type { Pet, ChatMessage } from '@/types'

/** 宠物列表 */
const pets = ref<Pet[]>([
  { id: 1, name: '小贝', avatar: '/static/pets/xiaobei.png', breed: '金毛' },
  { id: 2, name: '富贵', avatar: '/static/pets/fugui.png', breed: '布偶猫' },
])

const currentPetId = ref(1)
const userId = ref('wx_user_001')
const inputText = ref('')
const isLoading = ref(false)
const scrollTop = ref(0)
const scrollCounter = ref(0)
const isRecording = ref(false)
const autoSpeak = ref(false)
const speechAvailable = ref(false)
const speakingMessageId = ref<string | null>(null)

const voiceService = new WechatVoiceService()
const voiceSupported = isVoiceSupported()
const chatScrollHeight = ref('400px')
const pageHeight = ref('667px')

const quickQuestions = ['如何量体温？', '查附近的医院', '换种口味试试']

const messages = ref<ChatMessage[]>([
  {
    id: 'welcome',
    role: 'assistant',
    content:
      '你好！我是 AI 宠物医生，可以帮你分析宠物的健康问题。\n\n请描述一下宝贝的症状，也可以上传患处照片，我会给出专业建议：\n\n1. 观察精神状态和食欲变化\n2. 记录体温、呕吐、腹泻等情况\n3. 如有外伤请拍照上传\n\nⓘ 以上建议仅供参考，不能替代线下兽医诊断。',
  },
])

const currentPet = computed(() => pets.value.find((p) => p.id === currentPetId.value))

onMounted(() => {
  initLayout()

  if (!voiceSupported) return

  void checkSpeechAvailable().then((available) => {
    speechAvailable.value = available
  })

  try {
    voiceService.onSpeakEnd(() => {
      speakingMessageId.value = null
    })

    voiceService.initRecognition({
    onStart: () => {
      isRecording.value = true
    },
    onRecordEnd: () => {
      isRecording.value = false
    },
    onPartial: (text) => {
      inputText.value = text
    },
    onResult: (text) => {
      isRecording.value = false
      inputText.value = text
      handleSend(text)
    },
    onError: (message) => {
      isRecording.value = false
      uni.showToast({ title: message, icon: 'none' })
    },
  })
  } catch (err) {
    console.error('语音服务初始化失败', err)
  }
})

onUnmounted(() => {
  cancelActiveChat()
  voiceService.destroy()
})

/** 使用语音前检查后端是否已配置 */
async function ensureSpeechReady(): Promise<boolean> {
  const available = await checkSpeechAvailable(true)
  speechAvailable.value = available
  if (!available) {
    showSpeechConfigHint()
  }
  return available
}

/** 切换自动朗读 */
async function toggleAutoSpeak() {
  if (!autoSpeak.value && !(await ensureSpeechReady())) return

  autoSpeak.value = !autoSpeak.value
  if (!autoSpeak.value) {
    voiceService.stopSpeak()
    speakingMessageId.value = null
  }
  uni.showToast({
    title: autoSpeak.value ? '已开启自动朗读' : '已关闭自动朗读',
    icon: 'none',
  })
}

/** 按住开始录音（同步启动，避免 touch 事件与 scroll 冲突） */
function handleVoiceTouchStart() {
  if (isLoading.value || isRecording.value) return
  if (!speechAvailable.value) {
    void ensureSpeechReady()
    return
  }

  voiceService.stopSpeak()
  speakingMessageId.value = null
  voiceService.startRecognition()
}

/** 松手 / 触摸被系统取消时结束录音（touchcancel 不可 preventDefault） */
function handleVoiceTouchEnd() {
  if (!isRecording.value) return
  voiceService.stopRecognition()
}

/** 朗读指定 AI 消息 */
async function speakMessage(msg: ChatMessage) {
  if (!msg.content || msg.isStreaming) return

  if (speakingMessageId.value === msg.id) {
    voiceService.stopSpeak()
    speakingMessageId.value = null
    return
  }

  if (!(await ensureSpeechReady())) return

  voiceService.stopSpeak()
  speakingMessageId.value = msg.id
  voiceService.speak(msg.content)
}

/** 初始化页面布局高度（小程序 tabBar 页 height:100% 常为 0，需用 windowHeight） */
function initLayout() {
  const sys = uni.getSystemInfoSync()
  const usableHeight = sys.windowHeight || 667
  pageHeight.value = `${usableHeight}px`

  const headerPx = uni.upx2px(180)
  const quickPx = uni.upx2px(88)
  const footerPx = uni.upx2px(128)
  const scrollPx = usableHeight - headerPx - quickPx - footerPx
  chatScrollHeight.value = `${Math.max(240, scrollPx)}px`
}

initLayout()

/** 切换当前宠物 */
function selectPet(petId: number) {
  currentPetId.value = petId
}

/** 滚动聊天区域到底部 */
function scrollToBottom() {
  nextTick(() => {
    scrollCounter.value++
    scrollTop.value = 99999 + scrollCounter.value
  })
}

watch(messages, scrollToBottom, { deep: true })

/** 生成唯一消息 ID */
function genId(): string {
  return `msg_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
}

/** 发送用户消息并触发 SSE 流式问诊 */
function handleSend(query?: string) {
  const text = (query ?? inputText.value).trim()
  if (!text || isLoading.value) return

  inputText.value = ''

  const userMsg: ChatMessage = {
    id: genId(),
    role: 'user',
    content: text,
  }
  messages.value.push(userMsg)

  const assistantMsg: ChatMessage = {
    id: genId(),
    role: 'assistant',
    content: '',
    isStreaming: true,
  }
  messages.value.push(assistantMsg)
  isLoading.value = true

  sendMessage(
    {
      userId: userId.value,
      petId: currentPetId.value,
      query: text,
    },
    {
      onChunk: (chunk) => {
        const target = messages.value.find((m) => m.id === assistantMsg.id)
        if (target) {
          target.content += chunk
        }
      },
      onDone: () => {
        const target = messages.value.find((m) => m.id === assistantMsg.id)
        if (target) {
          target.isStreaming = false
          if (!target.content) {
            target.content = '抱歉，暂时无法获取回复，请稍后重试。'
          } else if (autoSpeak.value && voiceSupported && speechAvailable.value) {
            speakingMessageId.value = target.id
            voiceService.speak(target.content)
          }
        }
        isLoading.value = false
      },
      onError: (errMsg) => {
        const target = messages.value.find((m) => m.id === assistantMsg.id)
        if (target) {
          target.content = `⚠️ ${errMsg}`
          target.isStreaming = false
        }
        isLoading.value = false
      },
    },
  )
}

/** 预约附近宠物医院 */
function bookHospital() {
  uni.showToast({ title: '正在查找附近医院...', icon: 'none' })
}

/** 添加新宠物 */
function addPet() {
  uni.showToast({ title: '添加宠物功能开发中', icon: 'none' })
}

/** 将 AI 回复按行拆分为列表项 */
function parseListItems(content: string): string[] {
  return content
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => /^\d+[.、]/.test(line))
}

/** 获取非列表的正文段落 */
function parsePlainText(content: string): string {
  return content
    .split('\n')
    .filter((line) => !/^\d+[.、]/.test(line.trim()))
    .join('\n')
    .trim()
}
</script>

<template>
  <view class="chat-page" :style="{ height: pageHeight }">
    <!-- 顶部宠物切换栏 -->
    <view class="chat-header shrink-0">
      <view class="px-5 pt-3 pb-3">
        <view class="flex items-center gap-4">
          <view
            v-for="pet in pets"
            :key="pet.id"
            class="flex flex-col items-center"
            @tap="selectPet(pet.id)"
          >
            <view class="relative">
              <view
                class="w-14 h-14 rounded-full overflow-hidden border-2 transition-all"
                :class="currentPetId === pet.id ? 'border-orange-400' : 'border-transparent opacity-70'"
              >
                <image
                  :src="pet.avatar"
                  class="w-full h-full"
                  mode="aspectFill"
                />
              </view>
              <view
                v-if="currentPetId === pet.id"
                class="absolute -bottom-0.5 -right-0.5 w-4 h-4 bg-red-500 rounded-full flex items-center justify-center border-2 border-cream"
              >
                <text class="text-white text-xs leading-none">✓</text>
              </view>
            </view>
            <text
              class="text-xs mt-1.5"
              :class="currentPetId === pet.id ? 'text-gray-800 font-medium' : 'text-gray-400'"
            >
              {{ pet.name }}
            </text>
          </view>

          <!-- 添加宠物 -->
          <view class="flex flex-col items-center" @tap="addPet">
            <view class="w-14 h-14 rounded-full border-2 border-dashed border-gray-300 flex items-center justify-center">
              <text class="text-gray-400 text-2xl leading-none">+</text>
            </view>
            <text class="text-xs mt-1.5 text-gray-400">添加</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 聊天气泡流 -->
    <scroll-view
      class="chat-scroll px-4"
      :scroll-y="!isRecording"
      :style="{ height: chatScrollHeight }"
      :scroll-top="scrollTop"
      scroll-with-animation
      :show-scrollbar="false"
    >
      <view class="pb-4">
        <view
          v-for="msg in messages"
          :key="msg.id"
          class="mb-4"
        >
          <!-- 用户消息：右侧淡黄色气泡 -->
          <view v-if="msg.role === 'user'" class="flex justify-end">
            <view class="max-w-[80%] bg-user-bubble rounded-2xl rounded-tr-sm px-4 py-3">
              <text class="text-gray-800 text-sm leading-relaxed">{{ msg.content }}</text>
              <image
                v-if="msg.imageUrl"
                :src="msg.imageUrl"
                class="w-40 h-40 rounded-xl mt-2"
                mode="aspectFill"
              />
            </view>
          </view>

          <!-- AI 医生回复：左侧白色卡片 -->
          <view v-else class="flex justify-start">
            <view class="max-w-[88%] bg-white rounded-2xl rounded-tl-sm px-4 py-4 shadow-sm">
              <!-- 朗读按钮 -->
              <view
                v-if="voiceSupported && !msg.isStreaming && msg.content && msg.id !== 'welcome'"
                class="flex justify-end mb-2"
              >
                <view
                  class="flex items-center gap-1 px-2 py-1 rounded-full bg-orange-50"
                  @tap="speakMessage(msg)"
                >
                  <text class="text-xs">{{ speakingMessageId === msg.id ? '🔇' : '🔊' }}</text>
                  <text class="text-orange-500 text-xs">
                    {{ speakingMessageId === msg.id ? '停止朗读' : '朗读' }}
                  </text>
                </view>
              </view>

              <!-- 列表项渲染 -->
              <view v-if="parseListItems(msg.content).length > 0" class="mb-3">
                <view
                  v-for="(item, idx) in parseListItems(msg.content)"
                  :key="idx"
                  class="flex gap-2 mb-2"
                >
                  <text class="text-orange-500 font-medium text-sm shrink-0">
                    {{ idx + 1 }}.
                  </text>
                  <text class="text-gray-700 text-sm leading-relaxed">
                    {{ item.replace(/^\d+[.、]\s*/, '') }}
                  </text>
                </view>
              </view>

              <!-- 普通文本 -->
              <text
                v-if="parsePlainText(msg.content)"
                class="text-gray-700 text-sm leading-relaxed whitespace-pre-wrap"
              >
                {{ parsePlainText(msg.content) }}
              </text>

              <!-- 流式打字光标 -->
              <text
                v-if="msg.isStreaming"
                class="inline-block w-0.5 h-4 bg-orange-400 ml-0.5 animate-pulse"
              ></text>

              <!-- 免责声明 -->
              <view v-if="!msg.isStreaming && msg.content" class="flex items-start gap-1 mt-4">
                <text class="text-gray-400 text-xs">ⓘ</text>
                <text class="text-gray-400 text-xs leading-relaxed">
                  以上建议仅供参考，不能替代专业兽医诊断，如有紧急情况请立即就医。
                </text>
              </view>

              <!-- 预约按钮 -->
              <view
                v-if="!msg.isStreaming && msg.content && msg.id !== 'welcome'"
                class="mt-4 flex justify-center"
              >
                <button
                  class="bg-orange-500 text-white text-sm font-medium px-6 py-2.5 rounded-full shadow-sm border-none"
                  @tap="bookHospital"
                >
                  🚨 预约附近宠物医院
                </button>
              </view>
            </view>
          </view>
        </view>
      </view>
    </scroll-view>

    <!-- 底部快捷提问词 -->
    <scroll-view class="quick-scroll shrink-0 px-4 py-2" scroll-x :show-scrollbar="false">
      <view class="flex gap-2 whitespace-nowrap">
        <view
          v-for="q in quickQuestions"
          :key="q"
          class="inline-flex px-4 py-2 bg-white rounded-full border border-gray-200 shadow-sm"
          @tap="handleSend(q)"
        >
          <text class="text-gray-600 text-sm">{{ q }}</text>
        </view>
      </view>
    </scroll-view>

    <!-- 输入框区域 -->
    <view class="chat-footer shrink-0 px-4 pb-safe-bottom pt-2 bg-cream border-t border-gray-100">
      <!-- 录音提示条 -->
      <view
        v-if="isRecording"
        class="mb-2 py-2 px-4 bg-orange-50 rounded-full flex items-center justify-center gap-2"
      >
        <view class="w-2 h-2 bg-red-500 rounded-full animate-pulse"></view>
        <text class="text-orange-600 text-sm">正在聆听，松开结束...</text>
      </view>

      <view class="flex items-center gap-2 bg-white rounded-full px-3 py-2 shadow-sm">
        <!-- 语音按钮：按住说话（勿对 touchcancel 使用 .prevent） -->
        <view
          v-if="voiceSupported"
          class="voice-mic-btn w-9 h-9 rounded-full flex items-center justify-center shrink-0"
          :class="isRecording ? 'bg-red-500' : isLoading ? 'bg-gray-200' : 'bg-orange-100'"
          hover-class="none"
          @touchstart.stop="handleVoiceTouchStart"
          @touchend.stop="handleVoiceTouchEnd"
          @touchcancel="handleVoiceTouchEnd"
        >
          <text class="text-base">{{ isRecording ? '⏺' : '🎤' }}</text>
        </view>

        <input
          v-model="inputText"
          class="flex-1 text-sm text-gray-800 min-w-0"
          placeholder="描述症状，或按住麦克风..."
          placeholder-class="text-gray-400"
          confirm-type="send"
          :disabled="isLoading || isRecording"
          @confirm="handleSend()"
        />

        <!-- 自动朗读开关 -->
        <view
          v-if="voiceSupported"
          class="w-8 h-8 rounded-full flex items-center justify-center shrink-0"
          :class="autoSpeak ? 'bg-orange-50' : 'bg-gray-100'"
          @tap="toggleAutoSpeak"
        >
          <text class="text-sm">{{ autoSpeak ? '🔊' : '🔇' }}</text>
        </view>

        <view
          class="w-8 h-8 rounded-full flex items-center justify-center shrink-0"
          :class="isLoading || isRecording ? 'bg-gray-200' : 'bg-orange-500'"
          @tap="handleSend()"
        >
          <text class="text-white text-sm">↑</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style>
page {
  background-color: #fcfbf9;
}
</style>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  width: 100%;
  box-sizing: border-box;
  background-color: #fcfbf9;
  overflow: hidden;
}

.chat-scroll {
  width: 100%;
  box-sizing: border-box;
}

.quick-scroll {
  white-space: nowrap;
}

.shrink-0 {
  flex-shrink: 0;
}

.pb-safe-bottom {
  padding-bottom: calc(env(safe-area-inset-bottom) + 8px);
}

.voice-mic-btn {
  touch-action: none;
}
</style>
