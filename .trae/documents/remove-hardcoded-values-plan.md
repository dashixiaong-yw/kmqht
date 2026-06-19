# v1.62: 清理APP硬编码 — 安全性评估

---

## 逐项必要性+安全性评估

### 1. "全部"供应商筛选兜底 — ✅ 必须修复（同级Bug）

**位置**: [PickDetailViewModel.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt) L124

```kotlin
} catch (e: Exception) {
    _suppliers.value = listOf("全部")  // ← 与v1.61修复的"A区B区C区D区"完全同类
}
```

| 维度 | 评估 |
|------|------|
| 必要性 | **高** — 与刚修复的"A区B区C区D区"是同一类Bug：API异常时用假数据掩盖 |
| 安全性 | **高** — 改为`emptyList()+errorMessage`，复用已有Snackbar，无新代码路径 |
| 回归风险 | **无** — 网络正常时行为不变；网络异常时从"显示假数据"变为"显示错误提示" |

**结论：必须修复。**

---

### 2. "全部"字符串常量抽提 — ✅ 安全（纯重命名）

**涉及**: PickDetailViewModel L60/L64/L122 + PickDetailScreen L183

`"全部"` 在 3 处作为字面量出现。改为 `AppConstants.SUPPLIER_ALL_LABEL`。

| 维度 | 评估 |
|------|------|
| 必要性 | **中** — 非Bug，代码整洁性 |
| 安全性 | **高** — 编译期检查，值不变，无法引入运行时差异 |
| 回归风险 | **无** — 纯重命名 |

**结论：安全，可以做。**

---

### 3. "area"/"box" 图像类型提取常量 — ✅ 安全（纯重命名）

**涉及**: PickDetailViewModel L374-375、ProductViewModel L167-168/L357/L400、ProductScreen L198-206/L283

| 文件 | 使用场景 | 安全性 |
|------|------|:--:|
| PickDetailViewModel | `getImageBySkuAndType(skuOuterId, "area")` | 传参给Repository→SQL查询`WHERE image_type='area'`，值不变 |
| ProductViewModel | `images.find { it.imageType == "area" }` | 字符串比较，值不变 |
| ProductScreen | `pendingImageType = "area"` / 标签文本 | UI状态值+文本渲染，值不变 |

| 维度 | 评估 |
|------|------|
| 必要性 | **中** — 10+处重复，拼错`"are"`编译不报错 |
| 安全性 | **高** — 数据库`image_type`列约束为`CHECK(image_type IN ('area','box'))`，常量值与约束一致 |
| 回归风险 | **无** — 纯重命名 |

**结论：安全，可以做。**

---

### 4. 端口8900 UI文案 — ⚠️ 不可简单替换

**位置**: [SettingsScreen.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/settings/SettingsScreen.kt) L337、[AppNavigation.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/navigation/AppNavigation.kt) L219

```kotlin
"请在电脑浏览器访问: http://服务器地址:8900/admin"
"快麦API会话已过期，请在Web管理后台重新授权\n（浏览器访问 http://服务器地址:8900/admin）"
```

| 维度 | 评估 |
|------|------|
| 必要性 | **低** — 纯UI提示文案，端口变更概率极低 |
| 安全性 | **低** — 改为动态拼接需要传入SharedPreferences到这两个Composable，改接口签名→改调用链→引入回归风险 |
| 替代方案 | 改为模糊提示"请用电脑浏览器访问管理后台"去掉端口号，更简洁且不涉及动态读取 |

**结论：不修改。** 文案改"请用电脑浏览器访问管理后台"去掉端口，改动最小且零风险。

---

### 5. ImageUploadService API路径 — ⚠️ 不动更安全

**位置**: [ImageUploadService.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/api/ImageUploadService.kt) L84/L133/L156

```kotlin
val uploadUrl = "$serverUrl/api/upload"
val deleteUrl = "$serverUrl/api/images/$imageId"
val queryUrl = "$serverUrl/api/images/$skuOuterId"
```

| 维度 | 评估 |
|------|------|
| 必要性 | **低** — 3条路径仅在1个文件中，非散落在多处 |
| 安全性 | **低** — 提取常量后如果路径有变量拼接（`$imageId`）仍需字符串插值，反而增加常量理解难度 |
| 风险 | 过度提取常量会降低可读性（`"$BASE/$imageId"` vs 看代码就能读懂的完整路径） |

**结论：不修改。** 单文件内路径集中，改不改无实质收益。

---

## 最终修复范围（精简后）

| # | 改动 | 必要性 | 风险 |
|:-:|------|:--:|:--:|
| 1 | **PickDetailViewModel catch块** 移除假兜底 | 必须 | 无 |
| 2 | **"全部"字符串** → AppConstants常量 | 建议 | 无 |
| 3 | **"area"/"box"字符串** → AppConstants常量 | 建议 | 无 |
| 4 | **2处UI文案** 去掉端口号 "http://服务器地址:8900/admin" → "管理后台" | 可选 | 无 |
| ~ | ~~ImageUploadService路径~~ | ~~放弃~~ | — |

## 改动文件清单

| Step | 文件 | 改动 |
|:----:|------|------|
| 1 | AppConstants.kt | 新增`SUPPLIER_ALL_LABEL`、`IMAGE_TYPE_AREA`、`IMAGE_TYPE_BOX` |
| 2 | PickDetailViewModel.kt | catch `listOf("全部")`→`emptyList()`+errorMessage；area/box→常量；"全部"→常量 |
| 3 | PickDetailScreen.kt | `"全部"`→`AppConstants.SUPPLIER_ALL_LABEL` |
| 4 | ProductViewModel.kt | `"area"`/`"box"`→常量 |
| 5 | ProductScreen.kt | `"area"`/`"box"`→常量 |
| 6 | SettingsScreen.kt | 文案简化为"请用电脑浏览器访问管理后台" |
| 7 | AppNavigation.kt | 文案简化为"请用电脑浏览器访问管理后台" |
| 8 | 构建APK v1.62 | assembleRelease |
| 9 | 版本号 1.61→1.62 + sync + Git | |
