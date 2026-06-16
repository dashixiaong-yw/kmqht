# 计划：快麦API Key独立存储方案

## 摘要

将快麦ERP开放平台的API凭证（APP_KEY、APP_SECRET、SESSION）从后端代码和环境变量中独立出来，存储到单独的配置文件中，便于后续更换密钥时只需替换文件，无需修改代码或重新构建Docker镜像。

## 现状分析

### 当前密钥存储方式

| 密钥 | 存储位置 | 问题 |
|------|---------|------|
| `API_KEY`（后端认证） | `.env` 环境变量 | 正常，无需修改 |
| `KUAIMAI_APP_KEY` | `.env` 环境变量 | 与后端认证密钥混在一起 |
| `KUAIMAI_APP_SECRET` | `.env` 环境变量 | 同上 |
| `KUAIMAI_SESSION` | `.env` 环境变量 | 同上，且SESSION会过期需频繁更换 |

### 当前问题

1. **快麦凭证与后端配置混在一起**：`.env`中既有后端服务配置（API_KEY、SERVER_PORT），又有快麦ERP凭证，更换快麦凭证时需要编辑整个`.env`文件
2. **SESSION过期需频繁更换**：accessToken有效期30天，过期前3-5天需预警并更换，当前需要手动编辑`.env`重启容器
3. **无`.env.example`文件**：项目根目录和backend目录都没有`.env.example`模板文件，新部署时不知道需要哪些环境变量
4. **App端API_KEY硬编码风险**：App端OkHttp拦截器中的X-API-Key需要从DataStore读取，不能硬编码

## 修改方案

### 1. 后端：快麦凭证独立为 `kuaimai.json` 配置文件

将快麦ERP凭证从`.env`中分离，存储到独立的JSON文件中：

**文件路径**：`/data/kuaimai.json`（Docker volume挂载目录内）

```json
{
  "app_key": "your-app-key",
  "app_secret": "your-app-secret",
  "session": "your-access-token",
  "updated_at": "2026-06-15T10:00:00+08:00"
}
```

**优势**：
- 更换凭证只需替换JSON文件，无需编辑`.env`或重启容器
- 后端启动时读取，运行时监听文件变化自动热更新（无需重启）
- JSON格式便于后续扩展（如多账号支持）
- `updated_at`字段记录最后更新时间，便于SESSION过期预警

### 2. 后端：`.env`精简为仅服务配置

```bash
# .env — 仅后端服务配置

# 后端API认证密钥（PDA App请求时需携带X-API-Key Header）
API_KEY=your-api-key-here

# 服务器配置
SERVER_PORT=8900
```

### 3. 后端：添加凭证热更新+SESSION过期预警

- 后端启动时读取`kuaimai.json`
- 每24小时检查SESSION过期时间（距过期3-5天时日志预警）
- 检测到`kuaimai.json`文件修改时自动重新加载凭证（`watchfiles`库或定时轮询）

### 4. 创建 `.env.example` 模板文件

在`backend/`目录创建`.env.example`，供新部署参考。

### 5. 更新计划文档

将环境变量配置章节中的快麦凭证部分更新为`kuaimai.json`方案。

## 涉及文件

| 文件 | 操作 | 说明 |
|------|------|------|
| `.trae/documents/kuaimai-pda-app-plan.md` | 修改 | 更新环境变量配置章节，快麦凭证改为kuaimai.json |
| `backend/.env.example` | 新建 | 后端环境变量模板（仅API_KEY+SERVER_PORT） |
| `backend/kuaimai.example.json` | 新建 | 快麦凭证模板文件 |

## 不涉及的文件

- `app/`目录：App端不直接使用快麦API凭证（通过后端代理），无需修改
- `prototype/index.html`：UI原型无需修改
- `.trae/rules/README.md`：项目规则无需修改

## 验证步骤

1. 确认`.env.example`中不再包含快麦凭证
2. 确认`kuaimai.example.json`模板格式正确
3. 确认计划文档中环境变量配置章节已更新
4. 确认后端代码示例中读取`kuaimai.json`的逻辑已补充
5. 确认SESSION过期预警机制已写入文档
