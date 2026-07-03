import type { FileUploadResult } from '@/types'

const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080'

/**
 * 上传图片到后端，返回可供 AI 多模态检索的 URL
 */
export function uploadImage(filePath: string): Promise<FileUploadResult> {
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${BASE_URL}/api/v1/files/upload`,
      filePath,
      name: 'file',
      timeout: 60000,
      success: (res) => {
        if (res.statusCode !== 200) {
          reject(new Error(parseUploadError(res.data, `上传失败: ${res.statusCode}`)))
          return
        }

        try {
          const data = JSON.parse(res.data) as FileUploadResult
          if (!data?.url) {
            reject(new Error('服务器未返回图片地址'))
            return
          }
          resolve(data)
        } catch {
          reject(new Error('上传响应解析失败'))
        }
      },
      fail: (err) => {
        reject(new Error(err.errMsg || '图片上传失败'))
      },
    })
  })
}

function parseUploadError(data: string, fallback: string): string {
  try {
    const parsed = JSON.parse(data) as { message?: string }
    return parsed.message || fallback
  } catch {
    return fallback
  }
}
