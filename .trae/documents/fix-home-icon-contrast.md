# 修复计划：图标 + UI 紧凑 + 回传 + 对话框卡顿

## 一、首页模块图标颜色对比度恢复

v1.79 emoji→Material Icon 时 Search 图标套用白色 tint，在 DangerBg 上不可见。取货列表 emoji 替换未落地。

| 模块 | 修复后 | tint |
|------|--------|------|
| 取货列表 | `Icon(ListAlt)` | `PrimaryLightText` |
| 商品详情 | `Icon(Search)` | `DangerText` |

---

## 二、PDA 小屏 UI 紧凑化

| 元素 | 当前 | 修复 | 原因 |
|------|------|------|------|
| 完成按钮 | `height(36dp)` | `height(24dp)` | 复制自 "+新建"，13sp 文字空白过多 |
| FilterChip | 默认 32dp | `height(28dp)` | M3 默认 minHeight 过大 |
| 图片槽位间距 | `spacedBy(8dp)` | `spacedBy(20dp)` + Row `padding(horizontal=8dp)` | 横向间隙与屏幕边缘间距不足 |
| 图片槽位高度 | `heightIn(max=120dp)` | 移除 | width=height 正方形自适应屏幕 |

---

## 三、备注/供应商回传失败 🔴

v1.79 `ItemUpdateWrapper` 期望包裹层，API 返回扁平 JSON → `wrapper.response` 永为 null → Worker 丢弃操作。回退 `ItemUpdateWrapper`。

---

## 四、供应商对话框卡顿

`showSupplierDialog()` 先弹窗后加载 → 动画期间状态切换卡顿。改为先加载后弹窗。

---

## 五、修改清单

| # | 优先级 | 文件 | 行 | 改动 |
|---|:--:|------|:--:|------|
| 1 | 🟡 | `HomeScreen.kt` | L254 | emoji → `Icon(ListAlt, tint=PrimaryLightText)` |
| 2 | 🟡 | `HomeScreen.kt` | L264 | `Color.White` → `DangerText` |
| 3 | 🟡 | `PickItemRow.kt` | L212/L224 | `height(36)` → `height(24)` |
| 4 | 🟡 | `PickDetailScreen.kt` | L282 | FilterChip + `height(28)` |
| 5 | 🟡 | `ProductScreen.kt` | L538-L539 | Row 加 `padding(horizontal=8.dp)`, `spacedBy(8)→spacedBy(20)` |
| 6 | 🟡 | `ProductScreen.kt` | L605 | 移除 `heightIn(max=120.dp)` |
| 7 | 🔴 | `KuaimaiApiService.kt` | L21/L27 | `ItemUpdateWrapper`→`ItemUpdateResponse` |
| 8 | 🔴 | `OrderSyncWorker.kt` | L256/L291 | 去掉 wrapper 解包 |
| 9 | 🔴 | `ItemUpdateResponse.kt` | — | 删除 `ItemUpdateWrapper` |
| 10 | 🟡 | `ProductViewModel.kt` | L306 | 先加载后弹窗 |

---

## 六、验证

图标/按钮/Chip/槽位紧凑且间距合理，弹窗流畅，回传正常，lint+build 通过。
