# v1.25 最终全面扫描结果

> 三次并行扫描覆盖：后端FastAPI + Android核心层(45+文件) + Compose UI(17文件)
> 对比v1.23-v1.25已修项，列出**全部剩余未修复Bug**

---

## ✅ v1.23-v1.25已确认修复（本次不处理）

| 问题 | 版本 |
|:-----|:----:|
| save_kuaimai_config保存app_key/app_secret | v1.23 ✅ |
| offline image payload escapeJson | v1.23 ✅ |
| SettingsViewModel 先collect再download | v1.23 ✅ |
| 签名密码移至keystore.properties | v1.23 ✅ |
| images.py delete路径遍历防护 | v1.23 ✅ |
| orders.py已完成status=1校验 | v1.23 ✅ |
| completeAllItems原子操作 | v1.23 ✅ |
| sync脚本ErrorActionPreference=Stop | v1.23 ✅ |
| _config_lock定义 | v1.24 ✅ |
| PickListScreen嵌套Scaffold移除 | v1.24 ✅ |
| proguard sealed/enum规则 | v1.24 ✅ |
| ImageUploadService Thread.sleep→delay() | v1.24 ✅ |
| PickDetailScreen innerPadding传递 | v1.24 ✅ |
| getImageUrls serverUrl为空不拼接 | v1.24 ✅ |
| errorMessage计数器key | v1.24 ✅ |
| PickOrderCard进度点上限20个 | v1.24 ✅ |
| LoginScreen userId=0L检查 | v1.24 ✅ |
| GuideScreen改为"立即生效" | v1.24 ✅ |
| **admin.py Request导入** | v1.25 ✅ |
| **docker-compose.yml env_file改.env.docker.example** | v1.25 ✅ |
| **OrderSyncWorker改为App.OrderSyncWorkerDeps** | v1.25 ✅ |
| **images.py skuOuterId路径穿越防护** | v1.25 ✅ |
| **Dockerfile添加rust cargo + CMD支持SERVER_PORT** | v1.25 ✅ |
| **config.py load/save加_config_lock写保护** | v1.25 ✅ |
| **.env.docker.example添加API Key警告** | v1.25 ✅ |

---

## 🔴 CRASH级别（0项）

**无崩溃级Bug**。所有已知CRASH问题已在v1.23-v1.25中修复。

---

## 🟠 HIGH级别（3项）

| # | 层 | 文件 | 行 | 问题 | 影响 |
|:-:|:--|:-----|:-:|------|------|
| 1 | **Compose** | `PickDetailScreen.kt` | L210-217 | **双重innerPadding**——PullToRefreshBox和Column都应用了`.padding(innerPadding)` | 内容偏移约128dp，"全部完成"按钮可能被推到屏幕外 |
| 2 | **App** | `NetworkMonitor.kt` | L65 | **`register()`从未被调用**——`NetworkMonitor.register()`设计为外部调用，但没有任何地方调用它 | `_networkStatus`始终为`Status.OFFLINE`，`NetworkStatusIndicator`始终显示"已离线" |
| 3 | **App** | `AppNavigation.kt` L157 | **HomeScreen未传递networkMonitor参数**——首页声明了`networkMonitor: NetworkMonitor? = null`但导航处直接`HomeScreen()`不传参 | networkMonitor始终为null，即使修复了自动注册也没用 |

---

## 🟡 MEDIUM级别（3项）

| # | 层 | 文件 | 行 | 问题 |
|:-:|:--|:-----|:-:|------|
| 4 | **Compose** | `PickListScreen.kt` L73-82 | **errorMessageToken机制失效**——LaunchedEffect key用errorMessageToken.value(初始0)，clearError后自增但LaunchedEffect不会因此重新触发 | Snackbar永不显示，用户看不到错误提示 |
| 5 | **后端** | `admin.py` | 配置页 | **admin页面SERVER_URL端口与实际部署端口不一致**——`.env.docker.example`中`SERVER_PORT=8900`，配置二维码和server_url可能使用默认宿主机端口，PDA扫码可能无法连接 |
| 6 | **后端** | `admin.py` | HTML渲染 | **admin页面可能存在XSS**——前端渲染的`server_url`或其他用户可控字段未经过滤直接嵌入HTML |

---

## 🔵 LOW级别（5项）

| # | 层 | 文件 | 行 | 问题 |
|:-:|:--|:-----|:-:|------|
| 7 | Compose | ProductScreen.kt L335 | SKU图片缺少圆角裁剪`clip(RoundedCornerShape(10.dp))` |
| 8 | Compose | PickDetailScreen.kt L163 | PDA扫码不清空scanInput输入框 |
| 9 | Compose | PickDetailScreen.kt L313 | LazyColumn `fillMaxSize()`与`weight(1f)`冗余 |
| 10 | App | ImageUploadService.kt L202 | `catch(IOException) { throw e }`是死代码，直接重抛无额外逻辑 |
| 11 | App | api/dto/ | SkuListResponse, ItemListResponse等DTO定义但无API使用 |

---

## 建议本次修复（共6项P0-P2）

| 顺序 | 修改内容 | 文件 | 估计 |
|:----:|---------|:-----|:-----|
| 1 | 移除Column上的`.padding(innerPadding)`，PullToRefreshBox已有 | PickDetailScreen.kt L217 | 2秒 |
| 2 | `LaunchedEffect(errorMessageToken.value)`改为`LaunchedEffect(errorMessage)` | PickListScreen.kt L76 | 2秒 |
| 3 | NetworkMonitor init块中调用`register()` | NetworkMonitor.kt | 5分钟 |
| 4 | AppNavigation中传递networkMonitor给HomeScreen | AppNavigation.kt L157 | 2分钟 |
| 5 | 移除冗余`catch(IOException) { throw e }` | ImageUploadService.kt L202-203 | 2秒 |
| 6 | SKU图片添加圆角裁剪 | ProductScreen.kt L335 | 2秒 |

## 验证步骤

1. `./gradlew lint` 通过
2. `./gradlew assembleRelease` 构建成功
3. 后端Python启动测试无报错
4. 前端手动验证：NetworkStatusIndicator正常显示网络状态
5. 前端手动验证：PickDetail "全部完成"按钮可见
6. 前端手动验证：PickList删除/创建取货单Snackbar正常弹出
