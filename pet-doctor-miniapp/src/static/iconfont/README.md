# 阿里云 iconfont 配置说明

项目图标来自 iconfont.cn 项目 **「AI宠物医生」**：

| 名称 | Unicode | 用途 |
|------|---------|------|
| 宠物-宠物医生 | `\e600` | AI医生 Tab |
| 导入周食谱 | `\ue70a` | 定制食谱 Tab |
| 我的档案 | `\e617` | 我的档案 Tab |

## 方式一：下载至本地（推荐）

1. 打开 [iconfont.cn](https://www.iconfont.cn) → 进入项目「AI宠物医生」
2. 点击 **下载至本地**，解压 zip
3. 在项目根目录执行：

```bash
npm run setup:iconfont -- "解压目录路径"
```

脚本会将 `iconfont.ttf` 复制到 `src/static/iconfont/`。

## 方式二：在线链接

1. 在 iconfont 项目中点击 **查看在线链接**
2. 复制 `@font-face` 里 `url('...ttf...')` 的完整地址
3. 写入 `.env.development.local`：

```
VITE_ICONFONT_FONT_URL=https://at.alicdn.com/t/c/font_xxxx.ttf
```

4. 重新编译小程序

## 使用

```vue
<text class="iconfont">{{ '\ue600' }}</text>
```

或从 `@/config/iconfont` 导入常量 `ICON_DOCTOR` 等。
