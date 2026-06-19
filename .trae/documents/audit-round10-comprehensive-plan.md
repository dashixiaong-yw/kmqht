# 第十次全面审计计划：v1.21 ~ v1.51 代码级验证

## 审计范围

| 维度 | 内容 |
|:-----|:------|
| 版本范围 | v1.21 ~ v1.51（CHANGELOG最后30个版本） |
| 审计深度 | 代码级验证 — 逐项确认每次更新的修改在源码中正确实现 |
| 覆盖文件 | 约 60+ 文件（backend 20+ + Android 35+ + 配置文件 5+） |

## 任务划分（并行执行）

将 30 个版本的变更按功能域拆分，由 6 路并行代理独立审计：

### 代理 1：Backend API 路由审计

**覆盖版本**：v1.21, v1.23, v1.24, v1.27, v1.28, v1.29, v1.31, v1.32, v1.33, v1.34, v1.36, v1.38, v1.39, v1.41, v1.43, v1.45, v1.47, v1.48

**覆盖文件**：
- `backend/app/routers/orders.py` — complete_item/restore_item/complete_all/delete_item 参数校验、completed_count 加减、幂等检查
- `backend/app/routers/users.py` — 登录限流、mustChangePassword移除、admin禁用
- `backend/app/routers/admin.py` — XSS转义(escapeHtml/encodeURIComponent)、字段camelCase(isActive/createdAt)、APK上传校验
- `backend/app/routers/areas.py` — IntegrityError捕获、竞态修复
- `backend/app/routers/images.py` — 替换上传先写后删、速率限制、路径穿越防护
- `backend/app/routers/system.py` — downloadUrl路径、health添加totalOrders、凭证更新锁保护

**查询**：逐版本对照 CHANGELOG 检查每个修复在代码中是否已实现、实现是否正确。

### 代理 2：Backend 基础架构审计

**覆盖版本**：v1.21, v1.23, v1.25, v1.27, v1.30, v1.31, v1.32, v1.35, v1.37, v1.38, v1.39, v1.40, v1.46

**覆盖文件**：
- `backend/app/auth.py` — SKIP_AUTH_PREFIXES 完整性（/apk-download, /api/app-version, /admin精确匹配）、ApiKeyMiddleware逻辑、get_current_user API Key回退
- `backend/app/config.py` — nonlocal→global、watchfiles mtime过滤、kuaimai_config_lock、凭证原子写入、热重载
- `backend/app/main.py` — 静态文件挂载、定时任务scheduler、shutdown生命周区、asyncio.run
- `backend/app/database.py` — admin默认is_active=0
- `backend/app/services/kuaimai_api.py` — threading import、_get_client双检锁、_config_lock、wrapper_key解包、refresh_token

### 代理 3：Android 核心逻辑审计

**覆盖版本**：v1.21, v1.22, v1.24, v1.25, v1.27, v1.28, v1.31, v1.32, v1.34, v1.38, v1.39, v1.42, v1.43

**覆盖文件**：
- Repository: `PickOrderRepository.kt` — enqueueCompleteAll顺序、deleteItemWithQueue、payload转义、completeAllItemsDirect
- ViewModel: `PickDetailViewModel.kt` — getImageUrls拼接、在线/离线策略(isLoading)、suppliers初始值
- ViewModel: `SettingsViewModel.kt` — downloadJob取消、isDownloadingUpdate防重
- ViewModel: `ProductViewModel.kt` — loadImages收集泄漏、JSON转义
- DAO: `PickItemDao.kt`, `PickOrderDao.kt`, `ProductImageDao.kt` — 索引、事务
- Update: `AppUpdateManager.kt` — TOCTOU防护、下载缓存文件名
- Auth: `AuthRepository.kt`, `KuaimaiInterceptor.kt` — token加密存储、session持久化
- DI: `DatabaseModule.kt`, `NetworkModule.kt` — @Singleton、hilt-work移除

### 代理 4：Android UI 审计

**覆盖版本**：v1.24, v1.27, v1.28, v1.29, v1.30, v1.31, v1.32, v1.34, v1.35, v1.39, v1.43

**覆盖文件**：
- Screens: `PickDetailScreen.kt`, `PickListScreen.kt`, `HomeScreen.kt`, `SettingsScreen.kt`, `ProductScreen.kt`, `LoginScreen.kt`, `GuideScreen.kt`, `CameraScanScreen.kt`
- Components: `PickItemRow.kt`, `PickOrderCard.kt`, `NetworkStatusIndicator.kt`, `SupplierSelectDialog.kt`
- Navigation: `AppNavigation.kt`
- 检查项: maxLines溢出保护、触摸热区56dp、Alignment常量引用、padding统一、import清理、下拉刷新innerPadding、DisposableEffect释放、SoundPool释放

### 代理 5：构建配置 + 部署审计

**覆盖版本**：v1.22, v1.24, v1.25, v1.27, v1.32, v1.33, v1.34, v1.44, v1.49, v1.50, v1.51

**覆盖文件**：
- `app/build.gradle.kts` — versionCode/versionName、shrinkResources、outputFileName、签名配置
- `app/proguard-rules.pro` — Retrofit/Gson/Room/Hilt保留规则完整性
- `app/src/main/AndroidManifest.xml` — allowBackup、RECEIVER_EXPORTED
- `backend/Dockerfile` — EXPOSE/CORD、rust/cargo
- `backend/docker-compose.yml` — BUILD_VERSION、端口映射、volumes
- `docker-deploy/docker-compose.yaml` — 同上
- `scripts/sync-to-docker-deploy.ps1` — BUILD_VERSION校验、端口验证
- `.env.docker.example` — SERVER_URL/API_KEY/SERVER_PORT配置
- `gradle.properties` — 版本号

### 代理 6：APK 下载链路全链路审计（最近关键路径）

**覆盖版本**：v1.46, v1.47, v1.48, v1.49, v1.50, v1.51

**完整链路验证**：
1. upload → admin.py upload_app_version（文件保存 + 文件名）
2. publish → admin.py publish_app_version（元数据JSON）
3. query → system.py get_app_version（downloadUrl生成）
4. qrcode → system.py get_app_version_qrcode（二维码URL）
5. download → system.py download_apk（FileResponse Content-Type）
6. auth → auth.py SKIP_AUTH_PREFIXES（白名单覆盖）
7. static → main.py mount /apk（静态文件挂载）
8. server_url → .env.docker.example SERVER_URL
9. https → Android端兼容校验规则

**验证是否存在任何路径不匹配、任何白名单遗漏、任何404/401/zip问题残留。**

## 验证确认

1. 每个代理完成报告后汇总发现
2. 分类为新发现缺陷（P0/P1/P2）或 误报/已修复
3. 若有新缺陷 → 按项目流程修复（新版本号）
4. 若无新缺陷 → 更新知识图谱 + CHANGELOG记录本次审计

## 输出

- 审计报告文件：`.trae/documents/audit-round10-comprehensive.md`
- 缺陷清单（如有）
- 确认/修正后的代码文件
