# v1.38 40次更新审计 — 回归+一致性问题修复计划

> 4路并行审计：跨层一致性 + 后端修复审计 + Android核心审计 + Compose UI审计
> 基准版本：v1.38 (25cd175)
> 扫描时间：2026-06-18

---

## ✅ 最近40次更新修复验证总表

| 版本 | 修复数 | 核心修复内容 | 本轮验证 |
|:----|:------:|:-------------|:--------:|
| v1.20 | 7 | XSS+RECEIVER_EXPORTED+热区+引导条+日志+retryCount | ✅ |
| v1.21 | 2 | WorkManager去重+图片同步事务 | ✅ |
| v1.22 | 14 | OTA+安全配置+proguard+索引+ANR+构建+@Immutable | ✅ |
| v1.23 | 8 | 凭证保存+JSON转义+APK下载+签名+锁+路径+校验+热区 | ✅ |
| v1.24 | 9 | config_lock+Scaffold+proguard+delay+innerPadding+serverUrl+LaunchedEffect | ✅ |
| v1.25 | 10 | 快麦API全链路+admin启动+Worker依赖+路径+凭证锁+docker | ✅ |
| v1.26 | 5 | NetworkMonitor生命周期+Hilt编译 | ✅ |
| v1.27 | 7 | 端口统一8900+ThreadLocal+ProGuard+camelCase+image_url+锁+retryCount | ✅ |
| v1.31 | 11 | 回归修复(config+TimeUtils+ML Kit泄漏+Scaffold+ripple+死代码) | ✅ |
| v1.32 | 12 | 回归修复(Optional+shutdown+凭证锁+路径+增量合并+null+XSS) | ✅ |
| v1.33 | 9 | isActive+APK顺序+原子写入+INSERT-then-write+孤儿清理+镜像减负 | ✅ |
| v1.34 | 8 | 替换上传500+isActive第二处+持久化+原子写入+内存泄漏+UI清理 | ✅ |
| v1.35 | 10 | 死代码+重试退避+循环依赖+索引+SupplierDto | ✅ |
| v1.36 | 8 | 锁统一+isActive第三处+速率限制+写文件回滚+重试+docker-compose+UI清理 | ✅ |
| v1.37 | 9 | 连接池+响应检查+DTO+str兼容+wrapper_key+URL斜杠 | ⚠️ **部分失败** |
| v1.38 | 7 | XSS+双检锁+images顺序+响应检查+精确匹配+maxLines | ⚠️ **部分失败** |

---

## 🔴 CRASH — 1项

| # | 文件 | 行 | 问题 | 来源 |
|:-:|:-----|:--:|:------|:------|
| **1** | `kuaimai_api.py` | L20 | **v1.37引入`_client_lock`但缺失`import threading`** → 模块导入时`NameError: name 'threading' is not defined`，后端完全无法启动。v1.35移除了旧threading import，v1.37加`_client_lock`时未加回 | 后端审计 |

## 🟠 HIGH — 2项

| # | 文件 | 行 | 问题 | 来源 |
|:-:|:-----|:--:|:------|:------|
| **2** | `admin.py` | L28+L121+L225+L269+L337+L691 | **`from html import escape`已导入但从未调用** — `base_url`从Host头获取后未escape直接嵌入HTML 6处，XSS攻击在v1.38仍有效 | 后端审计 |
| **3** | `ProductScreen.kt` | L506 | **`{{}}`双lambda仍存在** — v1.34说修复了但L506`onClick = if (canManageAreaImage) onUploadArea else {{}}`未改，L516的box已改为`{}`但area漏了 | UI审计 |

## 🟡 MEDIUM — 6项

| # | 文件 | 行 | 问题 | 来源 |
|:-:|:-----|:--:|:------|:------|
| **4** | `kuaimai_api.py` | L190 | **get_supplier_list()响应仍未解包wrapper_key** — v1.35/1.37/1.38三次提到修复但代码未应用，`supplier_list_query_response`外层未解包 | 后端审计 |
| **5** | `admin.py` | L613 | **拣货区`a.created_at`仍为snake_case** — Pydantic返回`createdAt`(camelCase)，表格永远显示"-" | 跨层审计 |
| **6** | `ImageRepository.kt` | L80 | **JSONArray(responseBody)解析错误** — 后端返回`{"success":true,"data":[...]}`对象，但代码用`JSONArray`解析（期望`[...]`数组），`syncImagesFromBackend`永远失败 | 跨层审计 |
| **7** | `PickDetailViewModel.kt` | L376-L377 | **getImageUrls()缺少`trimEnd('/')`** — ProductViewModel已修但PickDetailViewModel遗漏，配置带尾部/的URL时双斜杠图片加载失败 | UI审计 |
| **8** | `HomeScreen.kt` | L6 | **死import `Arrangement`** — 文件未使用此import | UI审计 |
| **9** | `PickDetailScreen.kt` | L105 | **`isLoading`收集但UI从未使用** — ViewModel中5处设置loading但UI不消费 | UI审计 |

---

## 修复优先级

### 🚨 P0 — 1项（部署阻断）

| # | 修改内容 | 文件 | 修复方式 |
|:-:|----------|:-----|:---------|
| 1 | 加回`import threading` | `kuaimai_api.py` L3 | 1行 |

### ⚠️ P1 — 3项（功能严重+安全）

| # | 修改内容 | 文件 | 修复方式 |
|:-:|----------|:-----|:---------|
| 2 | admin.py 所有`{server_url}`位置调用`escape()` | `admin.py` L121,L225,L269,L337,L691 | 5行 |
| 3 | ProductScreen L506 `{{}}`→`{}` | `ProductScreen.kt` L506 | 1行 |
| 4 | get_supplier_list解包wrapper_key | `kuaimai_api.py` L190 | 1行 |

### 📝 P2 — 5项（一致性+功能辅助）

| # | 修改内容 | 文件 | 修复方式 |
|:-:|----------|:-----|:---------|
| 5 | admin.py L613 `a.created_at`→`a.createdAt` | `admin.py` L613 | 1行 |
| 6 | ImageRepository JSONObject解析 | `ImageRepository.kt` L80 | 1行 |
| 7 | PickDetailViewModel trimEnd('/') | `PickDetailViewModel.kt` L376-L377 | 2行 |
| 8 | HomeScreen移除Arrangement import | `HomeScreen.kt` L6 | 1行 |
| 9 | PickDetailScreen移除isLoading收集 | `PickDetailScreen.kt` L105 | 1行 |

---

## 验证步骤

1. `.\gradlew :app:compileReleaseKotlin`（Step 3: lint）
2. `.\gradlew assembleRelease`（Step 4: 构建）
3. `.\scripts\sync-to-docker-deploy.ps1 -Force`（Step 7: 同步）
4. `git add -A && git commit -m "v1.39: ..." && git push`（Step 8: 提交）
