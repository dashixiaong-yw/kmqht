# v1.35 终极全量扫描 — 剩余Bug与修复计划

> 四路并行扫描：Compose UI + Android核心层 + 后端/部署 + 安全/数据流/并发
> 基准版本：v1.35 (61a8a8d)
> 扫描时间：2026-06-18

---

## ✅ v1.35已验证修复（8/8通过）

| 修复项 | 状态 |
|:-------|:----:|
| querySupplierList/SupplierListResponse 死代码删除 | ✅ |
| ItemUpdateRequest suppliers 死代码移除 | ✅ |
| syncSupplierUpdate 不传skuRemark | ✅ |
| cache.py asyncio.sleep(1) 重试退避 | ✅ |
| _config_lock 移到config.py（解决循环依赖） | ✅ |
| kuaimai_api.py 移除threading import | ✅ |
| sku_cache cached_at 索引 | ✅ |

---

## 🔴 CRASH — 0项

## 🟠 HIGH — 1项

| # | 文件 | 行 | 问题 | 来源 |
|:-:|:-----|:--:|:------|:----:|
| **1** | `admin.py` | L518-L519 | **用户管理表格 `u.is_active`/`u.created_at` 仍为snake_case** — v1.32修复了L508-L509，v1.34修复了L568，但**L518-L519是另一处完全相同的模板**。所有用户状态显示为"禁用"，创建时间显示为"-" | 安全+数据流 |

## 🟡 MEDIUM — 5项

| # | 文件 | 行 | 问题 | 来源 |
|:-:|:-----|:--:|:------|:----:|
| **2** | `images.py` | L44-L50 | **_check_upload_rate v1.34引入空列表return BUG** — 所有记录过期后`del`+`return`，不记录本次上传，速率限制实际永不生效 | 后端+安全 |
| **3** | `images.py` | L104-L134 | **替换上传写文件失败时新旧DB记录全部丢失** — DELETE旧→INSERT新→写文件失败→DELETE新，DB记录全消失 | 安全 |
| **4** | `OrderSyncWorker.kt` | L258-L267 | **syncImageUpload finally删文件导致重试永久失效** — 失败时finally删除文件，重试时文件不存在直接return false | Android核心 |
| **5** | `images.py` | L44-L50 | **上传速率计数器空列表return导致每次上传被跳过**（同BUG#2） | 后端 |
| **6** | `docker-deploy/` | — | **两份docker-compose.yml/.yaml文件** — 同步脚本复制两份，修改不同步则配置漂移 | 后端 |

## 🔵 LOW — 8项

| # | 文件 | 行 | 问题 |
|:-:|:-----|:--:|:------|
| 7 | `PickItemRow.kt` | L20 | 未使用的 `MaterialTheme` import |
| 8 | `HomeScreen.kt` | L19 | 未使用的 `Inventory` icon import |
| 9 | `PickDetailScreen.kt` | L31-L32 | 未使用的 `DropdownMenu`/`DropdownMenuItem` import |
| 10 | `PickDetailScreen.kt` | L64 | 未使用的 `LocalView` import |
| 11 | `ProductScreen.kt` | L392-L397 | 供应商名称无maxLines（20sp Bold长文本溢出） |
| 12 | `SupplierSelectDialog.kt` | L157-L161 | 供应商名称无maxLines |
| 13 | `HomeScreen.kt` | L208-L215 | sessionWarningText无maxLines |
| 14 | `OrderSyncWorker.kt` | L17,L60-L62 | 未用PickOrderRepository import + authRepository变量 |

---

## 修复优先级

### 🚨 P0 — 0项

### ⚠️ P1 — 3项（功能+安全严重）

| # | 修改内容 | 文件 | 估算 |
|:-:|----------|:-----|:----:|
| 1 | admin.js L518 `u.is_active`→`u.isActive`, L519 `u.created_at`→`u.createdAt` | `admin.py` | 2行 |
| 2 | images.py `_check_upload_rate` 移除空列表提前return，确保每次追加时间戳 | `images.py` | 3行 |
| 3 | OrderSyncWorker syncImageUpload 移出finally，失败时不删文件 | `OrderSyncWorker.kt` | 4行 |

### 📝 P2 — 6项（安全加固+代码清理）

| # | 修改内容 | 文件 | 估算 |
|:-:|----------|:-----|:----:|
| 4 | images.py 替换上传事务保护（先INSERT后删旧，写文件失败回滚） | `images.py` | 5行 |
| 5 | UI 文件清理未用import(4处)+maxLines(3处) | 多个UI文件 | 10行 |
| 6 | OrderSyncWorker 清理死代码+添加注释 | `OrderSyncWorker.kt` | 3行 |
| 7 | docker-deploy 移除docker-compose.yaml | `sync.ps1` | 2行 |

---

## 验证步骤

1. `.\gradlew :app:compileReleaseKotlin`（Step 3: lint）
2. `.\gradlew assembleRelease`（Step 4: 构建）
3. `.\scripts\sync-to-docker-deploy.ps1 -Force`（Step 7: 同步）
4. `git add -A && git commit -m "v1.36: ..." && git push`（Step 8: 提交）
