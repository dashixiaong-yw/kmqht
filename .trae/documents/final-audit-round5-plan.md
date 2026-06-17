# 第五次（最终）代码审计计划

> 生成日期：2026-06-17
> 背景：四次审计已修复54个问题，本次为上线前最终验证，修复此前审计中标记但未执行的问题

---

## 一、当前状态总结

| 审计轮次 | 修复数 | 验证状态 |
|:--------:|:------:|:--------:|
| v1.13（首次审计） | 12 | ✅ 全部有效 |
| v1.14（部署审计） | 18 | ✅ 全部有效 |
| v1.15（第三次审计） | 16 | ✅ 全部有效 |
| v1.16（第四次审计） | 5 | ❌ 未执行（见下文） |
| **本次（第五次）** | 待修复 | 待执行 |

---

## 二、v1.16计划标记但未执行的问题（需本次修复）

### P1：功能缺陷

#### P1-1：PickDetailScreen中图片URL未拼接服务器地址

- **描述**：PickDetailViewModel.getImageUrls() 返回原始相对路径（如`/images/xxx.jpg`），但PickItemRow.kt中的AsyncImage未拼接服务器BaseURL。Coil无自定义配置导致相对路径无法解析。
- **对比**：ProductViewModel.loadImages()中会做`$serverUrl${it.imageUrl}`拼接，因此ProductScreen图片正常，只有PickDetailScreen的库区图/装箱图不显示。
- **文件**：PickDetailViewModel.kt:L376-L383 → PickItemRow.kt:L169-L176,L211-L218
- **修复**：在PickDetailViewModel.getImageUrls()内拼接`$serverUrl$imageUrl`

#### P1-2：401处理未扩展到其他Repository

- **描述**：handleAuthError仅覆盖UserRepository的5个方法。OrderApiService/AreaApiService/ImageRepository/PickOrderRepository的401没有触发loginRequired事件。
- **文件**：UserRepository.kt:L246-L253
- **修复**：在ImageRepository.syncImagesFromBackend()中添加401检测（catch中捕获HttpException），触发全局401处理

### P2：中级别缺陷

#### P2-1：触摸热区<56dp（7处）

- 修改以下位置：
  - HomeScreen.kt:L176 引导条关闭按钮：24dp→56dp（加padding）
  - HomeScreen.kt:L209 会话警告关闭按钮：24dp→56dp
  - PickItemRow.kt:L92 规格图：52dp→56dp
  - PickItemRow.kt:L163 库区图：40dp→56dp
  - PickItemRow.kt:L205 装箱图：40dp→56dp
  - PickItemRow.kt:L252 完成按钮：44dp height→56dp
  - SettingsScreen.kt:L112 返回按钮：默认48dp→更大（或加padding）

#### P2-2：未使用AppAlignment常量（12处）

- HomeScreen.kt(3处)、PickOrderCard.kt(3处)、NetworkStatusIndicator.kt(2处)、SettingsScreen.kt(4处)
- 将原生Alignment.*替换为AppAlignment.*

#### P2-3：HomeScreen引导条prefs=null问题

- HomeScreen.kt:L76-L78 `prefs?.getBoolean(KEY_GUIDE_SHOWN, false) == false` → 加null检测

### P2-4：completeAllItems catch全量入队

- PickDetailViewModel.kt:L233-L243 catch分支对每个item逐一入队，应只入队一条batch操作

---

## 三、验证标准

1. `./gradlew assembleDebug` 构建成功
2. `./scripts/sync-to-docker-deploy.ps1 -Force` 同步成功
3. 版本号三处一致（v1.17）
