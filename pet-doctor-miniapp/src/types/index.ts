/** 宠物信息 */
export interface Pet {
  id: number
  name: string
  avatar: string
  breed: string
}

/** 聊天消息角色 */
export type MessageRole = 'user' | 'assistant'

/** 聊天消息 */
export interface ChatMessage {
  id: string
  role: MessageRole
  content: string
  imageUrl?: string
  isStreaming?: boolean
}

/** 问诊请求参数 */
export interface ChatCompletionParams {
  userId: string
  petId: number
  query: string
  imageUrl?: string
}

/** SSE 流式回调 */
export interface StreamCallbacks {
  onChunk: (text: string) => void
  onDone: () => void
  onError: (message: string) => void
}
