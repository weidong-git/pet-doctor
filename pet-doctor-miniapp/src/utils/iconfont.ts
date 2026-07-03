import { ref } from 'vue'
import { ICONFONT_FAMILY, ICONFONT_FONT_URL } from '@/config/iconfont'

/** 字体是否已加载（用于底栏图标渲染） */
export const iconfontReady = ref(false)

let loadingPromise: Promise<void> | null = null

/**
 * 加载 iconfont（微信小程序必须用 loadFontFace + 网络 ttf）
 */
export function loadIconfont(): Promise<void> {
  if (iconfontReady.value) {
    return Promise.resolve()
  }
  if (loadingPromise) {
    return loadingPromise
  }

  loadingPromise = new Promise((resolve) => {
    const source = `url("${ICONFONT_FONT_URL}")`

    // #ifdef MP-WEIXIN
    uni.loadFontFace({
      global: true,
      family: ICONFONT_FAMILY,
      source,
      success: () => {
        iconfontReady.value = true
        console.log('[iconfont] 加载成功')
        resolve()
      },
      fail: (err) => {
        console.warn('[iconfont] 加载失败，请检查合法域名或下载字体到本地', err)
        iconfontReady.value = true
        resolve()
      },
    })
    // #endif

    // #ifdef H5
    iconfontReady.value = true
    resolve()
    // #endif

    // #ifndef MP-WEIXIN || H5
    iconfontReady.value = true
    resolve()
    // #endif
  })

  return loadingPromise
}
