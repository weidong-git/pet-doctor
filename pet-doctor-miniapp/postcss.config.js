import tailwindcss from 'tailwindcss'
import autoprefixer from 'autoprefixer'
import {
  postcssWeappTailwindcssPostPlugin,
  postcssWeappTailwindcssPrePlugin,
} from 'weapp-tailwindcss/postcss'

export default {
  plugins: [
    tailwindcss,
    autoprefixer,
    postcssWeappTailwindcssPrePlugin(),
    postcssWeappTailwindcssPostPlugin({ rem2rpx: true }),
  ],
}
