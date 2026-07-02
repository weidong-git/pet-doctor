# AI 宠物医生

基于大模型的 AI 宠物医疗问诊系统，包含 Spring Boot WebFlux 后端与 Uni-app 微信小程序前端。

> 说明：需求文档要求 Vite 6，但当前 `@dcloudio/vite-plugin-uni` 仅兼容 Vite 5.x，项目使用 Vite 5.2.8。待官方升级后可平滑迁移。

## 项目结构

```
ai-all/
├── demand/                  # 需求文档
├── pet-doctor-backend/      # Java 后端（Spring Boot 3 + WebFlux）
└── pet-doctor-miniapp/      # 微信小程序前端（Uni-app + Vue3 + TS + Tailwind）
```

## 后端启动

### 环境要求

- JDK 17+
- Maven 3.8+

### 配置 Dify

编辑 `pet-doctor-backend/src/main/resources/application.yml`，或通过环境变量注入：

```bash
set DIFY_API_KEY=app-your-dify-api-key
```

### 运行

```bash
cd pet-doctor-backend
mvn spring-boot:run
```

服务默认监听 `http://localhost:8080`

### 核心接口

**POST** `/api/v1/chat/completions`

请求体：

```json
{
  "userId": "wx_user_001",
  "petId": 1,
  "query": "我家狗狗最近食欲不好",
  "imageUrl": "https://example.com/pet-photo.jpg"
}
```

响应：`text/event-stream` SSE 流式数据

| 事件 | 说明 |
|------|------|
| `message` | AI 回复文本片段 |
| `error` | 错误信息 |
| `done` | 流结束标记 `[DONE]` |

## 前端启动

### 环境要求

- Node.js 18+
- 微信开发者工具

### 安装依赖

```bash
cd pet-doctor-miniapp
npm install
```

### H5 开发调试

```bash
npm run dev:h5
```

Vite 已配置 `/api` 代理到 `localhost:8080`。

### 微信小程序

```bash
npm run dev:mp-weixin
```

用微信开发者工具打开 `dist/dev/mp-weixin` 目录。

**真机调试注意：** 修改 `.env.development` 中的 `VITE_API_BASE_URL` 为电脑局域网 IP，例如：

```
VITE_API_BASE_URL=http://192.168.1.100:8080
```

并在微信公众平台配置 request 合法域名。

## 功能对照

| 需求 | 实现位置 |
|------|----------|
| SSE 流式问诊接口 | `ChatController` + `ChatService` |
| Dify API 对接 | `ChatService.buildDifyRequest()` |
| 宠物档案 Mock | `ChatService.getPetProfile()` |
| 多模态图片上传 | `imageUrl` 字段 → Dify `files` |
| 宠物切换 | `pages/index/index.vue` |
| wx.request 分块流式 | `src/api/chat.ts` `enableChunked: true` |
| 打字机效果 | 实时追加 `assistantMsg.content` |
| TabBar 三标签 | `pages.json` |
| Apple 极简 UI | Tailwind 奶白风格 |

## 静态资源

TabBar 图标和宠物头像位于 `pet-doctor-miniapp/src/static/`，可替换为设计稿中的实际图片。
