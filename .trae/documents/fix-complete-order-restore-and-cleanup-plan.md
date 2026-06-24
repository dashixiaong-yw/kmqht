# 修复：全部完成后隐藏恢复按钮 + 禁用长按删除 + 24小时自动清理

## 审查结论

### 完整审查清单

| # | 检查项 | 状态 |
|:-:|:-------|:----:|
| 1 | PickItemRow 还有别的调用点吗？ | ✅ 仅 PickDetailScreen.kt 一处，新增参数默认 false 无影响 |
| 2 | `order?.status` 可能为 null？ | ✅ 空安全 `?.` 返回 false，视觉上不受影响 |
| 3 | order.status 更新时序与 UI 同步 | ✅ `_order` 是 MutableStateFlow，`order?.status == 1` 在重组时重新计算 |
| 4 | 超时自动完成后（completion_type=1）按钮是否隐藏 | ✅ 后端同时将 order 和所有 items 标记为 status=1，条件满足 |
| 5 | 24h清理是否会误删进行中订单 | ✅ `WHERE status = 1 AND completed_at < ?` 只删已完成的 |
| 6 | 24h清理是否影响超时完成的订单 | ✅ 超时完成也会写入 `completed_at`，按该时间计算 |
| 7 | 长按删除是否也存在同样问题 | ⚠️ **存在！** 后端 `delete_item` 同样校验 `order.status == 1`，前端无防护 |

### 三个问题

| 问题 | 根因 | 修复方案 |
|:-----|:-----|:---------|
| **① 恢复按钮报错** | PickItemRow 按钮仅依赖 `item.status`，不检查 `order.status` | 取货单全部完成后，恢复按钮位置显示「已完成」文字 |
| **② 长按删除报错** | PickDetailScreen 传递 `onLongPress` 时不检查 `order.status` | `order.status == 1` 时长按不做任何事 |
| **③ 无24h自动清理** | `_cleanup_completed_orders` 阈值是 30 天 | 改为 `hours=24` |

---

## 改动详情（3个文件）

### 文件1：PickItemRow.kt（第52-201行）

**新增参数**：
```kotlin
fun PickItemRow(
    ...
    orderCompleted: Boolean = false,
)
```

**按钮逻辑（第175-201行）**：
```
改前：
  if (isCompleted) → TextButton("↩ 恢复")
  else             → TextButton("✓ 完成")

改后：
  if (isCompleted && orderCompleted) → Text("已完成", fontSize = 13.sp, color = TextMuted)
  else if (isCompleted)              → TextButton("↩ 恢复")
  else                               → TextButton("✓ 完成")
```

### 文件2：PickDetailScreen.kt（第314行、第315行）

**PickItemRow 调用处（第314行附近）**：
```
改前：onRestore = { viewModel.restoreItem(item.id) },
改后：onRestore = { viewModel.restoreItem(item.id) },
       orderCompleted = order?.status == 1,  // ← 参数1：控制恢复按钮
```

**onLongPress 传递处（第315行附近）**：
```
改前：onLongPress = { showDeleteConfirm = item },
改后：onLongPress = { if (order?.status != 1) showDeleteConfirm = item },
       // ↑ 参数2：取货单已完成时禁用长按删除
```

### 文件3：backend/main.py（第229-245行）

```python
# 改前
def _cleanup_completed_orders():
    """清理30天前已完成的取货单"""
    cutoff = (beijing_now() - timedelta(days=30)).strftime(...)
    ...
    logger.info(f"已清理{deleted}条30天前的已完成取货单")

# 改后
def _cleanup_completed_orders():
    """清理24小时前已完成的取货单"""
    cutoff = (beijing_now() - timedelta(hours=24)).strftime(...)
    ...
    logger.info(f"已清理{deleted}条24小时前的已完成取货单")
```

---

## 验证清单

1. 取货单全部完成后 → 明细右侧无恢复按钮，显示「已完成」灰色文字
2. 取货单全部完成后 → 长按明细不弹出删除确认弹窗
3. 未完成的取货单 → 恢复/完成/长按删除均正常
4. lint 通过
5. APK 构建成功（快麦取货通-2.8.apk）
6. 后端重新部署 `docker-compose up -d --build`
