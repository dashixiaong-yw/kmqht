# HomeScreen 字段截断 + 模块居中 修复方案

## 一、问题

1. 底部"设置" ModuleCard 的 description "扫码方式、反馈开关" 只显示了一半
2. 3 个模块卡片（取货列表/商品详情/设置）居左显示，未居中对齐

## 二、根因

### 问题1：文字截断

内层 Column 缺少 `verticalScroll`，当屏幕高度不够时底部内容被裁剪：

文件：[HomeScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/home/HomeScreen.kt#L139-L143)

```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp, vertical = 16.dp),  // ← 没有 verticalScroll!
    horizontalAlignment = Alignment.CenterHorizontally
)
```

### 问题2：模块居左

`horizontalAlignment = Alignment.CenterHorizontally` 已设置于 Column，但 ModuleCard 使用了 `Modifier.fillMaxWidth()`，导致卡片填满整宽，视觉上从左边缘开始：

文件：[HomeScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/home/HomeScreen.kt#L322-L325)

```kotlin
Card(
    modifier = Modifier
        .fillMaxWidth()   // ← 撑满整宽，居中被废弃
        .clickable(...)
)
```

## 三、修复方案

### 修复1：内层 Column 添加 verticalScroll（改 1 行）

```kotlin
// 改前
Column(
    modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp, vertical = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally
)

// 改后
Column(
    modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally
)
```

### 修复2：ModuleCard 的 Card 移除 fillMaxWidth（改 1 行）

```kotlin
// 改前
Card(
    modifier = Modifier
        .fillMaxWidth()   // 撑满，无法居中
        .clickable(onClick = onClick),
    ...
)

// 改后
Card(
    modifier = Modifier
        .clickable(onClick = onClick),  // 移除 fillMaxWidth，由 Column 的 CenterHorizontally 居中
    ...
)
```

### 修复后效果示意

```
         ← 填充全宽 →
┌──────────────────────────────┐
│        快麦取货通              │  ← 居中
│       扫码取货·高效管理         │
│                              │
│   ┌────────────────────┐     │  ← 卡片居中
│   │ 📋 取货列表         │     │
│   │    查看和完成取货任务  │     │
│   └────────────────────┘     │
│                              │
│   ┌────────────────────┐     │
│   │ 🔍 商品详情         │     │
│   │    查看商品信息和图片  │     │
│   └────────────────────┘     │
│                              │
│   ┌────────────────────┐     │
│   │ ⚙ 设置             │     │
│   │    扫码方式、反馈开关  │     │  ← 完整显示（不截断）
│   └────────────────────┘     │
└──────────────────────────────┘
```

## 四、安全性验证

| 检查项 | 结论 |
|--------|:----:|
| verticalScroll 影响 | 内容不溢出时无视觉效果；溢出时可滚动查看完整内容 |
| Card 移除 fillMaxWidth | Column 的 CenterHorizontally 自动居中卡片，视觉效果更符合设计 |
| 卡片点击热区 | `filterMaxWidth` 移除后卡片仍有点击事件，热区为卡片内容区域，可能略小于之前的全屏宽。但 PDA 卡片内容宽约 300dp，远大于 56dp 最小点击区域，不影响操作 |
| 回归风险 | **极低。** 仅删除 2 个修饰符，不改逻辑。 |

## 五、修改清单

| # | 文件 | 行 | 改动 |
|:--:|:----|:--:|:-----|
| 1 | [HomeScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/home/HomeScreen.kt) | L140 | 添加 `.verticalScroll(rememberScrollState())` |
| 2 | [HomeScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/home/HomeScreen.kt) | L323-L324 | 删除 `Modifier.fillMaxWidth()` |

## 六、验证步骤

1. `./gradlew lint` 通过
2. `./gradlew assembleRelease` 构建成功
3. 安装到 PDA，确认 3 个模块卡片居中显示
4. 确认底部"设置"卡片的"扫码方式、反馈开关"完整显示
5. 如果页面内容超出屏幕高度，确认可以上下滚动
