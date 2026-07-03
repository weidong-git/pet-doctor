import path from 'node:path'
import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'
import tailwindcssPostcss from 'tailwindcss'
import autoprefixer from 'autoprefixer'
import { UnifiedViteWeappTailwindcssPlugin as weappTailwindcss } from 'weapp-tailwindcss/vite'
import { mpWeixinPrivateConfigPlugin } from './vite-plugin-mp-weixin-private-config'
import { mpWxssPostcssPlugin } from './vite-plugin-mp-wxss-postcss'

export default defineConfig({
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  plugins: [
    uni(),
    weappTailwindcss({
      rem2rpx: true,
      appType: 'uni-app-vite',
      // 构建中间产物为 .css，默认 matcher 只匹配 .wxss 会漏处理 app 样式
      cssMatcher: (file) => /\.(?:css|wx|ac|jx|tt|q|c|ty)ss$/.test(file),
      postcssOptions: {
        plugins: [tailwindcssPostcss(), autoprefixer()],
      },
    }),
    mpWxssPostcssPlugin(),
    mpWeixinPrivateConfigPlugin(),
  ],
  server: {
    port: 5173,
    hmr: false,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
