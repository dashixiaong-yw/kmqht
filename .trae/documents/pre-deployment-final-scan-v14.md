# v1.37 终极全量扫描 — 剩余Bug与修复计划

> 四路并行扫描：Compose UI + Android核心层 + 后端/部署 + 安全/数据流/并发
> 基准版本：v1.37 (2bac89f)
> 扫描时间：2026-06-18

---

## ✅ v1.37已验证修复

| 修复项 | 状态 |
|:-------|:----:|
| _call_api() 使用 _get_client() 连接池 | ❌ **v1.37回归 — 见BUG#1** |
| _get_client() 双检锁 | ❌ **未修复 — 见BUG#3** |
| OrderSyncWorker 6 sync方法 + try-catch响应检查 | ✅ |
| KuaimaiApiService 返回 ItemUpdateResponse | ✅ |
| ProductViewModel 移除 apiService | ✅ |
| hasSupplier str() 兼容 | ✅ |
| 图片URL trimEnd('/') | ❌ **部分修复 — areaImageUrl有、boxImageUrl无 — 见BUG#4** |

---

## 🔴 CRASH — 0项

## 🟠 HIGH — 2项

| # | 文件 | 行 | 问题 | 来源 |
|:-:|:-----|:--:|:------|:----:|
| **1** | `admin.py` | L28+L128+L225+L269+L337+L691 | **XSS: `request.base_url`(Host头)未经转义嵌入HTML** — `/admin`无需认证。攻击者构造恶意`Host: "><script>alert(1)`, HTML被注入，可窃取管理员API Key | 安全扫描 |
| **2** | `kuaimai_api.py` | L85 | **v1.37回归: `_call_api()`仍使用新`httpx.AsyncClient`而非`_get_client()`** — 每次调用创建新TCP连接，高并发下连接泄漏 | 后端扫描 |

## 🟡 MEDIUM — 4项

| # | 文件 | 行 | 问题 | 来源 |
|:-:|:-----|:--:|:------|:----:|
| **3** | `kuaimai_api.py` | L22-L26 | **`_get_client()`双检锁缺陷** — 无锁保护，并发时创建多个AsyncClient实例泄漏 | 安全+后端 |
| **4** | `ProductViewModel.kt` | L174 | **boxImageUrl缺少trimEnd('/')** — areaImageUrl已修复但boxImageUrl遗漏，URL含`//`导致加载失败 | Android核心 |
| **5** | `OrderSyncWorker.kt` | L240-L266 | **syncRemarkUpdate/syncSupplierUpdate未检查`ItemUpdateResponse.success`** — 快麦API返回业务错误时静默删pending_operation | Android核心 |
| **6** | `images.py` | L102-L129 | **替换上传过早DELETE旧记录** — 旧记录L104-L105永久删除后写文件失败，新旧DB记录全部丢失 | 后端 |

## 🔵 LOW — 5项

| # | 文件 | 行 | 问题 |
|:-:|:-----|:--:|:------|
| 7 | `auth.py` | L18 | SKIP_AUTH_PREFIXES中`/api/app-version`前缀匹配过宽(覆盖upload/publish端点) |
| 8 | `ImageRepository.kt` | L76-L99 | syncImagesFromBackend仅增量插入、不删除后端已移除的记录 |
| 9 | `ProductScreen.kt` | L230-L251 | error/message信息文字缺少maxLines溢出保护 |
| 10 | `docker-deploy/` | — | 遗留docker-compose.yaml与docker-compose.yml重复 |
| 11 | `config.py` | L163 | asyncio模块级导入(已够) + 函数内重复导入冗余 |

---

## 修复优先级

### 🚨 P0 — 0项

### ⚠️ P1 — 3项

| # | 修改内容 | 文件 | 估算 |
|:-:|----------|:-----|:----:|
| 2 | `_call_api()` L85 替换`httpx.AsyncClient(...)`为`_get_client()` | `kuaimai_api.py` | 1行 |
| 4 | boxImageUrl trimEnd('/') 与areaImageUrl保持一致 | `ProductViewModel.kt` | 1行 |
| 5 | syncRemarkUpdate/syncSupplierUpdate 检查response.success，失败返回false | `OrderSyncWorker.kt` | 8行 |

### ⚠️ P1-SEC — 2项（安全严重）

| # | 修改内容 | 文件 | 估算 |
|:-:|----------|:-----|:----:|
| 1 | admin.py `escape(base_url)` + 所有`{server_url}`模板位置HTML转义 | `admin.py` | 5行 |
| 3 | _get_client()加`_client_lock`双重检查锁定 | `kuaimai_api.py` | 5行 |

### 📝 P2 — 3项

| # | 修改内容 | 文件 | 估算 |
|:-:|----------|:-----|:----:|
| 6 | images.py 先写文件后删旧记录（反转顺序） | `images.py` | 8行 |
| 7 | auth.py SKIP_AUTH_PREFIXES精确匹配 | `auth.py` | 1行 |
| 8 | ProductScreen error/message加maxLines | `ProductScreen.kt` | 2行 |

---

## 验证步骤

1. `.\gradlew :app:compileReleaseKotlin`（Step 3: lint）
2. `.\gradlew assembleRelease`（Step 4: 构建）
3. `.\scripts\sync-to-docker-deploy.ps1 -Force`（Step 7: 同步）
4. `git add -A && git commit -m "v1.38: ..." && git push`（Step 8: 提交）
