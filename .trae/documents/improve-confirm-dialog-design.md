# 确认弹窗 UI 改进计划

## 一、当前状态分析

### 现有实现

确认弹窗位于 [ProductScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductScreen.kt#L660-L693) 的 `ConfirmDialog` composable，代码仅 34 行，使用最基础的 `material3.AlertDialog`：

```kotlin
androidx.compose.material3.AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(message) },
    confirmButton = {
        TextButton(onClick = onConfirm) { Text("确认", color = BrandBlue) }
    },
    dismissButton = {
        TextButton(onClick = onDismiss) { Text("取消") }
    }
)
```

### 问题点

| 问题 | 说明 |
|:-----|:------|
| **无圆角** | 默认 AlertDialog 直角，与 SupplierSelectDialog 的 16dp 圆角不一致 |
| **无图标** | 没有视觉区分备注视和供应商变更两种操作 |
| **无视觉层级** | 标题和内容文本平铺，没有主次之分 |
| **按钮样式平淡** | 仅用了 TextButton，没有品牌色强调，触控区域小（PDA 需要>=56dp） |
| **无内容高亮** | 供应商名/备注内容未突出显示，用户需要自己从文本中找 |
| **无背景色区分** | 整个弹窗纯白一片 |

---

## 二、改进目标

1. **匹配系统主色调**（BrandBlue `#2563EB`）
2. **符合 Material3 现代弹窗设计规范**
3. **两种操作类型做视觉区分**（备注用编辑图标蓝色、供应商用人员图标红色）
4. **增大 PDA 触控友好性**
5. **仅修改 ProductScreen.kt 一个文件**（ConfirmDialog 是私有函数）

---

## 三、设计规范参考

参考 Material3 AlertDialog 设计指南和主流移动端弹窗模式：

- **Shape**: `RoundedCornerShape(16.dp)` 统一圆角（与 SupplierSelectDialog 一致）
- **Container**: `surface` + `tonalElevation(8.dp)` 产生层次感
- **图标区**: 顶部居中放置圆形图标头像（带背景色）
  - 备注：`Icons.Default.Edit` + BrandBlue 背景
  - 供应商：`Icons.Default.Person` + `SupplierRed` 背景
- **标题**: 18sp Bold `TextPrimary`
- **内容区**: 14sp 普通文本，关键值（供应商名/备注内容）用 **Card + 浅底色** 高亮展示
- **确认按钮**: 使用品牌主操作色 `PrimaryLightBg` + `PrimaryLightText`，全宽填充
- **取消按钮**: 文字按钮，在下方的文本链接
- **布局**: Column 排列：图标 → 标题 → 内容 → 确认按钮 → 取消按钮

---

## 四、修改方案

### 修改文件

**仅修改 [ProductScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductScreen.kt)**

### 4.1 新增 import

```kotlin
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
// 实际已有上述两个 import，新增以下：
import androidx.compose.material.icons.filled.Person
// 注：Icons.Default.Edit 已导入，Icons.Default.Person 需新增
```

### 4.2 重写 ConfirmDialog composable

保留函数签名不变，内部完全重写：

```kotlin
@Composable
private fun ConfirmDialog(
    confirmType: ConfirmType,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
)
```

**新结构**：

```
AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(16.dp),
    containerColor = SurfaceWhite,
    tonalElevation = 8.dp,
    title = null,  // 自定义标题放在 text 区域统一管理
    text = {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 1. 图标头像
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(iconBackgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = SurfaceWhite,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. 标题
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3. 说明文本
            Text(
                text = message,
                fontSize = 14.sp,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4. 高亮内容卡片
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = highlightedBgColor
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = highlightedText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = highlightedTextColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5. 确认按钮（全宽品牌色）
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryLightBg,
                    contentColor = PrimaryLightText
                )
            ) {
                Text(
                    text = "确认修改",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 6. 取消按钮（文字）
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp)
            ) {
                Text("取消", color = TextSecondary, fontSize = 14.sp)
            }
        }
    },
    confirmButton = {},
    dismissButton = {}
)
```

### 4.3 两种操作类型的差异化配置

依据 `confirmType` 分支设定不同颜色和图标：

| 属性 | Remark 类型 | Supplier 类型 |
|:-----|:-----------|:-------------|
| icon | `Icons.Default.Edit` | `Icons.Default.Person` |
| iconBackgroundColor | `BrandBlue` | `SupplierRed` |
| highlightedBgColor | `PrimaryLightBg` (浅蓝) | `DangerBg` (浅红) |
| highlightedTextColor | `PrimaryLightText` (深蓝) | `SupplierRed` (红) |
| highlightedText | 备注内容 | 供应商名 + 编码 |

---

## 五、预期效果

### 备注确认弹窗（蓝色主调）

```
┌─────────────────────────────┐
│                             │
│         [ ✏️ 蓝底 ]         │  ← BrandBlue 圆底 Edit 图标
│                             │
│       确认保存备注            │  ← 18sp Bold 标题
│                             │
│    是否保存备注修改？         │  ← 14sp 灰色说明
│                             │
│  ┌─────────────────────────┐│
│  │  备注内容文字展示区域     ││  ← 浅蓝底卡片高亮
│  └─────────────────────────┘│
│                             │
│  ┌─────────────────────────┐│
│  │        确认修改          ││  ← PrimaryLightBg 品牌按钮
│  └─────────────────────────┘│
│                             │
│           取消               │  ← 灰色文字按钮
│                             │
└─────────────────────────────┘
```

### 供应商确认弹窗（红色主调）

```
┌─────────────────────────────┐
│                             │
│         [ 👤 红底 ]         │  ← SupplierRed 圆底 Person 图标
│                             │
│       确认切换供应商          │  ← 18sp Bold 标题
│                             │
│    是否将供应商切换为？       │  ← 14sp 灰色说明
│                             │
│  ┌─────────────────────────┐│
│  │       备货②              ││  ← 浅红底卡片高亮(SupplierRed字)
│  └─────────────────────────┘│
│                             │
│  ┌─────────────────────────┐│
│  │        确认修改          ││  ← PrimaryLightBg 品牌按钮
│  └─────────────────────────┘│
│                             │
│           取消               │  ← 灰色文字按钮
│                             │
└─────────────────────────────┘
```

---

## 六、修改范围

| 文件 | 修改内容 | 行数变化 |
|:-----|:---------|:--------|
| [ProductScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductScreen.kt) | 重写 `ConfirmDialog` composable（L660-693） | 约 34 行 → 约 100 行 |
| — | 新增 `Icons.Default.Person` import | 1 行 |
| **其他文件** | **无需修改** | — |

---

## 七、验证标准

1. ✅ 弹窗显示时圆角为 16dp，有阴影层次
2. ✅ 备注弹窗显示蓝色 Edit 图标、蓝色主题内容高亮
3. ✅ 供应商弹窗显示红色 Person 图标、红色主题内容高亮
4. ✅ 确认按钮使用品牌色 `PrimaryLightBg` + `PrimaryLightText`
5. ✅ 取消按钮为灰色文字按钮
6. ✅ 点击确认触发对应回调（confirmSaveRemark / confirmChangeSupplier）
7. ✅ 点击取消触发 dismissConfirmDialog
8. ✅ `./gradlew lint` 通过

---

## 八、决策记录

| 决策 | 选择 | 理由 |
|:-----|:-----|:------|
| 图标头像 | 带背景色的圆形 icon | 为操作类型提供强视觉锚点，PDA 扫描枪在远距离也能辨别操作类型 |
| 内容高亮 | 使用卡片+浅底色 | PDA 用户不需要从文字中寻找关键信息，直接高亮展示 |
| 按钮全宽 | 全宽品牌色按钮 | 增大触控区域（>=48dp），减少 PDA 误触 |
| 取消放在下方 | 文字按钮在确认按钮之下 | 符合"确认是主操作、取消是次操作"的视觉层次 |
| 仅改一个文件 | 不移到单独文件 | ConfirmDialog 是 ProductScreen 专有的私有 composable，不重复使用 |
