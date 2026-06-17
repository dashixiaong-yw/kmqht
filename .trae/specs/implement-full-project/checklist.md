# Checklist

## Phase 1 验证
- [ ] Android项目可编译通过（./gradlew assembleDebug）
- [ ] minSdk=24（API 24 Android 7.0）
- [ ] Hilt依赖注入正常工作
- [ ] FastAPI后端可启动（uvicorn main:app）
- [ ] API Key认证中间件生效（无Key返回401）
- [ ] 图片静态资源免认证（/images路径无需X-API-Key）
- [ ] 健康检查接口返回200（GET /health）
- [ ] SQLite连接池正常（PRAGMA WAL + busy_timeout）
- [ ] 快麦API签名拦截器正确计算HMAC-MD5
- [ ] 快麦API 7个接口定义完整（item.list.query/erp.item.sku.list.get/erp.item.supplier.list.get/supplier.list.query/erp.item.general.addorupdate/session.refresh）
- [ ] DTO映射层隔离快麦API字段名（mapper目录）
- [ ] EncryptedSharedPreferences加密存储3类敏感信息（API Key/服务器地址/快麦Session），扫码配置用普通DataStore
- [ ] AppAlignment.kt对齐常量文件已创建（RowBetween/RowCenter/ItemStart/ButtonEnd/ColumnCenter）
- [ ] App启动自动检测服务器连通性，无法连接时提示
- [ ] Docker镜像可构建并启动
- [ ] api.yaml编写完成，openapi-generator可生成前后端代码骨架
- [ ] api.yaml核心Schema定义完整（OrderResponse/AddItemRequest/ItemResponse/BaseResponse/UploadImageRequest）
- [ ] .env文件配置正确（仅API_KEY+SERVER_PORT，快麦凭证已分离到kuaimai.json），.dockerignore忽略.env*
- [ ] kuaimai.json配置正确（app_key/app_secret/session/updated_at），位于/data/kuaimai.json
- [ ] kuaimai.json热更新：修改文件后后端自动加载（watchfiles或定时轮询），无需重启容器
- [ ] App端API_KEY从EncryptedSharedPreferences读取，禁止硬编码

## Phase 2 验证
- [ ] PDA广播扫码可接收条码数据（4种设备配置表：iData/Urovo/新大陆/通用）
- [ ] PDA扫码300ms防抖生效
- [ ] Activity生命周期管理：onResume注册/onPause注销广播
- [ ] ML Kit摄像头扫码可正常工作
- [ ] 后端条码清洗逻辑正确（trim+控制字符+格式验证）
- [ ] ScannerManager防抖生效

## Phase 3 验证
- [ ] Room数据库表创建正确（含索引+约束+完整字段定义）
- [ ] 4个DAO创建完整（PickOrderDao/PickItemDao/ProductImageDao/PendingOperationDao）
- [ ] Room Migration策略已配置（禁止fallbackToDestructiveMigration）
- [ ] 取货单CRUD接口全部可用
- [ ] 单号生成规则正确：yyyyMMdd-拣货区X + 同拣货区第2单起（1）（2）递增
- [ ] 扫码添加待办行：App 1次请求→后端查快麦API+创建+缓存
- [ ] 扫码框行为：扫码后内容不消失，下次扫码直接替换
- [ ] 连续扫码模式：不自动标记完成，重复SKU不中断连续扫码
- [ ] 供应商筛选：Chip自动生成+去重+筛选正确
- [ ] 查看库区图/装箱图：取货单详情页点击按钮弹窗展示图片
- [ ] 重复扫码检测：中等振动+高亮提示
- [ ] 完成/恢复待办行：状态正确切换+自动排序
- [ ] 取货单自动完成检测：所有行完成后取货单自动标记完成，恢复一行后取货单恢复进行中
- [ ] 全部完成：批量SQL一次更新
- [ ] 12小时超时自动完成（前端不显示倒计时）
- [ ] 12小时超时验证：模拟时间流逝，确认超时自动完成逻辑正确
- [ ] 取货单删除：CASCADE级联删除待办行
- [ ] 已完成取货单查看（F21）："查看已完成"入口+最近7天列表
- [ ] 取货单排序（F24）：创建时间倒序+同拣货区相邻
- [ ] 长按操作（F25）：待办行操作菜单+取货单删除选项
- [ ] 待办行不显示规格编码
- [ ] 拣货区管理接口可用
- [ ] 供应商列表接口可用（GET /api/orders/{id}/suppliers）
- [ ] sku_cache缓存层正常工作
- [ ] 并发冲突处理：幂等完成+409 Conflict+404处理
- [ ] 后端数据库约束完整：pick_orders(order_no UNIQUE/completed_at条件/total_count>=0/completed_count>=0/completion_type CHECK) + pick_items(UNIQUE(order_id,sku_outer_id)/CASCADE/completed_at条件) + product_images(UNIQUE(sku_outer_id,image_type)/file_path字段/CHECK image_type) + pick_areas(name UNIQUE)
- [ ] PickOrderEntity包含expireAt字段（过期时间=createdAt+12小时）
- [ ] PickItemEntity完整字段（id/orderId/skuOuterId/sysItemId/sysSkuId/propertiesName/picPath/status/supplierName/supplierCode/remark/createdAt/completedAt）
- [ ] ProductImageEntity完整字段（id/skuOuterId/imageType/imageUrl/createdAt），窄表方案与后端一致
- [ ] UI符合设计规范（供应商20sp Bold #DC2626、按钮语义色、待办行72dp、F17触摸优化56dp）
- [ ] 主页设计：无底部导航Tab，三个模块入口卡片（取货列表+商品详情+设置）
- [ ] 多PDA共享架构：所有PDA访问同一后端，App端Room仅离线缓存

## Phase 4 验证
- [ ] 商品详情页信息展示正确（待办行不显示规格编码）
- [ ] 商品详情页扫码输入框用于快速切换查看其他SKU（扫码后替换当前页面内容）
- [ ] 供应商修改：选择→二次确认→回传快麦→立即更新本地+缓存失效
- [ ] 备注修改：编辑→二次确认→回传快麦→立即更新本地+缓存失效
- [ ] 备注同步验证：修改备注后在快麦ERP后台确认同步成功
- [ ] 快麦ERP字段同步策略：权威数据源为快麦API，已完成取货单字段不更新
- [ ] 图片上传：压缩至200KB+进度条+存储正确
- [ ] 图片上传策略：异步上传+失败重试3次+上传期间可继续操作
- [ ] 图片查询接口可用（GET /api/images/{skuOuterId}）
- [ ] 图片删除/替换功能正常（替换时先删旧图再传新图）
- [ ] product_images UNIQUE约束生效（同SKU同类型只有一条记录）
- [ ] 图片静态资源免认证（/images路径无需X-API-Key）

## Phase 5 验证
- [ ] 离线操作：断网→操作写入队列→网络恢复→WorkManager自动同步
- [ ] 离线同步按取货单分组并行
- [ ] 离线操作6种类型完整（ADD_ITEM/COMPLETE_ITEM/RESTORE_ITEM/COMPLETE_ALL/DELETE_ITEM/DELETE_ORDER）
- [ ] PendingOperationEntity完整字段（id/operationType/orderId/targetId/payload/createdAt/retryCount）
- [ ] 同步成功后立即从Room删除
- [ ] 同步失败重试3次→标记冲突→提示用户手动处理
- [ ] 网络状态指示：在线/离线/弱网 + "待同步"标记
- [ ] 扫码反馈：成功短振动+提示音 / 失败长振动+错误音 / 设置页可开关
- [ ] 屏幕常亮：详情页常亮，退出恢复
- [ ] 下拉刷新：并行请求，数据正确更新，清除缓存强制重新请求
- [ ] 首次使用引导（F23）：配置服务器地址→选择扫码方式→完成，引导后不再显示
- [ ] 冷启动优化（F32）：<2秒
- [ ] Token刷新失败（F35）：自动刷新→失败弹窗（不可关闭）→一键跳转（WebView或外部浏览器）→离线队列接管→新Token后自动重试失败请求
- [ ] F16会话过期预警（3-5天内自动刷新Token）
- [ ] 数据清理定时任务正常执行（已完成取货单/凌晨3:00 + sku_cache/每小时 + crash日志/凌晨4:00 + 孤立图片7天 + 商品图片文件清理：取货单删除时检查关联SKU引用）
- [ ] 12小时超时检查：后端每分钟检查
- [ ] crash-report接口可用（POST /api/crash-report + crash_logs表）
- [ ] ACRA集成正常（禁止第三方SaaS如Bugly/Crashlytics）
- [ ] ANR检测正常（主线程阻塞5秒以上记录日志）
- [ ] app-version接口可用（GET /api/app-version + APK分发 + REQUEST_INSTALL_PACKAGES权限）
- [ ] 快麦凭证热更新：kuaimai.json修改后后端自动加载
- [ ] App端数据策略：取货单Room缓存+操作队列、商品信息Room缓存24h、图片无需缓存
- [ ] OkHttp连接池+令牌桶限流正常工作（connectTimeout 10秒/readTimeout 15秒）
- [ ] Docker部署：sync脚本+docker-compose+健康检查
- [ ] Docker volume挂载整个./data目录（WAL模式-shm文件可正常写入）
- [ ] Tailscale组网：PDA通过Tailscale IP访问后端（WireGuard加密，HTTP无需额外HTTPS）
- [ ] 3处版本号一致（build.gradle.kts + CHANGELOG.md + gradle.properties）

## F37 Web管理后台验证
- [ ] /admin页面可正常访问，API Key认证机制生效（未认证时仅显示认证输入框）
- [ ] 仪表盘标签页：显示取货单数/用户数/拣货区数统计卡片 + 扫码配置二维码
- [ ] 用户管理标签页：用户列表展示+新增用户+编辑权限（5种权限代码）+启用/禁用+删除（二次确认）
- [ ] 拣货区管理标签页：拣货区列表+新增+删除（二次确认）
- [ ] 快麦配置标签页：凭证状态展示+刷新Session按钮+手动更新凭证表单
- [ ] 系统配置标签页：API Key（脱敏）+服务器地址（只读）展示
- [ ] 图片查看标签页：按SKU搜索查看库区图/装箱图（只读）

## F38 扫码配置验证
- [ ] /setup页面可正常访问，显示配置二维码（内容为kuaimai://setup?server=xxx&apikey=xxx）
- [ ] 二维码协议格式正确：kuaimai://setup?server=<URL>&apikey=<KEY>，兼容纯URL格式
- [ ] PDA端SetupQrParser正确解析二维码内容，GuideScreen和SettingsScreen扫码配置按钮可用

## App与Web权限分离验证
- [ ] SettingsScreen.kt仅保留个人设置（扫码方式/反馈开关/退出登录），无系统管理功能
- [ ] 权限边界清晰：Web后台负责系统管理（用户/拣货区/快麦凭证/服务器配置），App端负责日常业务（取货单/图片上传删除/供应商备注修改/扫码方式反馈开关）

## 扫码配置环境变量验证
- [ ] .env文件包含SERVER_URL配置（用于生成扫码配置二维码）
- [ ] .env.docker.example模板包含SERVER_URL示例（http://YOUR_NAS_IP:8900）

## 全局验证
- [ ] CSS变量→Compose Color映射完整（16个变量全部映射到Color.kt，禁止直接写色值）
- [ ] 9类组件规格映射正确（模块卡片/取货单卡片/扫码输入框/供应商Chip/待办行/商品详情/弹窗/Toast/网络状态条）
- [ ] 8种页面交互规格正确（页面切换/扫码聚焦/扫码替换/完成动画/供应商筛选/图片预览/新建取货单/设置管理）
- [ ] 原型与开发对照表8个页面/弹窗映射正确（HomeScreen/PickListScreen/PickDetailScreen/ProductScreen + 4个弹窗）
- [ ] 对齐方式统一：所有页面使用Modifier.align()+Arrangement，AppAlignment.kt常量引用
- [ ] 页面状态规范：Loading（LinearProgressIndicator）/ 空状态（纯文字提示）/ 错误（Snackbar红色）/ 离线（顶部薄条）/ 弱网（黄色薄条）
- [ ] 过渡与动效规范：页面切换Fade 200ms / 完成点击打勾动画300ms / 扫码成功Snackbar 1500ms / 扫码失败Snackbar 2000ms / 网络切换薄条300ms
- [ ] 间距系统：水平16dp / 组件间12dp / 卡片间8dp / 行间4dp / 内边距12dp / 卡片圆角12dp / 按钮圆角8dp
- [ ] 字体系统：页面标题20sp Bold / 供应商20sp Bold #DC2626 / 商品名18sp Medium / 单号16sp Regular / 按钮18sp Medium / 辅助14sp / 占位符16sp
- [ ] 触摸目标尺寸：按钮56dp×40dp / 图片上传位64dp×64dp / 待办行72dp高 / 筛选Chip 40dp高
- [ ] Room批量写入事务（insertAll + syncOrderWithItems）
- [ ] SQLite并发说明：3-5台PDA WAL模式足够，>10台需升级PostgreSQL
