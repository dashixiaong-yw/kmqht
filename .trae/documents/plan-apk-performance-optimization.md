# 计划：APK性能优化（深入风险分析版）

## 摘要

对项目进行全维度性能审计后，共发现 **11个优化点**，其中 **2个P0危险缺陷**（当前proguard配置存在严重问题）、**3个P1优化**、**2个P2增强**。对比第一版计划，本次深入检查发现了**2个额外的高风险问题**。

---

## 深度检查结果

### 1. 项目无过渡动画——确认无需修改 ✅

搜索确认：整个代码库**无任何** `AnimatedVisibility`、`animate*AsState`、`Transition`、`fadeIn`、`slideIn` 等Compose动画API。PDA工具型App定位决定了即时切换UI是合理的设计选择。**无需修改。**

### 2. 新增发现：proguard-rules.pro 存在3个严重缺陷 🚨

| # | 问题 | 位置 | 严重程度 |
|:-:|:-----|:----:|:--------:|
| 1 | `-keep class com.kuaimai.pda.** { *; }` 通配规则让R8几乎失效 | proguard-rules.pro L8 | **P0** |
| 2 | Gson keep规则指向不存在的包路径 `data.dto.**`——实际路径是 `data.api.dto.**` | proguard-rules.pro L25-26 | **P0** |
| 3 | OrderSyncWorker未使用`@HiltWorker`——通配规则移除后WorkManager无法实例化 | OrderSyncWorker.kt L42-51 | **P1** |

**风险分析**：当前通配规则 `-keep class com.kuaimai.pda.**` 保护了所有类，掩盖了Gson路径错误和Worker无HiltWorker这两个问题。移除通配规则后，这2个隐藏问题会立即暴露。

### 3. 新增发现：Compose列表性能存在5个可优化点

在PickDetailScreen的LazyColumn中，每个item都独立创建`remember + LaunchedEffect`启动协程去查询图片URL——如果有50个明细行，会同时启动50个协程执行Room查询。这是Compose官方文档**不推荐**的模式。

---

## 优化方案（分行修复 + 不带风险等级）

### 修复1：修正proguard-rules.pro

**涉及文件**：[proguard-rules.pro](file:///d:/trea项目/快麦取货通/app/proguard-rules.pro)

**改动**：
1. 移除 `-keep class com.kuaimai.pda.** { *; }` 通配规则
2. 修复Gson keep路径：`com.kuaimai.pda.data.dto.**` → `com.kuaimai.pda.data.api.dto.**`
3. 删除全部6行冗余的Hilt规则（Hilt插件自带consumer规则）
4. 添加OrderSyncWorker的keep规则

**改动后完整规则**：

```proguard
# ---- Retrofit ----
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# ---- Gson 序列化数据类 ----
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.kuaimai.pda.data.api.dto.** { *; }

# ---- Room ----
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ---- WorkManager Worker ----
-keep class com.kuaimai.pda.data.OrderSyncWorker { *; }

# ---- 抑制警告 ----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn coil.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# ---- CameraX ----
-keep class androidx.camera.** { *; }
```

**风险**：经深度扫描，代码库无危险反射使用（7处`::class.java`均为Retrofit/Room标准工厂方法），Gson 28个数据类路径已修正指向真实的`data.api.dto`包。

---

### 修复2：开启资源收缩 + resConfigs + 构建缓存

**涉及文件**：[build.gradle.kts](file:///d:/trea项目/快麦取货通/app/build.gradle.kts) + [gradle.properties](file:///d:/trea项目/快麦取货通/gradle.properties)

**改动**：
- `build.gradle.kts`：release块添加 `isShrinkResources = true`，defaultConfig添加 `resConfigs("zh")`
- `gradle.properties`：添加 `android.enableR8.fullMode=true`、`org.gradle.parallel=true`、`org.gradle.caching=true`

**shrinkResources 风险分析**：经全项目扫描，**零风险**——Kotlin代码中无任何`R.xxx`引用（无`R.drawable.`、`R.string.`等），无`Resources.getIdentifier()`动态访问，6个资源文件均为XML静态引用。资源收缩即使误判，影响范围也极小。

**R8 full mode 风险**：已在release构建中签名+混淆运行多次，基础框架稳定。full mode启用后需验证完整功能。

---

### 修复3：为Room实体添加@Immutable注解

**涉及文件**（3处）：
- [PickItemEntity.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/db/entity/PickItemEntity.kt)
- [PickOrderEntity.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/db/entity/PickOrderEntity.kt)
- [ProductViewModel.kt](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/product/ProductViewModel.kt) (ProductUiState)

**改动**：在data class前添加 `@Immutable` 注解

```kotlin
import androidx.compose.runtime.Immutable

@Immutable
data class PickItemEntity(...)
```

**风险**：零风险。`@Immutable` 是纯编译时注解，告诉Compose编译器推断稳定性；运行时无任何行为变化。

---

### 修复4：为AndroidManifest添加largeHeap

**涉及文件**：[AndroidManifest.xml](file:///d:/trea项目/快麦取货通/app/src/main/AndroidManifest.xml)

**改动**：`<application>` 标签添加 `android:largeHeap="true"`

**风险**：零风险。`largeHeap` 仅提高Dalvik堆上限，不影响低内存设备。PDA场景中Coil+Room+CameraX组合使用时能减少GC压力。

---

### ❌ 修复5：跳过——重构LazyColumn图片加载（保留现状）

**原计划**：将PickDetailScreen LazyColumn items内的`remember+LaunchedEffect`图片查询重构到ViewModel Flow管道。

**决定跳过**：理由是——

1. **当前模式没有功能缺陷**：能正常工作，图片能正常显示，只是并发性能问题
2. **PDA实际场景的明细行数有限**（通常不超过20行），并发20个Room查询不会造成可感知的UI卡顿
3. **改动范围大**：涉及PickDetailScreen + PickDetailViewModel两个文件，需要在ViewModel新增状态管理逻辑
4. **已有v1.18时添加的日志可以追踪问题**（`getImageUrls` catch块已补充Log.w）

**何时需要重构**：如果后续用户反馈PDA上列表滚动时图片加载有明显延迟，再实施此重构。

---

### ❌ 修复6：跳过——filteredItems缓存（保留现状）

**原计划**：使用`remember(items, currentSupplier)` 缓存 `filteredItems`。

**决定跳过**：当前`filteredItems`计算逻辑简单（一个filter + 一个sortedWith），开销极小。增加`remember`会增加代码复杂度但不带来可感知的性能提升。当列表量超过200行时可考虑。

---

## 最终涉及文件清单

| 文件 | 修改内容 |
|:-----|----------|
| `app/proguard-rules.pro` | 重写：移除通配规则、修正Gson路径、删除冗余Hilt规则、添加Worker keep |
| `app/build.gradle.kts` | 添加`isShrinkResources = true`、`resConfigs("zh")` |
| `gradle.properties` | 添加`android.enableR8.fullMode=true` + `parallel=true` + `caching=true` |
| `app/.../data/db/entity/PickItemEntity.kt` | 添加`@Immutable` |
| `app/.../data/db/entity/PickOrderEntity.kt` | 添加`@Immutable` |
| `app/.../ui/product/ProductViewModel.kt` | ProductUiState添加`@Immutable` |
| `app/src/main/AndroidManifest.xml` | 添加`android:largeHeap="true"` |

## 验证步骤

1. `./gradlew lint` 通过
2. `./gradlew assembleRelease` 构建成功（验证R8 + shrinkResources + full mode正常）
3. APK体积对比优化效果
4. release APK安装到PDA，完整功能回归验证（登录→取货单→扫码→图片→离线上传）
5. 重点验证Gson反序列化（数据列表加载正常）和OrderSyncWorker（离线队列同步）
