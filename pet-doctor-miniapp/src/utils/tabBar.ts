import { onShow } from '@dcloudio/uni-app'

/**
 * 隐藏微信原生 TabBar，改用 iconfont 自定义底栏组件
 */
export function useAppTabBar(): void {
  onShow(() => {
    uni.hideTabBar({ animation: false })
  })
}
