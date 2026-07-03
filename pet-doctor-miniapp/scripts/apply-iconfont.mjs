import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')
const targetDir = path.join(root, 'src/static/iconfont')

const sourceDir = process.argv[2]
if (!sourceDir) {
  console.error('用法: npm run setup:iconfont -- <iconfont解压目录>')
  console.error('示例: npm run setup:iconfont -- C:\\Users\\xxx\\Downloads\\font_xxx')
  process.exit(1)
}

const absSource = path.resolve(sourceDir)
if (!fs.existsSync(absSource)) {
  console.error('目录不存在:', absSource)
  process.exit(1)
}

const ttfCandidates = ['iconfont.ttf', 'font.ttf']
let ttfPath = ''
for (const name of ttfCandidates) {
  const p = path.join(absSource, name)
  if (fs.existsSync(p)) {
    ttfPath = p
    break
  }
}

if (!ttfPath) {
  console.error('未找到 iconfont.ttf，请确认已解压 iconfont 下载包')
  process.exit(1)
}

fs.mkdirSync(targetDir, { recursive: true })
fs.copyFileSync(ttfPath, path.join(targetDir, 'iconfont.ttf'))

const cssPath = path.join(absSource, 'iconfont.css')
if (fs.existsSync(cssPath)) {
  console.log('提示: 已使用项目内 iconfont.css 的 Unicode 映射，无需覆盖')
}

console.log('✓ iconfont.ttf 已复制到 src/static/iconfont/')
console.log('  请重新运行 npm run dev:mp-weixin')
