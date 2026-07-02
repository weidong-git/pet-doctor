import fs from 'node:fs'
import path from 'node:path'
import type { Plugin } from 'vite'

/**
 * 构建后写入/修补微信小程序工程配置：
 * - 关闭 compileHotReLoad，减少 closeSocket 1006 噪声
 * - 从 VITE_WX_APPID 注入真实 AppID，避免 touristappid 游客模式
 */
export function mpWeixinPrivateConfigPlugin(): Plugin {
  return {
    name: 'mp-weixin-private-config',
    closeBundle() {
      if (process.env.UNI_PLATFORM !== 'mp-weixin') return

      const subDir = process.env.NODE_ENV === 'production' ? 'build' : 'dev'
      const outputDir = path.resolve(__dirname, `dist/${subDir}/mp-weixin`)
      if (!fs.existsSync(outputDir)) return

      const wxAppId = process.env.VITE_WX_APPID?.trim()
      const projectConfigPath = path.join(outputDir, 'project.config.json')
      const projectConfig = fs.existsSync(projectConfigPath)
        ? (JSON.parse(fs.readFileSync(projectConfigPath, 'utf-8')) as Record<string, unknown>)
        : null
      const manifestAppId = typeof projectConfig?.appid === 'string' ? projectConfig.appid : ''
      const effectiveAppId = wxAppId || (manifestAppId !== 'touristappid' ? manifestAppId : '')

      if (!effectiveAppId) {
        console.warn(
          '\n[mp-weixin] 未设置 AppID，将使用 touristappid 游客模式。\n' +
            '  请在 src/manifest.json 的 mp-weixin.appid 或 .env.*.local 中配置 VITE_WX_APPID\n' +
            '  然后重新编译，关闭旧项目并重新导入 dist 目录。\n',
        )
      } else if (wxAppId && projectConfig) {
        projectConfig.appid = wxAppId
        fs.writeFileSync(projectConfigPath, `${JSON.stringify(projectConfig, null, 2)}\n`, 'utf-8')
      }

      const privateConfig = {
        description: '关闭编译热重载，减少开发者工具 WebSocket 报错',
        setting: {
          compileHotReLoad: false,
        },
      }

      fs.writeFileSync(
        path.join(outputDir, 'project.private.config.json'),
        `${JSON.stringify(privateConfig, null, 2)}\n`,
        'utf-8',
      )
    },
  }
}
