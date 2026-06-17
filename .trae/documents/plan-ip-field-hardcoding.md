# 计划：三系统间通信IP字段硬编码问题 + 扫码配置方案

## 摘要

三个系统（App端、后端服务、快麦ERP API）中，只有"App→后端服务"的IP存在硬编码问题。采用**扫码配置**方案：后端提供配置页面显示二维码，PDA扫码自动填入服务器地址，同时移除硬编码的模拟器IP默认值。

## 现状分析

### 三系统通信链路

```
App端 ──(1)──→ 后端服务(NAS Docker) ──(2)──→ 快麦ERP API
  │                                            ↑
  └────────────(3) 快麦ERP API────────────────┘
```

| 链路 | 当前配置 | 是否有问题 |
|------|---------|-----------|
| (1) App→后端 | `DEFAULT_SERVER_URL = "http://10.0.2.2:8000"` | **有** — 模拟器专用IP，真机无法使用 |
| (2) 后端→快麦 | `KUAIMAI_API_BASE = "https://gw.superboss.cc/router"` | **无** — 公网域名，环境变量读取 |
| (3) App→快麦 | `KUAIMAI_API_URL = "https://gw.superboss.cc/router"` | **无** — 公网域名，固定地址 |

### 已有的动态配置机制

1. **GuideScreen**（首次引导）：手动输入服务器地址
2. **SettingsScreen**（设置页面）：可随时修改服务器地址和API Key
3. **NetworkModule**：从SharedPreferences读取`KEY_SERVER_URL`

### 核心问题

1. `DEFAULT_SERVER_URL = "http://10.0.2.2:8000"` 是模拟器IP，真机部署无意义
2. 真机首次使用需手动输入IP，操作不便且易出错
3. 用户希望**扫码配置**：后端页面显示二维码，PDA扫码自动填入

## 方案设计：扫码配置 + 移除硬编码默认值

### 整体流程

```
部署后操作流程：
1. NAS启动后端服务（docker-compose up -d）
2. 电脑浏览器访问 http://NAS_IP:8900/setup 页面
3. 页面自动显示包含服务器地址的二维码（内容：http://NAS_IP:8900）
4. PDA首次启动App → GuideScreen → 点击"扫码配置"
5. PDA扫描电脑屏幕上的二维码 → 自动填入服务器地址
6. 保存并继续后续配置
```

### 后端改动

#### 1. 新增 `/setup` 页面（system.py）

- 新增 `GET /setup` 端点，返回HTML页面
- 页面内容：显示当前服务器地址 + 对应的二维码
- 二维码内容格式：`kuaimai://setup?server=http://NAS_IP:8900&apikey=xxx`
- 后端自动检测自身监听地址（从环境变量 `SERVER_URL` 读取，或使用请求的host）
- 使用Python `qrcode` 库生成二维码图片（base64内嵌到HTML中，无需额外静态文件）
- 页面简洁：标题 + 二维码 + 服务器地址文本 + API Key（可选显示）

#### 2. config.py 新增 SERVER_URL 配置

- 新增 `SERVER_URL: str = os.getenv("SERVER_URL", "")` 环境变量
- 用于后端知道自己对外暴露的地址（NAS_IP:8900）
- 如果未配置，`/setup` 页面提示"请配置SERVER_URL环境变量"

#### 3. .env.docker.example 更新

- 添加 `SERVER_URL=http://YOUR_NAS_IP:8900` 示例

### App端改动

#### 4. AppConstants.kt

- `DEFAULT_SERVER_URL` 从 `"http://10.0.2.2:8000"` 改为 `""`（空字符串）
- 更新注释：说明真机部署需通过扫码或手动配置

#### 5. GuideScreen.kt 改造

- 服务器地址输入区域增加"扫码配置"按钮
- 点击按钮打开相机扫码（复用现有CameraScanScreen）
- 扫码结果解析：
  - 格式1：`kuaimai://setup?server=xxx&apikey=xxx` → 解析出server和apikey
  - 格式2：纯URL（`http://...`） → 直接作为server地址
- 解析成功后自动填入服务器地址输入框
- 保留手动输入方式作为备选

#### 6. SettingsScreen.kt 改造

- 服务器地址输入区域增加"扫码配置"按钮
- 逻辑同GuideScreen

#### 7. NetworkModule.kt / SettingsViewModel.kt / ProductViewModel.kt / ImageUploadService.kt / App.kt

- 所有读取 `DEFAULT_SERVER_URL` 的地方，空值处理保持一致
- 空值时不应fallback到模拟器IP

## 具体修改清单

### 后端（3个文件）

| 文件 | 修改内容 |
|------|---------|
| `backend/app/config.py` | 新增 `SERVER_URL` 环境变量 |
| `backend/app/routers/system.py` | 新增 `GET /setup` 端点，返回含二维码的HTML页面 |
| `backend/.env.docker.example` | 添加 `SERVER_URL` 示例 |

### App端（5个文件）

| 文件 | 修改内容 |
|------|---------|
| `app/.../util/AppConstants.kt` | `DEFAULT_SERVER_URL` 改为空字符串 |
| `app/.../ui/guide/GuideScreen.kt` | 增加"扫码配置"按钮+扫码解析逻辑 |
| `app/.../ui/settings/SettingsScreen.kt` | 增加"扫码配置"按钮+扫码解析逻辑 |
| `app/.../ui/settings/SettingsViewModel.kt` | 空值处理，不再fallback模拟器IP |
| `app/.../di/NetworkModule.kt` | 空值处理一致性 |

### 新增依赖

| 位置 | 依赖 | 说明 |
|------|------|------|
| 后端 requirements.txt | `qrcode[pil]` | 生成二维码图片 |
| App端 | 无新增 | 复用现有ML Kit扫码和CameraScanScreen |

## 二维码协议设计

```
格式：kuaimai://setup?server=<URL>&apikey=<KEY>

示例：
kuaimai://setup?server=http://192.168.1.100:8900&apikey=abc123

参数说明：
- server: 必填，后端服务器完整地址（含协议、IP、端口）
- apikey: 可选，后端API Key（方便一次性配置完成）

兼容格式：
- 纯URL字符串（http://192.168.1.100:8900）也识别为服务器地址
```

## 部署后操作流程

1. NAS上 `docker-compose up -d` 启动后端
2. 在 `.env` 中配置 `SERVER_URL=http://NAS实际IP:8900`
3. 电脑浏览器访问 `http://NAS_IP:8900/setup`
4. 页面显示二维码和服务器地址
5. PDA打开App → 引导页 → 扫码配置 → 扫描二维码 → 自动填入
6. 配置完成，开始使用

## 验证步骤

1. `./gradlew lint` 通过
2. `./gradlew assembleDebug` 构建成功
3. 后端 `/setup` 页面正常显示二维码
4. PDA扫码后自动填入服务器地址
5. 手动输入方式仍可用
6. App首次启动无硬编码IP，必须配置后才能使用
