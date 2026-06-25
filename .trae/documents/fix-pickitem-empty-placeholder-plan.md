# 取货单详情库区图/箱图空状态添加文字占位

## 问题

取货单详情页中，当库区图或箱图没有数据时，显示空白方块，不醒目。用户希望在没有图片时显示"库区""箱图"文字提示。

## 当前状态

[PickItemRow.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/components/PickItemRow.kt) 中两个缩略图 Box 的 `if` 块缺少 `else` 分支：

- **库区图**（L154）：`if (!areaImageUrl.isNullOrEmpty()) { AsyncImage(...) }` — 为空时不显示任何内容
- **箱图**（L182）：`if (!boxImageUrl.isNullOrEmpty()) { AsyncImage(...) }` — 为空时不显示任何内容

旁边的 SKU 规格图（L103-L112）已有正确的空状态占位：`else { Text(text = "规格图", fontSize = 9.sp, color = TextMuted) }`

## 修复

仅修改 **1 个文件**，2 处 `if` 块各加 `else`：

| 文件 | 位置 | 改动 |
|:-----|:-----|:-----|
| `app/.../components/PickItemRow.kt` | L154 if 后 | `else { Text("库区", fontSize = 9.sp, color = TextMuted) }` |
| `app/.../components/PickItemRow.kt` | L182 if 后 | `else { Text("箱图", fontSize = 9.sp, color = TextMuted) }` |

## 前置条件二次审查

| # | 检查项 | 状态 | 证据 |
|:--|:-------|:----:|:-----|
| 1 | 样式一致 | ✅ | 完全复用 SKU 规格图占位模式：9sp、TextMuted、居中显示 |
| 2 | TextMuted 已导入 | ✅ | L50: `import com.kuaimai.pda.ui.theme.TextMuted` |
| 3 | PickItemRow 调用方 | ✅ | 仅 PickDetailScreen.kt:L368 一处调用，参数 `urls?.areaUrl`/`urls?.boxUrl` 可为 null |
| 4 | if 条件匹配 | ✅ | `!areaImageUrl.isNullOrEmpty()` 与 `urls?.areaUrl` 可为 null 对齐 |
| 5 | 有图片时不受影响 | ✅ | AsyncImage 渲染逻辑不变 |

## 验证

- 查看有图片的商品 → 缩略图正常显示
- 查看无图片的商品 → 显示"库区"、"箱图"灰色文字
- 完成态（alpha 0.65）→ 占位文字也跟着变淡，风格一致
