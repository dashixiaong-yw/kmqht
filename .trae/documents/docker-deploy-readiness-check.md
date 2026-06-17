# Docker 部署就绪检查报告

## 目录对比：backend/ ↔ docker-deploy/

| 文件/目录 | backend | docker-deploy | 状态 |
|-----------|---------|---------------|------|
| `app/` (完整目录树) | ✅ | ✅ | 相同 |
| `main.py` | ✅ | ✅ | 相同 |
| `Dockerfile` | ✅ | ✅ | 相同 |
| `requirements.txt` | ✅ | ✅ | 相同 |
| `.dockerignore` | ✅ | ✅ | 相同 |
| `.env.docker.example` | ✅ | ✅ | 相同 |
| `kuaimai.example.json` | ✅ | ✅ | 相同 |
| `docker-compose.yml` | ✅ | ✅ | 相同 |
| `docker-compose.yaml` | ❌ | ✅ | 仅 docker-deploy 有（NAS兼容副本） |

**结论**: ✅ 文件结构完全一致，无缺失、无多余文件

---

## 部署准备检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| Dockerfile 有效性 | ✅ | `python:3.12-alpine` + tzdata + pip install + `uvicorn main:app` |
| docker-compose.yml | ✅ | 端口8900、健康检查、512M内存限制、env_file、volume映射 |
| docker-compose.yaml | ✅ | 与.yml内容一致（绿联NAS兼容） |
| .dockerignore | ✅ | 排除了 `__pycache__` / `.env` / `kuaimai.json` / `data/` |
| 时区设置 | ✅ | `Asia/Shanghai`，北京时间 |
| 健康检查 | ✅ | `curl -f http://localhost:8000/health` |
| BuildKit 缓存 | ✅ | `--mount=type=cache,target=/root/.cache/pip` |
| 依赖完整 | ✅ | fastapi / uvicorn / Pillow / httpx / 等共10个 |

---

## 部署前需要准备的 3 件事

### 1️⃣ 创建 `.env` 配置文件

将 `.env.docker.example` 复制为 `.env`，填入实际值：

```
API_KEY=这里填写随机生成的密钥
SERVER_URL=http://你的NAS_IP:8900
```

> **⚠️ 当前示例文件中的 API_KEY 为 `zxf199333`**，部署前请务必修改为随机密钥（建议用 `uuidgen` 生成）。

### 2️⃣ 创建 `kuaimai.json` 凭证文件

将 `kuaimai.example.json` 复制为 `kuaimai.json`，填入快麦 appKey/appSecret/session：

```json
{
  "app_key": "你的快麦APP_KEY",
  "app_secret": "你的快麦APP_SECRET",
  "session": "你的快麦SESSION",
  "refresh_token": "你的快麦REFRESH_TOKEN(可选)",
  "updated_at": ""
}
```

### 3️⃣ 创建 APK 目录（用于 OTA 更新）

```bash
mkdir -p ./data/apk
```

> 虽然目录会自动创建，但建议提前创建确保权限正确。

---

## 部署命令

```bash
# 进入 docker-deploy 目录
cd docker-deploy

# 准备好 .env 和 kuaimai.json 后：
docker-compose up -d --build

# 或指定 yaml 文件：
docker-compose -f docker-compose.yaml up -d --build
```

---

## 检查结论

**✅ 可以部署**。docker-deploy 目录结构完整，与 backend 完全同步，`Dockerfile` 和 `docker-compose.yml` 配置正确。部署前只需准备好 `.env` 和 `kuaimai.json` 两个配置文件即可。
