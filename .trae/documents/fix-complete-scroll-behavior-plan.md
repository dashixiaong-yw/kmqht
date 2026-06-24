# 修复：取货单完成商品后滚动行为

## 方案：方案F — LazyColumn key 加入 status 前缀 + remember key 加固

### 改动：PickDetailScreen.kt 共 5 行

#### 1. LazyColumn key 加入 status（第296行）
```kotlin
// 改前
key = { it.id }
// 改后
key = { "${it.status}_${it.id}" }
```

**原理**：完成商品后 `status 0→1`，key 从 `"0_123"` → `"1_123"`。旧 key 在 index 0 消失 → LazyColumn 不追踪消失的 key → 视口自然停在 index 0。

#### 2. remember 加 skuOuterId 作为 key（第299-302行，共4行）
```kotlin
// 改前
var areaImageUrl by remember { mutableStateOf<String?>(null) }
var boxImageUrl by remember { mutableStateOf<String?>(null) }
var areaThumbUrl by remember { mutableStateOf<String?>(null) }
var boxThumbUrl by remember { mutableStateOf<String?>(null) }

// 改后
var areaImageUrl by remember(item.skuOuterId) { mutableStateOf<String?>(null) }
var boxImageUrl by remember(item.skuOuterId) { mutableStateOf<String?>(null) }
var areaThumbUrl by remember(item.skuOuterId) { mutableStateOf<String?>(null) }
var boxThumbUrl by remember(item.skuOuterId) { mutableStateOf<String?>(null) }
```

**原理**：key 变化后 LazyColumn 重建 composable，`remember` 添加 `item.skuOuterId` 作为 key 可保持 state 不丢失，缩略图不闪烁。

### 影响范围

- 完成商品：key 变化 → 消失于顶部、出现于底部，视口不动
- 恢复商品：key 变回 `"0_xxx"` → 同理
- 添加商品：不变，`animateScrollToItem(0)` 正常工作
- 缩略图：完成/恢复时不闪烁

### 验证

1. 完成顶部第一个商品 → 它「消失」出现在底部，下一个未完成在顶部
2. 完成中间商品 → 视口不动
3. 恢复已完成 → 回到顶部位置
4. 缩略图不闪烁
