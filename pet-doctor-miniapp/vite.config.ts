import path from 'node:path'
import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'
import tailwindcssPostcss from 'tailwindcss'
import autoprefixer from 'autoprefixer'
import { UnifiedViteWeappTailwindcssPlugin as weappTailwindcss } from 'weapp-tailwindcss/vite'
import { mpWeixinPrivateConfigPlugin } from './vite-plugin-mp-weixin-private-config'

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
      postcssOptions: {
        plugins: [tailwindcssPostcss(), autoprefixer()],
      },
    }),
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
