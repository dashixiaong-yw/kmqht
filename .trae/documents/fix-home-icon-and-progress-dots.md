# 首页模块图标色 + 取货单进度圆点对齐修复

## 当前状态分析

### 问题 1：设置模块 icon 与底色无法分辨

**根因**：[HomeScreen.kt:L273-L278](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/home/HomeScreen.kt#L273-L278)

| 属性 | 当前值 | 色值 |
|------|--------|------|
| 卡片容器色 | `SurfaceWhite` | `#FFFFFF` |
| icon 背景盒色 | `SurfaceGray` | `#F3F4F6` |
| icon 着色 | `Color.White` | `#FFFFFF` |

`SurfaceGray` (#F3F4F6) 与 `SurfaceWhite` (#FFFFFF) 色差极小（仅低 2.2%），在 PDA 屏幕上肉眼几乎无法区分 icon 背景盒与周围卡片。白色 icon 覆在近乎白色的背景盒上，完全不可见。

**对比其他两个模块**（清晰可见）：
- 取货列表：`PrimaryLightBg` (#DBEAFE 浅蓝) + 白色 emoji
- 商品详情：`DangerBg` (#FEE2E2 浅红) + 白色 emoji

### 问题 2：取货单进度圆点未靠右

**根因**：[PickOrderCard.kt:L156-L184](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/components/PickOrderCard.kt#L156-L184)

进度文字"进度: 3/5"与进度圆点在同一 `Row` 中紧挨排列（只有一个 8dp Spacer），圆点紧贴文字右侧。当取货单卡片较宽时，圆点应该靠右对齐。

---

## 修复方案

### 修复 1：设置模块 icon 色

```kotlin
// 修改前
ModuleCard(
    ...
    iconBgColor = SurfaceGray,
    icon = {
        Icon(Icons.Default.Settings, tint = Color.White, ...)
    },
)

// 修改后
ModuleCard(
    ...
    iconBgColor = BorderGray,
    icon = {
        Icon(Icons.Default.Settings, tint = TextSecondary, ...)
    },
)
```

| 属性 | 修改前 | 修改后 |
|------|--------|--------|
| iconBgColor | `SurfaceGray` #F3F4F6 | `BorderGray` #E5E7EB |
| icon tint | `Color.White` | `TextSecondary` #6B7280 |

- `BorderGray` (#E5E7EB) 与 `SurfaceWhite` (#FFFFFF) 色差约 10%，肉片可见
- `TextSecondary` (#6B7280) 深灰 icon 在浅灰背景盒上清晰可辨
- 两个颜色都是已有色彩常量，无需新增

### 修复 2：取货单进度圆点靠右

```kotlin
// 修改前
Row(modifier = Modifier.fillMaxWidth(), ...) {
    Text("进度: ...")
    if (order.totalCount > 0) {
        Spacer(modifier = Modifier.width(8.dp))
        // ... 圆点 ...
    }
}

// 修改后
Row(modifier = Modifier.fillMaxWidth(), ...) {
    Text("进度: ...")
    if (order.totalCount > 0) {
        Spacer(modifier = Modifier.weight(1f))
        // ... 圆点 ...
    }
}
```

`Spacer(Modifier.width(8.dp))` → `Spacer(Modifier.weight(1f))`，将圆点推到 Row 右端。

---

## 修改清单

| # | 文件 | 改动 |
|---|------|------|
| 1 | `HomeScreen.kt` L273 | `iconBgColor` SurfaceGray → BorderGray |
| 2 | `HomeScreen.kt` L276 | icon `tint` Color.White → TextSecondary |
| 3 | `PickOrderCard.kt` L165 | `Spacer(Modifier.width(8.dp))` → `Spacer(Modifier.weight(1f))` |

---

## 验证步骤

1. `./gradlew lint` 通过
2. 首页设置模块 icon 背景盒在卡片上清晰可见，齿轮图标深色可辨
3. 取货单进度圆点靠卡片右侧对齐，与左侧进度文字有足够间距
