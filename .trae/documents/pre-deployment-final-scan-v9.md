# v1.32 终极全量扫描 — 剩余Bug与修复计划

> 四路并行扫描：安全/数据流/并发 + 后端/部署 + Android核心层 + Compose UI
> 基准版本：v1.32 (626ac3e, 59 commits)
> 扫描时间：2026-06-18

---

## 🔴 CRASH — 0项（v1.32已验证全部清除）

## 🟠 HIGH — 3项

| # | 文件 | 行 | 问题 | 来源 |
|:-:|:-----|:--:|:------|:----:|
| **1** | `backend/.../admin.py` | L568 | **Admin UI 用户启用/禁用功能失效** — JS发送`body.is_active`(snake_case)但后端Pydantic期望`isActive`(camelCase)，被Pydantic v2静默丢弃，字段永不更新 | 后端扫描 |
| **2** | `backend/.../admin.py` | L52-L62 | **APK上传先删旧文件再读新文件** → 若`file.file.read()`中途失败(网络断开)，旧APK已丢失新APK未保存，OTA彻底中断 | 后端扫描 |
| **3** | `backend/.../config.py` | L145-L146 | **save_kuaimai_config非原子写入** — 直接覆盖`kuaimai.json`，若写入中断(磁盘满/断电)，文件JSON损坏，全部4个凭证丢失 | 后端扫描 |

## 🟡 MEDIUM — 6项

| # | 文件 | 行 | 问题 | 来源 |
|:-:|:-----|:--:|:------|:----:|
| 4 | `backend/main.py` | L300-L336 | **_cleanup_orphan_images与图片上传竞态** — 上传先写文件后INSERT DB，清理线程在时间窗口内误删新文件 | 后端扫描 |
| 5 | `backend/main.py` | L325-L330 | **_cleanup_orphan_images单文件失败中断整个清理** — os.path.getmtime/os.remove未单独try-except | 后端扫描 |
| 6 | `backend/Dockerfile` | L5 | **rust/cargo ~200MB残留生产镜像** — pip install后未apk del清理 | 后端扫描 |
| 7 | `docker-deploy/` | 根目录vs data/ | **两份kuaimai.json时间戳不一致** — Docker读data/kuaimai.json，根目录文件不被使用但混淆 | 后端扫描 |
| 8 | `backend/.../auth.py` | L50-L55 | **登录时未检查userId=0L边界** — LoginScreen已修复但auth.py后端缺少防御(已确认安全) | 安全扫描 |
| 9 | `app/.../SettingsScreen.kt` | L85 | **HorizontalDivider color使用已废弃API** — 需替换为新API | UI扫描 |

## 🔵 LOW — 2项

| # | 文件 | 行 | 问题 |
|:-:|:-----|:--:|:------|
| 10 | `backend/main.py` | L319-L331 | _cleanup_orphan_images不清理空子目录，长期积累空目录 |
| 11 | `app/.../OrderSyncWorker.kt` | L14 | 未使用的 import PickItemDao |

---

## 修复优先级

### 🚨 P0 — 0项（无部署阻断）

### ⚠️ P1 — 3项（功能严重）

| # | 修改内容 | 文件 | 估算 |
|:-:|----------|:-----|:----:|
| 1 | admin.js `body.is_active` → `body.isActive` | `admin.py` L568 | 1行 |
| 2 | APK上传改为：先read文件到内存 → 再删旧文件 → 再写新文件 | `admin.py` L52-L67 | 3行 |
| 3 | save_kuaimai_config改为原子写入：写.tmp → os.replace | `config.py` L145-L146 | 4行 |

### 📝 P2 — 6项（功能辅助+清理）

| # | 修改内容 | 文件 | 估算 |
|:-:|----------|:-----|:----:|
| 4 | _cleanup_orphan_images先INSERT DB后写文件 | `images.py` + `main.py` | 5行 |
| 5 | _cleanup_orphan_images每文件独立try-except | `main.py` L325-L330 | 4行 |
| 6 | Dockerfile pip install后`apk del rust cargo` | `Dockerfile` | 1行 |
| 7 | sync脚本同步后删除docker-deploy/kuaimai.json | `sync-to-docker-deploy.ps1` | 3行 |
| 8 | _cleanup_orphan_images删除空目录 | `main.py` L319-L331 | 5行 |
| 9 | OrderSyncWorker移除未使用import | `OrderSyncWorker.kt` L14 | 1行 |

---

## 验证步骤

1. `cd backend && python -c "import sys; sys.path.insert(0,'.'); from main import app; print('OK')"`
2. `.\gradlew :app:compileReleaseKotlin`（Step 3: lint）
3. `.\gradlew assembleRelease`（Step 4: 构建）
4. `.\scripts\sync-to-docker-deploy.ps1 -Force`（Step 7: 同步）
5. `git add -A && git commit -m "v1.33: ..." && git push`（Step 8: 提交）
