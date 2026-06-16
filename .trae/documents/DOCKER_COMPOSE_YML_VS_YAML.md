# Docker Compose 配置文件双扩展名机制说明

## 为什么会有两个文件？

在 `docker-deploy/` 目录中会同时存在：

```
docker-deploy/
├── docker-compose.yml     ← 源文件（与根目录 docker-compose.yml 保持一致）
└── docker-compose.yaml    ← 自动生成文件（供绿联 NAS Docker 使用）
```

**原因：绿联 NAS 的 Docker 界面只识别 `.yaml` 扩展名**，不识别 `.yml`。

---

## 工作原理

### 1. 源代码位置

**只有一个地方是"真的"：项目根目录的 `docker-compose.yml`**

```
你的项目/
└── docker-compose.yml     ← 唯一需要手动维护的文件
```

所有版本号修改、服务配置修改，**全部只改这一个文件**。

### 2. 同步流程

运行 `scripts/sync-to-docker-deploy.ps1` 后，发生以下两步：

```
Step 1 ─ 根目录 docker-compose.yml  ── 拷贝 ──→  docker-deploy/docker-compose.yml
                       ↓
Step 2 ─ docker-deploy/docker-compose.yml ─ 拷贝 ─→ docker-deploy/docker-compose.yaml
                      (脚本第241-264行)
```

### 3. 同步脚本关键代码

在 `scripts/sync-to-docker-deploy.ps1` 中第 59 行和第 241-264 行：

```powershell
# Step A: 从根目录同步 docker-compose.yml 到 docker-deploy/
$configFiles = @(
    ...
    "docker-compose.yml",          # ← 第59行：列入同步清单
    ...
)

# Step B: 在 docker-deploy/ 内自动生成 .yaml 副本（第241-264行）
$yamlSource = Join-Path $DockerDeployRoot "docker-compose.yml"
$yamlTarget = Join-Path $DockerDeployRoot "docker-compose.yaml"
# 然后将 .yml 内容复制到 .yaml
```

---

## 🚫 禁止操作

| 操作 | 后果 |
|------|------|
| 手动修改 `docker-deploy/docker-compose.yaml` | 下次运行同步脚本会被 **完全覆盖** |
| 在 `docker-deploy/docker-compose.yml` 中直接改配置 | 下次同步根目录版本时被覆盖 |
| 删除 `docker-compose.yml` 改用 `docker-compose.yaml` 作为主文件 | 同步脚本找不到源文件，无法生成部署包 |
| 在根目录同时创建 `docker-compose.yml` 和 `docker-compose.yaml` 并手动维护 | 两个文件内容漂移，部署时可能读到旧版本 |

---

## ✅ 正确操作流程

### 场景1：修改版本号 / 服务配置

```bash
# Step 1: 修改根目录文件（唯一修改点）
vi docker-compose.yml     # 修改 BUILD_VERSION / ports / env 等

# Step 2: 运行同步脚本
.\scripts\sync-to-docker-deploy.ps1 -Force

# Step 3: 验证一致性
Select-String "BUILD_VERSION" docker-deploy/docker-compose.yml
Select-String "BUILD_VERSION" docker-deploy/docker-compose.yaml
# 两处输出的 BUILD_VERSION 必须完全相同
```

### 场景2：首次新项目部署

```bash
# Step 1: 项目根目录创建 docker-compose.yml（用 .yml 扩展名）
# Step 2: 运行同步脚本
.\scripts\sync-to-docker-deploy.ps1 -Force

# Step 3: 上传 docker-deploy/ 整个目录到 NAS
# Step 4: 在绿联 NAS 的 Docker 界面，它会自动识别 docker-compose.yaml
```

---

## 新项目落地清单

新建项目时，需要做以下 3 件事：

- [ ] **主文件命名为 `docker-compose.yml`**（放在项目根目录）
- [ ] **在同步脚本中，`docker-compose.yml` 列入 `$configFiles` 数组**（参考第 59 行）
- [ ] **在同步脚本末尾，增加 `.yml → .yaml` 自动拷贝步骤**（参考第 241-264 行）

### 新项目同步脚本模板

如果新项目需要写同步脚本，只需在最后加入以下逻辑：

```powershell
# 位于同步脚本的末尾（所有文件都同步到 docker-deploy/ 之后）
# 将 docker-compose.yml 自动生成 docker-compose.yaml（绿联 NAS 兼容）
$yamlSource = Join-Path $DockerDeployRoot "docker-compose.yml"
$yamlTarget = Join-Path $DockerDeployRoot "docker-compose.yaml"

if (Test-Path -LiteralPath $yamlSource) {
    $shouldCopy = $true
    if ((Test-Path -LiteralPath $yamlTarget) -and -not $Force) {
        $sourceHash = (Get-FileHash -LiteralPath $yamlSource -Algorithm MD5).Hash
        $destHash = (Get-FileHash -LiteralPath $yamlTarget -Algorithm MD5).Hash
        if ($sourceHash -eq $destHash) {
            $shouldCopy = $false
        }
    }
    if ($shouldCopy) {
        Copy-Item -LiteralPath $yamlSource -Destination $yamlTarget -Force
        Write-Host "[OK] docker-compose.yml -> docker-compose.yaml"
    } else {
        Write-Host "[SKIP] docker-compose.yaml (no changes)"
    }
}
```

---

## 常见问题

### Q: 为什么不直接把主文件命名为 `docker-compose.yaml`？

**答：** 可以，但不推荐。
- `docker-compose.yml` 是社区主流命名习惯，开发者一眼就知道是配置文件
- Docker CLI 同时支持两种扩展名，优先找 `.yml`
- 统一用 `.yml` 作为源文件 + 自动生成 `.yaml` 作为 NAS 兼容副本，是最清晰的方案

### Q: NAS 上运行 `docker compose up` 时，Docker 会读哪个文件？

**答：** Docker CLI 会优先找 `docker-compose.yml`，找不到再找 `docker-compose.yaml`。所以在 NAS 上两个文件都在时，实际用的是 `.yml`。但绿联 NAS 的 GUI 只识别 `.yaml`。保留两个文件可以同时满足 **命令行部署** 和 **图形界面部署** 两种方式。

### Q: 我忘了运行同步脚本，直接改了 `docker-deploy/docker-compose.yaml` 怎么办？

**答：** 重新运行同步脚本会覆盖你的修改。补救方法：在运行脚本前，把 `docker-compose.yaml` 中的改动**反向合并**到根目录的 `docker-compose.yml`，再运行同步脚本。

---

## 一句话总结

> **`docker-compose.yml` 是源头（只改它），`docker-compose.yaml` 是绿联 NAS 兼容副本（自动生成，别手改）。**
