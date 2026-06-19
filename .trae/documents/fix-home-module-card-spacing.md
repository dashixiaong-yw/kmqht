# HomeScreen 模块卡片间距 + Logo/图标尺寸 + PickList新建按钮 + PickOrderCard进度圆点 修复方案

## 一、原型 vs 当前差异分析

### 1.1 模块卡片 (.module-card)

| 属性 | 原型 CSS | 当前代码 | 问题 |
|:-----|:---------|:---------|:-----|
| 卡片 padding | **20px** 四边统一 | 无（靠子元素各自 padding） | 图标紧贴卡片边缘 |
| 图标框尺寸 | **52×52px** 固定 | 无固定尺寸（靠 padding 撑开） | 不同图标可能导致大小不一致 |
| 图标框圆角 | **14px** | **无圆角** | 图标框是方形 |
| 图标 | emoji 📋🔍⚙️ 24px | Material Icon 32dp | icon 背景色外无填充感、偏大 |
| 图标框-文字间距 | **16px** (gap) | **无** (Row 无 spacing) | 间距偏紧 |
| 标题色 | `#111827` 黑色 | `PrimaryLightText` 蓝色 | 颜色不对 |
| 卡片间距 | `gap: 14px` | `Spacer(height = 8.dp)` | 间距偏窄 |

### 1.2 Logo 图标框

| 属性 | 原型 | 当前 |
|:-----|:-----|:-----|
| 尺寸 | 56×56px | 56dp |
| 圆角 | 16px | 16dp |
| 图标字号 | 28px | 28sp |
| 需求 | — | 缩小（56→48dp） |

### 1.3 PickOrderCard 进度圆点

| | 当前 | 原型 |
|:-----|:-----|:-----|
| 行数 | 4行（进度圆点独立一行） | 2行（圆点与拣货区同行） |
| 问题 | 浪费垂直空间，卡片过高 | 紧凑布局 |

---

## 二、修复方案

### 文件1：[HomeScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/home/HomeScreen.kt)（5处修改）

#### 修改1：Logo 图标框缩小

```kotlin
// 改前
Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(PrimaryLightBg)) {
    Text("📦", fontSize = 28.sp)
}

// 改后
Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(PrimaryLightBg)) {
    Text("📦", fontSize = 24.sp)
}
```

#### 修改2：ModuleCard 重构（Card padding 20dp + 图标Box 52×52/圆角14 + gap 16dp）

```kotlin
// 改前
Card(...) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.background(iconBgColor).padding(horizontal = 16.dp, vertical = 20.dp)) { icon() }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 20.dp)) { ... }
    }
}

// 改后
Card(...) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(iconBgColor), contentAlignment = Alignment.Center) { icon() }
        Column(modifier = Modifier.weight(1f)) { ... }
    }
}
```

#### 修改3：图标改用 emoji 📋🔍⚙️

```kotlin
// 改前
icon = { Icon(Icons.Default.List, modifier = Modifier.size(32.dp), tint = BrandBlue) }
icon = { Icon(Icons.Default.Search, modifier = Modifier.size(32.dp), tint = BrandBlue) }
icon = { Icon(Icons.Default.Settings, modifier = Modifier.size(32.dp), tint = BrandBlue) }

// 改后
icon = { Text("📋", fontSize = 24.sp) }
icon = { Text("🔍", fontSize = 24.sp) }
icon = { Text("⚙️", fontSize = 24.sp) }
```

> 删 import：`Icons`、`Icons.filled.List`、`Icons.filled.Search`、`Icons.filled.Settings`

#### 修改4：标题颜色改为黑色 TextPrimary

```kotlin
// 改前
color = PrimaryLightText

// 改后
color = TextPrimary
```

> 新增 import：`import com.kuaimai.pda.ui.theme.TextPrimary`

#### 修改5：模块卡片间距 8dp → 14dp

三处 `Spacer(modifier = Modifier.height(8.dp))` → `14.dp`。

---

### 文件2：[PickListScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/picklist/PickListScreen.kt)（1处修改）

#### 修改6：新建按钮 "+ 新建"

```kotlin
// 改前
IconButton(onClick = { viewModel.showNewOrderDialog() }) {
    Icon(Icons.Default.Add, contentDescription = "新建", tint = SurfaceWhite)
}

// 改后（对齐原型 .btn-new: border-radius: var(--radius-btn)=8px）
TextButton(
    onClick = { viewModel.showNewOrderDialog() },
    shape = RoundedCornerShape(8.dp),                      // 6→8dp 对齐 --radius-btn
    colors = ButtonDefaults.textButtonColors(containerColor = PrimaryLightBg, contentColor = PrimaryLightText),
    modifier = Modifier.height(36.dp).defaultMinSize(minWidth = 56.dp)
) { Text("+ 新建", fontSize = 13.sp, fontWeight = FontWeight.Medium) }
```

> 新增 import：`defaultMinSize`、`PrimaryLightBg`、`PrimaryLightText`

---

### 文件3：[PickOrderCard.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/components/PickOrderCard.kt)（1处修改）

#### 修改7：进度圆点合并到"进度"同行 + 创建时间移到创建者同行

当前 4 行 → 改后 3 行。圆点从独立行移到"进度: X/Y"右侧，创建时间从进度行移到创建者同行右侧。

```
改前 (4行):                                   改后 (3行):
[单号                     公开 进行中]         [单号                     公开 进行中]
[创建者: zhangsan                    ]         [创建者: zhangsan        2026-06-14]
[进度: 3/5              2026-06-14  ]         [进度: 3/5 ●●●●●○○○                ]
[●●●●○○○○○                           ]
```

代码改动：L134-L195 区域，第二行创建者 Row 末尾加时间戳，第三行进度 Row 末尾加圆点。圆点 10dp→8dp（行内稍小）。

```kotlin
// 改前
// 第二行：创建者 (L135-144)
Row(modifier = Modifier.fillMaxWidth()) {
    Text("创建者: ${order.createdBy.take(12)}", ...)
}

// 第三行：进度 + 时间 (L148-166)
Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = AppAlignment.RowBetween) {
    Text("进度: ${order.completedCount}/${order.totalCount}", ...)
    Text(TimeUtils.formatTimestamp(order.createdAt), ...)
}

// 第四行：进度圆点 (L168-195)
if (order.totalCount > 0) { Spacer(6.dp); Row(...) { repeat... } }

// 改后
// 第二行：创建者 + 时间
Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text("创建者: ${order.createdBy.take(12)}", fontSize = 13.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    Text(TimeUtils.formatTimestamp(order.createdAt), fontSize = 12.sp, color = TextSecondary)
}

Spacer(modifier = Modifier.height(2.dp))

// 第三行：进度 + 圆点
Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text("进度: ${order.completedCount}/${order.totalCount}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (...) SuccessText else TextSecondary)
    if (order.totalCount > 0) {
        Spacer(modifier = Modifier.width(8.dp))
        val maxDots = minOf(order.totalCount, 20)
        repeat(maxDots) { index ->
            Box(modifier = Modifier.size(8.dp).background(color = if (index < order.completedCount) SuccessText else BorderGray, shape = CircleShape))
            if (index < maxDots - 1 || order.totalCount > 20) Spacer(width = 3.dp)
        }
        if (order.totalCount > 20) Text("...", fontSize = 8.sp, color = TextSecondary)
    }
}
```

---

### 文件4：[PickItemRow.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/components/PickItemRow.kt)（2处修改）

#### 修改8：完成/恢复按钮缩小（对齐原型 .btn-complete）

原型按钮 `padding: 4px 14px, font-size: 13px`，高度由内容+padding 自然决定（约 24px），非常紧凑。当前 TextButton 有 `height(32.dp)` 且 Material3 默认最小 40dp，导致按钮过高。原型圆角 `--radius-btn: 8px`，当前 `RoundedCornerShape(6.dp)` 需同步修正。

```kotlin
// 改前
TextButton(
    onClick = onRestore,
    shape = RoundedCornerShape(6.dp),
    colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary),
    modifier = Modifier.height(32.dp).defaultMinSize(minWidth = 56.dp)
) { Text("恢复", fontSize = 13.sp, fontWeight = FontWeight.Medium) }

TextButton(
    onClick = onComplete,
    shape = RoundedCornerShape(6.dp),
    colors = ButtonDefaults.textButtonColors(containerColor = SuccessBg, contentColor = SuccessText),
    modifier = Modifier.height(32.dp).defaultMinSize(minWidth = 56.dp)
) { Text("完成", fontSize = 13.sp, fontWeight = FontWeight.Medium) }

// 改后（对齐原型 padding: 4px 14px, border-radius: 8px）
TextButton(
    onClick = onRestore,
    shape = RoundedCornerShape(8.dp),                      // 6→8dp 对齐 --radius-btn
    colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary),
    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
    modifier = Modifier.defaultMinSize(minWidth = 56.dp)
) { Text("恢复", fontSize = 13.sp, fontWeight = FontWeight.Medium) }

TextButton(
    onClick = onComplete,
    shape = RoundedCornerShape(8.dp),                      // 6→8dp 对齐 --radius-btn
    colors = ButtonDefaults.textButtonColors(containerColor = SuccessBg, contentColor = SuccessText),
    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
    modifier = Modifier.defaultMinSize(minWidth = 56.dp)
) { Text("完成", fontSize = 13.sp, fontWeight = FontWeight.Medium) }
```

> 新增 import：`import androidx.compose.foundation.layout.PaddingValues`

#### 修改9：规格图底部标签移除

删除规格图底部半透明遮罩和"规格图"文字，避免遮挡图片预览：

```kotlin
// 删除以下整个 Box（L101-116）
Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(18.dp)
        .background(SurfaceGray.copy(alpha = 0.8f))
        .align(Alignment.BottomCenter),
    contentAlignment = Alignment.Center
) {
    Text(text = "规格图", fontSize = 10.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
```

> 同时删除无图占位文字中的"规格图"字样，改为空占位：
> ```kotlin
> // 改前
> Text(text = "规格图", fontSize = 9.sp, color = TextMuted)
> // 改后（或直接不显示占位文字）
> // 保留 Box 占位，无文字
> ```

---

### 文件5：[ProductScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductScreen.kt)（3处修改）

#### 修改10：规格备注框 + 保存按钮合并为同一行

原型 `.remark-input-wrap { display: flex; gap: 8px }`，输入框和按钮同行。当前是 Column 垂直排列。

```kotlin
// 改前（RemarkSection 内 Column 中的 OutlinedTextField + Spacer + Button）
Text(title)
Spacer(8.dp)
OutlinedTextField(...)
Spacer(8.dp)
Button(...) { Text("保存备注") }

// 改后（Row: 输入框 flex + 按钮 nowrap）
Text(title)
Spacer(8.dp)
Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
    OutlinedTextField(
        value = remark,
        onValueChange = onRemarkChange,
        placeholder = { Text("输入备注信息") },
        modifier = Modifier.weight(1f),                     // flex: 1
        maxLines = 2,
        shape = RoundedCornerShape(8.dp)
    )
    Button(
        onClick = onSaveRemark,
        enabled = !isSaving,
        shape = RoundedCornerShape(8.dp),                  // 对齐 --radius-btn
        colors = ButtonDefaults.buttonColors(
            containerColor = PrimaryLightBg,
            contentColor = PrimaryLightText
        )
    ) {
        if (isSaving) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text("💾 保存")                                     // ← 新增 emoji
    }
}
```

#### 修改11：库区图/箱图上传模块整体缩小至少 1/3

原型 `aspect-ratio: 1; min-height: 120px`，每个槽约 120px 见方。当前 `aspectRatio(1f)` 无上限约束，PDA 390dp 宽度下两槽并排后每个约 175dp 正方形，比原型大 ~45%。

**核心改动**：在 `ImageUploadSlot` 的 Box 上加 `heightIn(max = 120.dp)`，使整个模块（含库区图/箱图两个槽位）整体缩小约 1/3。

```kotlin
// 改前（ImageUploadSlot Box，两槽并排约 175dp 正方形）
Box(
    modifier = modifier
        .aspectRatio(1f)
        .drawBehind { ... }
        ...
)

// 改后（加 maxHeight 约束，整体缩小至约 120dp）
Box(
    modifier = modifier
        .aspectRatio(1f)
        .heightIn(max = 120.dp)                 // ← 整体缩小 1/3
        .drawBehind { ... }
        ...
)
```

> 缩小的效果是：库区图和箱图两个并排的正方形槽位整体变小，从约 175dp → 约 120dp。
> 空状态"+"和标签文字自然会随模块缩小而比例协调，无需单独调整字号。
> 需新增 import：`import androidx.compose.foundation.layout.heightIn`

#### 修改12：供应商切换按钮加 emoji ✏️

```kotlin
// 改前
TextButton(onClick = onChangeSupplier) {
    Text("切换", fontSize = 14.sp, color = BrandBlue)
}

// 改后
TextButton(onClick = onChangeSupplier) {
    Text("✏️ 切换", fontSize = 14.sp, color = BrandBlue)
}
```

---

## 三、修改清单

| # | 文件 | 改动 |
|:--:|:-----|:-----|
| 1 | [HomeScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/home/HomeScreen.kt) | Logo: 56→48dp, 圆角16→14dp, 字号28→24sp |
| 2 | 同上 | ModuleCard 重构：Card padding 20dp + Box 52×52 圆角14dp + Row spacedBy 16dp |
| 3 | 同上 | 图标改用 emoji 📋🔍⚙️，删4个icon import |
| 4 | 同上 | 标题色 PrimaryLightText → TextPrimary，新增 import |
| 5 | 同上 | 卡片间距 Spacer 8dp → 14dp（3处） |
| 6 | [PickListScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/picklist/PickListScreen.kt) | 新建按钮：Icon(Add) → TextButton("+ 新建", shape=8dp) |
| 7 | [PickOrderCard.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/components/PickOrderCard.kt) | 4行→3行：创建时间移到创建者同行右侧 + 进度圆点合并到进度同行右侧 |
| 8 | [PickItemRow.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/components/PickItemRow.kt) | 完成/恢复按钮：contentPadding(4×14) + 圆角 RoundedCornerShape(6→8dp) 对齐 --radius-btn |
| 9 | 同上 | 规格图底部标签移除（半透明底 + "规格图"字样） |
| 10 | [ProductScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductScreen.kt) | RemarkSection：输入框+保存按钮同行 Row + 保存按钮加 shape(8dp) + emoji "💾 保存" |
| 11 | 同上 | ImageUploadSlot：Box 加 heightIn(max=120dp) 整体缩小约 1/3（两并排槽位从 175dp → 120dp） |
| 12 | 同上 | 供应商切换：TextButton("切换") → TextButton("✏️ 切换") |

## 四、验证步骤

1. `./gradlew lint` 通过
2. `./gradlew assembleRelease` 构建成功
3. 首页验证：Logo 48dp + 卡片 20dp 留白 + 圆角 14dp + emoji 图标 + 黑色标题 + 间距 14dp
4. 取货列表验证："+ 新建"文字按钮
5. 取货单卡片验证：创建时间在"创建者"同行右侧、进度圆点在"进度: X/Y"同行（3行紧凑布局）
6. 取货详情验证：完成/恢复按钮紧凑（padding 4px×14px 级别）、规格图底部无遮罩标签
7. 商品详情验证：备注框+保存按钮同行、"💾 保存"含 emoji、库区图/箱图两个槽位整体缩小约 1/3、"✏️ 切换"含 emoji
