# 第六次代码审计计划 — 最终收尾

> 日期：2026-06-17
> 背景：五次审计已修复59个问题，本次聚焦v1.17未彻底修复的回归Bug和新发现的代码质量问题

---

## 一、当前状态

| 审计轮次 | 修复数 | 状态 |
|:--------:|:------:|:----:|
| v1.13-v1.16 | 54 | ✅ 全部有效 |
| v1.17 | 5 | ❌ 1个未彻底修复（P1） |
| **本次（第六次）** | 待修复 | |

---

## 二、新发现的Bug

### B1（P1）：PickDetailViewModel.getImageUrls() serverUrl 为编译时常量

- **问题**：v1.17虽然添加了`$serverUrl`拼接逻辑，但使用了`AppConstants.DEFAULT_SERVER_URL`（空字符串）而非运行时从`encryptedPrefs`读取
- **影响**：用户配置服务器地址后，库区图/装箱图URL形如`/images/xxx.jpg`而非`http://192.168.1.100:8900/images/xxx.jpg`，图片永不可见
- **对比**：ProductViewModel和ImageUploadService都正确使用`prefs.getString(KEY_SERVER_URL, ...)`，仅PickDetailViewModel错了
- **文件**：[PickDetailViewModel.kt:L373](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt#L373)
- **修复**：注入`@Named("encrypted") SharedPreferences`，运行时读取KEY_SERVER_URL

### B2（P2）：OkHttp BODY级别日志泄露敏感信息

- **问题**：`HttpLoggingInterceptor.Level.BODY`会打印完整请求体，登录密码和用户Token在Logcat明文可见
- **文件**：[NetworkModule.kt:L103](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/di/NetworkModule.kt#L103)
- **修复**：改为`Level.HEADERS`（不打印Body）

### B3（P3）：ImageRepository.kt空catch块

- **问题**：`syncImagesFromBackend` catch空实现，异常完全静默
- **文件**：[ImageRepository.kt:L92](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/repository/ImageRepository.kt#L92)
- **修复**：添加Log.w日志

### B4（P3）：AuthRepository.kt 使用Log.w应改为Log.e

- **问题**：session刷新失败使用警告级别，应使用错误级别
- **文件**：[AuthRepository.kt:L60](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/data/repository/AuthRepository.kt#L60)
- **修复**：改为Log.e

### B5（P3）：PickListViewModel.kt catch块无日志

- **问题**：loadAreas的catch块直接fallback无日志，无法排查拣货区加载失败
- **文件**：[PickListViewModel.kt:L81](file:///d:/trea项目/快麦取货通/app/src/main/java/com/kuaimai/pda/ui/picklist/PickListViewModel.kt#L81)
- **修复**：添加Log.w日志

### 未使用的导入清理

- PickDetailScreen 1处（DangerBg）
- PickItemRow 3处（Arrangement, MaterialTheme, BorderGray）
- OrderSyncWorker 1处（AuthRepository死依赖）

---

## 三、验证标准

1. `./gradlew assembleDebug` 构建成功
2. 版本号三处一致（v1.18）
