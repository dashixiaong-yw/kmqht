# HomeScreen 修复方案（修订版）

## 一、修复内容

1. **文字截断** → 内层 Column 添加 `verticalScroll`
2. **模块居中** → ModuleCard 移除 `fillMaxWidth`
3. **减少模块间隙** → 12dp → 8dp
4. **全页面滚动审计** → 确认其他页面

---

## 二、全页面垂直滚动审计

| 页面 | 当前状态 | 需修复？ |
|:-----|:--------:|:--------:|
| **HomeScreen** | ❌ 无 scroll，底部溢出裁剪 | **是** |
| SettingsScreen | ✅ `verticalScroll` | 否 |
| ProductScreen | ✅ `verticalScroll` | 否 |
| PickListScreen | ✅ `LazyColumn` | 否 |
| PickDetailScreen | ✅ header 固定 + `LazyColumn` 滚动 | 否 |
| LoginScreen | ❌ 无 scroll，但内容紧凑一般不会溢出 | 否（保持现状） |
| GuideScreen | ❌ 无 scroll，分步向导每步内容少 | 否（保持现状） |

**结论：仅 HomeScreen 需要修复滚动问题。**

---

## 三、修改清单

全部在 **[HomeScreen.kt](file:///d:/trea项目\快麦取货通/app/src/main/java/com/kuaimai/pda/ui/home/HomeScreen.kt)**，4 处改动：

### 改动1：内层 Column 添加 verticalScroll（解决文字截断）

**行号**：L140-L141

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

### 改动2：ModuleCard 移除 fillMaxWidth（解决居中问题）

**行号**：L323-L324

```kotlin
// 改前
Card(
    modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick),
    ...

// 改后
Card(
    modifier = Modifier
        .clickable(onClick = onClick),
    ...
```

### 改动3：减少模块间间距（12dp → 8dp）

**行号**：L252

```kotlin
// 改前
Spacer(modifier = Modifier.height(12.dp))

// 改后
Spacer(modifier = Modifier.height(8.dp))
```

**行号**：L269

```kotlin
// 改前
Spacer(modifier = Modifier.height(12.dp))

// 改后
Spacer(modifier = Modifier.height(8.dp))
```

---

## 四、安全性分析

| 检查项 | 结论 |
|--------|:----:|
| verticalScroll | 内容不溢出时无视觉变化；溢出时可滚动查看 |
| 移除 fillMaxWidth | Column 的 CenterHorizontally 自动居中 |
| 卡片点击热区 | 移除 fillMaxWidth 后卡片宽约 300dp，远大于 56dp 最小点击区域，不影响操作 |
| 间隙 12→8dp | 视觉更紧凑，不影响功能 |
| 回归风险 | **极低。** 仅删 2 个修饰符，改 2 个数值，不改逻辑 |

## 五、验证步骤

1. `./gradlew lint` 通过
2. `./gradlew assembleRelease` 构建成功
3. 安装到 PDA，确认 3 个模块卡片居中
4. 确认底部"设置"卡片文字完整显示
5. 确认页面内容超出时可上下滚动
