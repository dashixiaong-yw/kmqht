# v1.33 终极全量扫描 — 剩余Bug与修复计划

> 四路并行扫描：安全/数据流/并发 + 后端/部署 + Android核心层 + Compose UI
> 基准版本：v1.33 (4e650b9, 60 commits)
> 扫描时间：2026-06-18

---

## ✅ v1.33已验证修复

| 修复项 | 状态 |
|:-------|:----:|
| admin.js body.isActive (Pydantic camelCase) | ✅ |
| APK上传 先read到内存 再删旧文件 | ✅ |
| save_kuaimai_config 原子写入(.tmp→replace) | ✅ |
| images.py 先INSERT DB再写文件 | ✅ (但有BUG#1) |
| _cleanup_orphan_images 每文件独立try-except | ✅ |
| _cleanup_orphan_images 删除空目录 | ✅ |
| Dockerfile pip install后`apk del rust cargo` | ✅ |
| OrderSyncWorker 移除未用PickItemDao import | ✅ |
| App.kt Deps @Volatile nullable | ✅ |
| OrderSyncWorker nullable + uploadImage处理 | ✅ |
| KuaimaiInterceptor 凭证空值检查 | ✅ |
| ImageRepository 增量合并 | ✅ |
| ScannerManager soundPool?.release() | ✅ |

---

## 🔴 CRASH — 1项

| # | 文件 | 行 | 问题 | 来源 |
|:-:|:-----|:--:|:------|:----:|
| **1** | `backend/.../images.py` | L100-L111 | **v1.33 INSERT提前引入BUG：替换上传永远500** — 当`skuOuterId+imageType`已有记录时，L100 INSERT因UNIQUE约束冲突异常→rollback→HTTP 500。旧文件保留、新文件未写、DB不变，替换功能不可用 | 后端扫描 |

## 🟠 HIGH — 1项

| # | 文件 | 行 | 问题 | 来源 |
|:-:|:-----|:--:|:------|:----:|
| **2** | `backend/.../admin.py` | L516 | **用户管理状态仍全部显示为"禁用"** — JS `u.is_active`(snake_case) vs API返回`isActive`(camelCase)。与v1.32修复的L568是同一个模式，L516是另一处遗漏 | 后端扫描 |

## 🟡 MEDIUM — 3项

| # | 文件 | 行 | 问题 | 来源 |
|:-:|:-----|:--:|:------|:----:|
| **3** | `app/.../OrderSyncWorker.kt` | L240-L253 | **uploadImage成功后未持久化回本地ProductImage表** — return值(remoteId,imageUrl)解构后丢弃，App缺少ProductImageDao注入 | Android核心 |
| **4** | `backend/.../admin.py` | L109-L112 | **_save_version_info非原子写入** — 配置类(config.py)已修复，此处显式不一致，写中断导致版本信息损坏 | 后端扫描 |
| **5** | `backend/.../images.py` | L32-L47 | **_upload_counts内存泄漏** — 用户条目只增不减，用户停止上传后永不清理 | 后端扫描 |

## 🔵 LOW — 6项

| # | 文件 | 行 | 问题 |
|:-:|:-----|:--:|:------|
| 6 | `PickItemRow.kt` | L8 | 未使用的 `Arrangement` import |
| 7 | `PickDetailViewModel.kt` | L4 | 未使用的 `Log` import |
| 8 | `ProductScreen.kt` | L500, L510 | `{{}}` 双括号空lambda |
| 9 | `PickDetailScreen.kt` | L105 | `isLoading` 收集但UI从未使用 |
| 10 | `PickDetailScreen.kt` | L308 | FilterChip硬编码 `13.sp` |
| 11 | `SettingsScreen.kt` | L221-L224 | 权限文本无maxLines溢出处理 |

---

## 修复优先级

### 🚨 P0 — 1项（功能不可用）

| # | 修改内容 | 文件 | 估算 |
|:-:|----------|:-----|:----:|
| 1 | images.py INSERT前先DELETE旧记录 | `images.py` L100前 | 4行 |

### ⚠️ P1 — 2项（功能严重）

| # | 修改内容 | 文件 | 估算 |
|:-:|----------|:-----|:----:|
| 2 | admin.js `u.is_active`→`u.isActive` 第二处 | `admin.py` L516 | 1行 |
| 3 | OrderSyncWorker加ProductImageDao+持久化 | `App.kt` + `OrderSyncWorker.kt` | 15行 |

### 📝 P2 — 3项（安全加固）

| # | 修改内容 | 文件 | 估算 |
|:-:|----------|:-----|:----:|
| 4 | _save_version_info 原子写入(.tmp→replace) | `admin.py` L109-L112 | 3行 |
| 5 | _upload_counts清理空用户条目 | `images.py` | 2行 |
| 6 | 各文件清理未用import/双括号/硬编码 | 多个UI文件 | 6行 |

---

## 验证步骤

1. `cd backend && python -c "import sys; sys.path.insert(0,'.'); from main import app; print('OK')"`
2. `.\gradlew :app:compileReleaseKotlin`（Step 3: lint）
3. `.\gradlew assembleRelease`（Step 4: 构建）
4. `.\scripts\sync-to-docker-deploy.ps1 -Force`（Step 7: 同步）
5. `git add -A && git commit -m "v1.34: ..." && git push`（Step 8: 提交）
