# 快麦取货通 - UI 设计审核报告

> 审核依据：Anthropic Frontend Design 技能设计体系
> 审核范围：7 个页面 + 3 个主题文件 + 导航系统
> 目标风格：**工业实用（Industrial Utility）** — 功能优先、高对比、大触控、品牌统一

---

## 设计总览

| 维度 | 当前状态 | 评估 |
|:-----|:---------|:----:|
| 色彩系统 | 16 个命名常量，品牌蓝 `#2563EB` 贯穿 | ✅ 良好 |
| 排版系统 | 使用 MaterialTheme 默认排版，未定义字号阶梯 | ⚠️ 可改进 |
| 间距系统 | 使用 dp 硬编码值，无统一间距常量 | ⚠️ 可改进 |
| 弹窗系统 | 7 处 AlertDialog，风格不统一（圆角/阴影/按钮样式不一致） | ❌ 需改进 |
| 按钮样式 | 多处 TextButton 触控区偏小 | ❌ 需改进 |
| 动效系统 | 几乎无动效，页面切换无过渡 | ⚠️ 可改进 |
| PDA 适配 | 有屏幕常亮、大触控区设计 | ✅ 良好 |

---

## 一、全局性问题（影响所有页面）

### 1.1 AlertDialog 风格不统一

**现状**：App 中有 7 个 AlertDialog，使用 3 种不同风格：

| 对话框 | 位置 | 圆角 | 阴影 | 按钮风格 |
|:-------|:-----|:----:|:----:|:---------|
| 确认弹窗(备注/供应商) | ProductScreen | 16dp | 6dp | FilledButton + TextButton |
| 供应商选择弹窗 | SupplierSelectDialog | 16dp | - | TextButton |
| 删除确认(取货单) | PickListScreen | 默认 | - | DangerText FilledButton + TextButton |
| 删除确认(明细) | PickDetailScreen | 默认 | - | DangerText FilledButton + TextButton |
| 删除确认(图片) | ProductScreen | 默认 | - | DangerText FilledButton + TextButton |
| 退出登录 | SettingsScreen | 默认 | - | error色 FilledButton + TextButton |
| 检查更新(4种状态) | SettingsScreen | 默认 | - | TextButton × 多种 |
| 强制改密 | LoginScreen | 默认 | - | 无形状 Button |
| 图片预览 | PickDetailScreen | 默认 | - | TextButton |
| 会话过期 | AppNavigation | 默认 | - | TextButton × 2 |

**建议方案（任选其一）**：

| 方案 | 说明 | 复杂度 |
|:-----|:------|:------:|
| **A. 统一 Dialog 基座** | 创建 `KMDialog` composable 包裹 AlertDialog，统一 16dp 圆角 + 6dp 阴影 | 低 |
| **B. 独立提取组件** | 将删除确认弹窗提取为通用 `ConfirmDeleteDialog`，确认弹窗提取为 `ActionConfirmDialog` | 中 |
| **C. 分批优化** | 按使用频率优先优化高频弹窗（确认弹窗→删除弹窗→其他） | 低 |

### 1.2 按钮触控区一致性

**现状**：
- 多处 `TextButton` 使用默认 padding，触控区域偏小
- `FilterChip` 高度 28dp（PickDetailScreen）
- `TopAppBar` 中的 "+新建" 按钮自定义 `height=36dp`

**建议**：触控区域按实际使用场景调整，建议同类按钮保持大小一致：
1. `TextButton` 注意留够 `contentPadding`，不要挤在一起
2. `FilterChip` 当前 28dp 偏紧凑，建议适当增大到 32-36dp 方便点击
3. "+新建"按钮当前 36dp 可用，可保持

### 1.3 间距系统无统一常量

**现状**：各页面使用硬编码 dp 值（12.dp / 16.dp / 20.dp / 24.dp / 32.dp），无统一间距规范。

**建议**：定义 `AppSpacing` 对象统一管理间距（类似 AppAlignment）：

```kotlin
// 无需修改现有代码，仅作规范参考
object AppSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}
```

---

## 二、逐页审核

### 2.1 登录页（LoginScreen.kt）

**文件**：[LoginScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/login/LoginScreen.kt)

| 项目 | 当前 | 建议 | 复杂度 |
|:-----|:-----|:-----|:------:|
| 标题"用户登录" | `headlineMedium`, BrandBlue | 改为 22sp Bold 更显著 | ★☆☆ |
| 登录按钮 | `height(56.dp)` 正常 | — | — |
| 密码框 | 无右侧"显示/隐藏"图标 | 加 `trailingIcon` 切换密码可见性 | ★☆☆ |
| 强制改密 Dialog | 无圆角/无阴影 | 加 `shape = RoundedCornerShape(16.dp)` | ★☆☆ |
| 页面整体 | 居中布局，下方大块空白 | 改为上对齐：Logo在顶、表单在中、按钮在底 | ★★☆ |

### 2.2 主页（HomeScreen.kt）

**文件**：[HomeScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/home/HomeScreen.kt)

| 项目 | 当前 | 建议 | 复杂度 |
|:-----|:-----|:-----|:------:|
| 3个ModuleCard图标底色 | 3种不同底色 | ⚠️ 这不是问题，每个模块独立标识是有意设计 | — |
| 引导提示条 | WarningBg黄色底 + 关闭按钮 | **建议改为** `PrimaryLightBg` 蓝色调，与品牌色统一 | ★☆☆ |
| 会话警告条 | 与引导条相同黄色 | 保持黄色以与普通提示区分（警告语义） | — |
| ModuleCard | 12dp圆角 + 2dp阴影 + 1dp边框 | 移除阴影改为浅灰底，更干净；或保留但统一其他卡片 | ★☆☆ |
| 页面整体 | 顶部品牌区占用空间较多 | 减少上间距，让功能卡片更靠上 | ★☆☆ |

### 2.3 取货单列表（PickListScreen.kt）

**文件**：[PickListScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/picklist/PickListScreen.kt)

| 项目 | 当前 | 建议 | 复杂度 |
|:-----|:-----|:-----|:------:|
| "+新建" 按钮 | `height(36.dp)`, 自定义圆角8dp | 提升到 40dp，PDA触控区偏小 | ★☆☆ |
| 删除确认Dialog | 默认形状，无语义色条 | 加 `shape = RoundedCornerShape(16.dp)` | ★☆☆ |
| NewOrderDialog** | 拣货区按钮使用 `Button` + PrimaryLightBg | 按钮样式与"确认修改"按钮一致化 | ★☆☆ |
| NewOrderDialog** | 无标题装饰 | 加 16dp 圆角 | ★☆☆ |
| 已完成入口按钮 | 底部 `TextButton` 全宽 | 改为 Card 样式，与列表风格一致 | ★★☆ |
| PickOrderCard 长按菜单 | 系统默认 DropdownMenu | 选项间距24dp，字号加大 | ★☆☆ |

### 2.4 取货单详情（PickDetailScreen.kt）

**文件**：[PickDetailScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailScreen.kt)

| 项目 | 当前 | 建议 | 复杂度 |
|:-----|:-----|:-----|:------:|
| 扫码输入框 | `2.dp BrandBlue` 边框 + 8dp圆角 | 10dp圆角与ProductScreen统一 | ★☆☆ |
| FilterChip** | `height(28.dp)`, 13.sp, 选中色BrandBlue | 高度提升到 36dp，字号14sp | ★☆☆ |
| 删除确认Dialog | 默认形状 | 加 `shape = RoundedCornerShape(16.dp)` | ★☆☆ |
| 图片预览Dialog** | 默认形状 | 加 `shape = RoundedCornerShape(16.dp)` | ★☆☆ |
| 底部进度Card | 正常 | — | — |
| 全部完成按钮** | 与列表底部进度在同级 | — | — |

### 2.5 商品详情（ProductScreen.kt）

**文件**：[ProductScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductScreen.kt)

| 项目 | 当前 | 建议 | 复杂度 |
|:-----|:-----|:-----|:------:|
| ConfirmDialog** | ✅ 刚优化过，16dp圆角+阴影 | — | — |
| 图片删除Dialog** | 默认形状 | 加 `shape = RoundedCornerShape(16.dp)` | ★☆☆ |
| 扫码输入框 | 10dp圆角 | — | — |
| 商品卡片右侧文字 | `align(CenterVertically)` | — | — |
| ImageUploadSlot虚线边框 | `drawBehind` 自定义绘制 | 不影响功能，保留 | — |

### 2.6 设置页（SettingsScreen.kt）

**文件**：[SettingsScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/settings/SettingsScreen.kt)

| 项目 | 当前 | 建议 | 复杂度 |
|:-----|:-----|:-----|:------:|
| 退出登录Dialog | 默认形状 | 加 `shape = RoundedCornerShape(16.dp)` | ★☆☆ |
| 检查更新弹窗 × 4种 | 全部默认形状 | 加 `shape = RoundedCornerShape(16.dp)` | ★☆☆ |
| 扫码方式RadioButton | 默认排列，间距16dp | — | — |
| 退出按钮 | `height(56.dp)` 正常 | — | — |

### 2.7 引导页（GuideScreen.kt）

**文件**：[GuideScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/guide/GuideScreen.kt)

| 项目 | 当前 | 建议 | 复杂度 |
|:-----|:-----|:-----|:------:|
| 步骤切换 | 无动画，即时切换 | 加 `AnimatedContent` 淡入淡出效果 | ★★☆ |
| Step0 标题 | "第1步：服务器配置" + "服务器地址已自动配置" | 更清晰地说明：一步到位的配置信息 | ★☆☆ |
| Step2 标题图标 | 纯文字"设置完成！" | 加一个大对勾图标/动画表示完成 | ★★☆ |
| 按钮风格不一致 | Step1下一步=BrandBlue白字，Step0下一步=PrimaryLightBg深蓝字 | **建议统一为主操作色** PrimaryLightBg | ★☆☆ |
| Step1 扫码方式 | `RadioButton + Text` 组合 | 改为更大的触控行：整行可点击，加大字号 | ★☆☆ |

---

## 三、组件级别优化建议

### 3.1 PickOrderCard（取货单卡片）

**文件**：[PickOrderCard.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/components/PickOrderCard.kt)

| 项目 | 当前 | 建议 |
|:-----|:-----|:------|
| 订单号 | 18sp SemiBold | 加粗到 Bold，与标题一致性 |
| 状态徽章 | 圆角20dp | 可以直接引用 RoundedCornerShape(10.dp) 更统一 |
| 圆点进度条 | `Canvas` 绘制，最多20个圆点 | 改为进度条+数值展示，简化复杂度 |
| 长按菜单 | 默认 DropdownMenu | 改为底部 Sheet 风格，PDA 误触率更低 |

### 3.2 PickItemRow（取货明细行）

**文件**：[PickItemRow.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/components/PickItemRow.kt)

| 项目 | 当前 | 建议 |
|:-----|:-----|:------|
| 规格图容器 | 52dp × 52dp | 适合PDA，保持 |
| 右侧图片 | 44dp × 44dp | 略小，建议 48dp 对齐最小触控单位 |
| 完成按钮 | "✓ 完成" (绿色) / "↩ 恢复" (灰色) | 可以改为全宽按行，点击整个row切换状态 |
| 已完成透明度 | `Modifier.alpha(0.55f)` | 55%透明度辨识度偏低，建议 0.65f 或 0.7f |

### 3.3 ImageUploadSlot（图片上传槽位）

| 项目 | 当前 | 建议 |
|:-----|:-----|:------|
| 占位符号 | "+" 32sp + "标签" 12sp | 可以使用 Icons.Default.AddPhoto 图标替换 |
| 虚线边框 | `drawBehind` + `PathEffect.dashPathEffect` | 可保留，有辨识度 |

---

## 四、主题系统优化建议

### 4.1 增加暗色模式支持

**现状**：`Theme.kt` 中 `DarkColorScheme` 仅定义 8 个色值，覆盖不全。暗色模式下某些组件可能显示异常。

**建议**：补全 DarkColorScheme 的 20 个色槽位定义。

### 4.2 考虑添加排版系统常量

**现状**：各页面混用 `MaterialTheme.typography.*` 和直接写 `fontSize = xx.sp`。

**建议**：在 Theme.kt 中统一字号规范（非强制，仅建议）：

```kotlin
// 当前混用的格式：
Text(..., fontSize = 18.sp, fontWeight = FontWeight.Bold)  // ProductScreen
Text(..., style = MaterialTheme.typography.titleLarge)      // HomeScreen
```

> 注意：此改动涉及页面较多，建议仅在重写页面时一并处理。

---

## 五、优先级建议

基于 Anthropic Frontend Design 的"差异化识别 + 目的识别"原则，建议按以下优先级实施：

### P0（立即改善 — 影响面广、改动小）

| # | 改进项 | 涉及文件 | 预估行数 |
|:-:|:-------|:---------|:--------:|
| 1 | 统一所有 AlertDialog 加 16dp 圆角 | 7 处弹窗 | ~14 行 |
| 2 | 图片删除Dialog统一圆角 | ProductScreen | ~2 行 |

### P1（中等优先级 — 视觉一致性强）

| # | 改进项 | 涉及文件 | 预估行数 |
|:-:|:-------|:---------|:--------:|
| 3 | 引导页提示条改为品牌蓝 | HomeScreen | ~3 行 |
| 4 | FilterChip 高度提升到 32-36dp | PickDetailScreen | ~1 行 |
| 5 | 强制改密Dialog加圆角 | LoginScreen | ~2 行 |
| 6 | 退出登录Dialog加圆角 | SettingsScreen | ~2 行 |
| 7 | 检查更新弹窗统一圆角 | SettingsScreen | ~8 行 |
| 8 | NewOrderDialog 加圆角 | PickListScreen | ~2 行 |
| 9 | 密码框加显示/隐藏切换 | LoginScreen | ~5 行 |

### P2（低优先级 — 设计提升）

| # | 改进项 | 涉及文件 | 预估行数 |
|:-:|:-------|:---------|:--------:|
| 12 | 引导页步骤切换加 AnimatedContent | GuideScreen | ~10 行 |
| 13 | 设置完成页加大对勾图标 | GuideScreen | ~5 行 |
| 14 | 登录页改为上对齐布局 | LoginScreen | ~15 行 |
| 15 | 主页引导条改为品牌蓝 | HomeScreen | ~3 行 |
| 16 | PickItemRow 透明度调整 0.55→0.65 | PickItemRow | ~1 行 |
| 17 | 补全 DarkColorScheme | Theme.kt | ~15 行 |

---

## 六、不改动声明

本次审核**不会修改**以下内容：

- ❌ 所有 ViewModel 逻辑（登录/退出/创建删除/扫码/上传）
- ❌ 所有 Repository / DAO / API Service 数据层
- ❌ 所有 Room 实体定义
- ❌ 所有导航路由逻辑（AppNavigation.kt）
- ❌ 所有权限检查逻辑
- ❌ 所有扫描/相机功能
- ❌ 所有后台 Worker 同步逻辑

仅涉及属性调整（shape / color / elevation / fontSize / padding / height 等视觉属性）。

---

## 七、设计风格说明

审核基于 **工业实用（Industrial Utility）** 风格定位：

| 特征 | 设计体现 |
|:-----|:---------|
| 功能优先 | 不添加装饰性元素（图标底色、背景纹理等） |
| 高对比度 | 信息层级清晰，关键文本突出 |
| 品牌统一 | 所有弹窗 16dp 圆角，统一使用 BrandBlue 主色系 |
| 一致性 | 同类组件（AlertDialog/Button/Card）风格统一 |

---

## 八、参考设计资源

- [Material3 AlertDialog 指南](https://m3.material.io/components/dialogs/guidelines)
- [Material3 按钮触控区规范](https://m3.material.io/components/buttons/guidelines)
