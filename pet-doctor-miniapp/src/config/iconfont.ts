/**
 * 阿里云 iconfont 项目「AI宠物医生」
 * Project id: 5204310
 */
export const ICONFONT_FAMILY = 'iconfont'

/** 在线字体地址（微信小程序请用 ttf + https） */
export const ICONFONT_FONT_URL =
  import.meta.env.VITE_ICONFONT_FONT_URL?.trim() ||
  'https://at.alicdn.com/t/c/font_5204310_xddrpnya1pe.ttf?t=1783062521416'

/** TabBar：宠物医生 */
export const ICON_DOCTOR = '\ue600'

/** TabBar：导入周食谱 */
export const ICON_RECIPE = '\ue70a'

/** TabBar：我的档案 */
export const ICON_PROFILE = '\ue617'

export const TAB_BAR_ITEMS = [
  {
    pagePath: '/pages/index/index',
    text: 'AI医生',
    icon: ICON_DOCTOR,
  },
  {
    pagePath: '/pages/recipe/index',
    text: '定制食谱',
    icon: ICON_RECIPE,
  },
  {
    pagePath: '/pages/profile/index',
    text: '我的档案',
    icon: ICON_PROFILE,
  },
] as const
