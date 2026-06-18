# 部署前核查报告 v1.41

## 摘要

对 `docker-deploy/` 与 `backend/` 进行全量对比，确保部署前所有变更已同步且无冗余文件。

---

## 核查结果：3 个问题，需修复后再部署

---

### ❌ 问题 1：3 个关键文件未同步（docker-deploy 落后）

上次修改的 APK 下载二维码功能（3 个文件）未被同步到 docker-deploy。

| 文件 | backend 最新 | docker-deploy 旧版本 | 后果 |
|:-----|:------------|:--------------------|:-----|
| [auth.py:18](file:///d:/trea项目/快麦取货通/backend/app/auth.py#L18) | 包含 `"/apk/"` | 缺少 `"/apk/"` | PDA 扫码下载 APK 时被 API Key 拦截，返回 401 |
| [system.py:109-120](file:///d:/trea项目/快麦取货通/backend/app/routers/system.py#L109-L120) | 有 `GET /api/app-version/qrcode` 路由 | 无此路由 | 管理后台 APK 二维码 API 返回 404 |
| [admin.py:740-751](file:///d:/trea项目/快麦取货通/backend/app/routers/admin.py#L740-L751) | 有二维码加载 JS | 无二维码 JS | 管理后台 APK 页不显示下载二维码 |

**修复**：运行同步脚本即可。

```powershell
.\scripts\sync-to-docker-deploy.ps1 -Force
```

---

### ℹ️ docker-compose.yaml（绿联 NAS 自动生成，正常行为）

- 绿联 NAS Docker 编辑器在编辑 `docker-compose.yml` 时会自动生成 `.yaml` 副本
- 这是 NAS 系统的正常行为，不作为问题处理
- `docker-compose.yml` 是唯一维护的源文件，NAS 上的 Docker 使用 `.yaml` 也指向同一配置

---

### ✅ 问题 3：config.py 端口默认值（已同步）

| 文件 | 端口 |
|:-----|:----:|
| backend/app/config.py:26 | 8900 ✅ |
| docker-deploy/app/config.py:26 | 8900 ✅ |
| Dockerfile | 8900 ✅ |
| docker-compose.yml | 8900:8900 ✅ |

---

### ✅ 问题 4：orders.py completed_count 修复（已同步）

| 文件 | completed_count |
|:-----|:---------------|
| backend/app/routers/orders.py | `completed_count as new_completed` ✅ |
| docker-deploy/app/routers/orders.py | `completed_count as new_completed` ✅ |

---

## 部署前操作清单

| 步骤 | 操作 | 命令 |
|:----:|:-----|:-----|
| 1 | 删除孤儿文件 | `Remove-Item docker-deploy/docker-compose.yaml` |
| 2 | 同步最新代码 | `.\scripts\sync-to-docker-deploy.ps1 -Force` |
| 3 | 验证同步结果：auth.py 含 `/apk/` | `Select-String "/apk/" docker-deploy/app/auth.py` |
| 4 | 验证同步结果：system.py 含 qrcode | `Select-String "qrcode" docker-deploy/app/routers/system.py` |
| 5 | 部署 | `cd backend; docker-compose up -d --build` |

---

## 总结

| 检查项 | 状态 |
|:-------|:----:|
| 3 个 APK 下载二维码文件是否同步 | ❌ 未同步 → 需运行 sync 脚本 |
| docker-compose.yaml 孤儿文件 | ❌ 存在 → 需删除 |
| config.py 端口默认值 8900 | ✅ 一致 |
| orders.py completed_count 修复 | ✅ 一致 |
| Dockerfile 配置 | ✅ 一致 |
| docker-compose.yml 端口映射 | ✅ 一致 |
| .env.docker.example 环境变量 | ✅ 一致 |

**仅需 2 步修复：删除孤儿文件 + 运行 sync 脚本，然后即可部署。**
