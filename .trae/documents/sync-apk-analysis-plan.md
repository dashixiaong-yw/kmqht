# 同步脚本添加 APK 同步可行性分析

## 现状分析

### 同步脚本当前范围
`sync-to-docker-deploy.ps1` 只同步两样东西：
- `backend/app/` 目录（源代码）
- 9个根级配置文件（Dockerfile、docker-compose.yml 等）

**APK 文件和 `data/apk_version.json` 不在同步范围内。**

### Web 管理后台已有完整的 APK 分发功能

| 步骤 | 操作 | 路由 |
|:----:|------|:-----|
| ① | 构建 APK → 访问 `/admin` | - |
| ② | APK管理 Tab → 选择 APK 文件 + 填版本说明 | `/api/app-version/upload` |
| ③ | 点击"分发"按钮 | `/api/app-version/publish` |
| ④ | PDA 启动时自动发现更新 → 下载 → 安装 | `/api/app-version` + `/api/app-version/download` |

### 数据存储
```
docker-deploy/data/
├── apk/
│   └── 快麦取货通-1.xx.apk      ← APK 文件
└── apk_version.json              ← 版本元信息
```
这些文件不在脚本同步范围内，由 Web 后台上传/分发逻辑直接操作。

---

## 可行性方案对比

### 方案 A：脚本同步 APK（新增功能）

在同步脚本中增加：

```powershell
# 伪代码
$apkSource = Join-Path $ProjectRoot "app\build\outputs\apk\release"
$apkTarget = Join-Path $DockerDeployRoot "data\apk"
# 复制最新 APK 到 docker-deploy/data/apk/
# 自动生成 apk_version.json
```

### 方案 B：保持现状，只用 Web 后台上传（推荐）

**不修改同步脚本**，APK 分发完全走 Web 管理后台 `/admin`。

---

## 核心结论：方案 A 与方案 B 功能完全重叠

### 重叠点

| 功能 | 方案 A（脚本同步） | 方案 B（Web上传） |
|:-----|:------------------|:-----------------|
| APK 文件到达 `/data/apk/` | 通过脚本复制 | 通过 HTTP upload |
| 创建/更新 `apk_version.json` | 脚本需自行生成 | 后端自动处理 |
| 原子写入 | 脚本无原子写入保护 | `os.replace()` 原子写入 |
| 版本号校验 | 需要脚本自行实现 | 后端自动校验版本格式 |
| 文件类型校验 | 需要脚本自行实现 | 后端自动校验 `.apk` |
| 强制更新标记 | 需要脚本自行实现 | 管理后台可选勾选 |
| 更新说明 | 需要脚本自行实现 | 管理后台输入 |
| 版本回滚 | 可保留旧文件 | 无回滚（上传即覆盖） |
| 是否需要重启容器 | 不需要（容器读取同一 volume） | 不需要 |

### 方案 A 的唯一优势

版本回滚 —— 脚本同步时可以保留旧 APK 文件在目录中。但 Web 后台也可以增加此功能。

### 方案 A 的额外风险

1. **版本信息不一致**：脚本需要硬编码或从 build.gradle.kts 解析 `versionName`，但 `updateNotes`、`forceUpdate`、`publishedAt` 无法自动获取，会导致 `apk_version.json` 不完整
2. **无校验逻辑**：脚本没有文件类型校验、版本号格式校验、文件大小检查
3. **同步脚本职责扩大**：当前脚本只处理**源代码和配置**，加入运行时数据会模糊它的职责边界
4. **双向写入冲突**：如果某人通过 Web 后台上传了 v1.50，然后运行同步脚本又把 **本地构建的 v1.50**（可能更新说明不同）覆盖上去，版本信息会被静默覆盖

---

## 建议

```
┌──────────────────────────────────────────────────────┐
│                                                       │
│   构建 APK → 签名的 APK → 打开发布网页                │
│   (本地 gradlew)         (浏览器访问 /admin)           │
│                                                       │
│   上传 → 填写更新说明 → 分发 → PDA 收到更新             │
│   (两步操作，安全可控)                                   │
│                                                       │
└──────────────────────────────────────────────────────┘
```

**推荐不要修改同步脚本。** 保持现有分工清晰：

| 工具 | 职责 |
|:-----|:------|
| `sync-to-docker-deploy.ps1` | 同步**源代码 + 配置文件** |
| `Web 管理后台 /admin` | 管理**运行时数据（APK分发）** |
| `docker-compose volumes` | 运行时数据持久化到 `docker-deploy/data/` |

**结论：两个功能完全重叠，不建议在同步脚本中加 APK 同步。**
