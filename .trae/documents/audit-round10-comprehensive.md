# 第十次全面审计报告

**审计时间**: 2026-06-19 | **范围**: v1.21 ~ v1.51 (30版本) | **深度**: 代码级

## 审计结果总览

| 维度 | 检查项 | ✅ | ⚠️ | ❌ |
|:-----|:------:|:--:|:--:|:-:|
| Backend API 路由 | 42 | 41 | 1 | 0 |
| Backend 基础架构 | 23 | 23 | 0 | 0 |
| Android 核心逻辑 | 24 | 24 | 0 | 0 |
| Android UI | 10 | 8 | 1 | 1 |
| 构建配置/部署 | 43 | 41 | 1 | 0 |
| APK 下载链路 | 11 | 9 | 0 | **2** |
| **总计** | **153** | **146** | **3** | **3** |

## 发现的缺陷清单

**152 项已正确实现，6 项需要修复**:

### P0: auth.py SKIP_AUTH_PREFIXES 顺序错误 — v1.51 修复未生效

- **位置**: [auth.py:L19](file:///d:/trea项目/快麦取货通/backend/app/auth.py#L19)
- **问题**: `/api/app-version/download` 和 `/api/app-version/qrcode` 排在 `/api/app-version` 之后。中间件遍历 prefix 时先匹配到 `/api/app-version`，触发 `break` 逻辑（因 path != "/api/app-version"），直接退出循环，不会继续检查后面的 `/api/app-version/download`。结果：PDA 无认证下载 APK 时返回 **401**。
- **影响**: 生产环境 100% 触发 — PDA 扫码下载 APK 永远失败
- **修复**: 将具体子路径移到通用前缀之前

### P3: auth.py 残留死代码 /apk-download

- **位置**: [auth.py:L19](file:///d:/trea项目/快麦取货通/backend/app/auth.py#L19) 末尾
- **说明**: v1.47 引入 `/apk-download` 但该路径从无对应路由，现所有流量走 `/api/app-version/download`

### P2: orders.py restore_item 缺少 status=1 校验

- **位置**: [orders.py:L265-304](file:///d:/trea项目/快麦取货通/backend/app/routers/orders.py#L265)
- **问题**: complete_item、delete_item、add_item 都有 status=1 已完成拦截，但 restore_item 没有
- **影响**: 已完成取货单的明细可被恢复，订单状态可能错误回退

### P3: CameraScanScreen BarcodeScanner 缺少 DisposableEffect 释放

- **位置**: [CameraScanScreen.kt:L122](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/scanner/CameraScanScreen.kt#L122)
- **问题**: `BarcodeScanning.getClient()` 创建的 Scanner 没有对应的 DisposableEffect 调用 `.close()`
- **影响**: ML Kit native 资源泄漏

### P2: PickItemRow 规格图触摸热区 52dp（应为 56dp）

- **位置**: [PickItemRow.kt:L90](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/components/PickItemRow.kt#L90)
- **问题**: v1.20 记录修复 52→56dp，但当前仍为 52dp

### P3: build.gradle.kts shrinkResources 未启用

- **位置**: [build.gradle.kts](file:///d:/trea项目/快麦取货通/app/build.gradle.kts)
- **问题**: v1.22 记录设置 `shrinkResources = true` 但实际未添加。非功能性问题
