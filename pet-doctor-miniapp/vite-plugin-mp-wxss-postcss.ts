import fs from 'node:fs'
import path from 'node:path'
import postcss from 'postcss'
import tailwindcss from 'tailwindcss'
import autoprefixer from 'autoprefixer'
import {
  postcssWeappTailwindcssPostPlugin,
  postcssWeappTailwindcssPrePlugin,
} from 'weapp-tailwindcss/postcss'
import type { Plugin } from 'vite'

/**
 * uni-app 构建产物为 app.wxss，但 generateBundle 阶段常为 app.css，
 * 导致 weapp-tailwindcss 跳过处理、@tailwind 原样写入引发 WXSS 报错。
 * 在 closeBundle 对最终 wxss 再跑一遍 PostCSS。
 */
export function mpWxssPostcssPlugin(): Plugin {
  return {
    name: 'mp-wxss-postcss',
    enforce: 'post',
    async closeBundle() {
      if (process.env.UNI_PLATFORM !== 'mp-weixin') return

      const subDir = process.env.NODE_ENV === 'production' ? 'build' : 'dev'
      const outputDir = path.resolve(__dirname, `dist/${subDir}/mp-weixin`)
      if (!fs.existsSync(outputDir)) return

      const wxssFiles = collectWxssFiles(outputDir)
      await Promise.all(
        wxssFiles.map(async (filePath) => {
          const raw = fs.readFileSync(filePath, 'utf-8')
          if (!raw.includes('@tailwind')) return

          const result = await postcss([
            tailwindcss,
            autoprefixer,
            postcssWeappTailwindcssPrePlugin(),
            postcssWeappTailwindcssPostPlugin({ rem2rpx: true }),
          ]).process(raw, { from: undefined })

          fs.writeFileSync(filePath, result.css, 'utf-8')
        }),
      )
    },
  }
}

function collectWxssFiles(dir: string): string[] {
  const result: string[] = []
  const walk = (current: string) => {
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      const fullPath = path.join(current, entry.name)
      if (entry.isDirectory()) {
        walk(fullPath)
      } else if (entry.name.endsWith('.wxss')) {
        result.push(fullPath)
      }
    }
  }
  walk(dir)
  return result
}
