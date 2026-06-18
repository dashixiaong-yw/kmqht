# v1.28 最终四路并扫结果 — 剩余Bug清单与修复计划

> 四路并行扫描覆盖：后端FastAPI(17文件) + Android核心层(17文件) + Compose UI(21文件) + 安全/数据流/集成
> 基准版本：v1.28 (9564c1a — 全量25项修复)
> 扫描时间：2026-06-18

---

## ✅ v1.28已确认修复（本次不处理）

| 修复项 | 状态 | 已验证 |
|:-------|:----:|:-------|
| backend + docker-deploy docker-compose.yml `8900:8900` | ✅ | 端口映射正确 |
| AppUpdateManager `compareAndSet(false, true)` | ✅ | TOCTOU已修复 |
| config.py session/rf移入_config_lock | ✅ | 锁保护完整 |
| admin.py img.filePath仅保留 | ✅ | 无imageUrl后备 |
| system.py health_check + totalOrders查询 | **❌ 遗漏** | 需本次修复 |
| images.py len==0检查 + del空key | **❌ 回归** | del导致KeyError |
| users.py 过期记录清理 | ✅ | 正确 |
| system.py 307→302 | ✅ | 正确 |
| sync脚本端口验证 | ✅ | 已新增 |
| DatabaseModule 4个DAO @Singleton | ✅ | 正确 |
| ScannerManager asStateFlow + SoundPool release | ✅ | 正确 |
| ImageUploadService 删除prefs | ✅ | 正确 |
| PickDetailScreen Spacer width(4.dp) | ✅ | 正确 |
| PickItemRow height(72→80dp) | ✅ | 正确 |
| GuideScreen "配置已保存" color primary | ✅ | 正确 |
| ProductScreen uriToFile try-finally | ✅ | 正确 |

---

## 🔴 CRASH — 部署阻断/运行时崩溃（2项）

| # | 位置 | 行 | 问题 | 来源扫描 |
|:-:|:-----|:--:|:------|:--------:|
| **1** | `backend/app/routers/images.py` `_check_upload_rate` | L45-47 | **v1.28新增回归：del _upload_counts后读取KeyError**——首次上传时，空列表被del后L47还去检查len()，100%崩溃 | 后端扫描 |
| **2** | `app/src/main/java/.../KuaimaiInterceptor.kt` | L93-98 | **401重试时请求体已被消费**——extractBodyString调用body.writeTo(buffer)消耗body，TokenAuthenticator重试时body已空，重试请求只有公共参数无业务参数，快麦API返回签名失败 | Android核心扫描 |

---

## 🟠 HIGH — 功能严重缺陷（5项）

| # | 位置 | 行 | 问题 | 来源 |
|:-:|:-----|:--:|:------|:----:|
| **3** | `system.py` + `admin.py` L477 | L38-53 | **health_check未查询totalOrders**——模型有字段但端点未赋值；JS又用`total_orders`而非`totalOrders` → 仪表盘订单数始终0 | 后端+安全 |
| **4** | `AppNavigation.kt` 快麦session过期弹窗 | L220-232 | **弹窗无导航行为**——用户点"知道了"后停留在原页面，无"前往设置"按钮，与HomeScreen token过期弹窗行为不一致 | UI扫描 |
| **5** | `PickDetailScreen.kt` 明细列表图片 | L322-328 | **N+1数据库查询**——每个item独立LaunchedEffect查Room，50个item=100次DB查询。并发DB查询可能导致锁竞争UI卡顿 | UI扫描 |
| **6** | `PickDetailScreen.kt` 扫码输入键盘 | L238-245 | **输入后键盘未隐藏**——连续扫码模式下键盘遮挡底部进度条，`LocalSoftwareKeyboardController`未使用 | UI扫描 |
| **7** | `ProductScreen.kt` AsyncImage缺少contentScale | L568-574 | **默认ContentScale.Fit导致图片letterbox**——与PickItemRow的Crop不一致 | UI扫描 |

---

## 🟡 MEDIUM — 功能性缺陷（8项）

| # | 位置 | 行 | 问题 | 来源 |
|:-:|:-----|:--:|:------|:----:|
| 8 | `orders.py` restore/delete_item | L285, L407 | completed_count无WHERE completed_count>0保护→并发时可减到-1触发SQLite CHECK约束失败 | 后端扫描 |
| 9 | `config.py` 热重载task | L151-177 | loop.create_task未引用保存→异常退出不重启+关闭时无法cancel | 后端扫描 |
| 10 | 全局 AsyncImage | 多处 | 缺少error/placeholder占位图→网络失败时空白无反馈 | UI扫描 |
| 11 | `components/ImageUploadSection.kt` | 全文件 | 死代码——未被任何文件引用，ProductScreen另有独立实现 | UI扫描 |
| 12 | `PickDetailScreen.kt` PDA/键盘输入行为不一致 | L149, L239 | 非连续扫码模式下扫码后输入框不清空 | UI扫描 |
| 13 | `PickDetailScreen.kt` suppliers初始值 | L296-307 | `_suppliers`初始值为`emptyList()`→FlowRow短暂空白 | UI扫描 |
| 14 | `PickItemRow.kt` combinedClickable空onClick | L73-76 | Card级别空onClick浪费触摸事件处理 | UI扫描 |
| 15 | `admin.py` server_url未HTML转义 | L215/L259/L326 | 环境变量直接渲染到HTML→低概率XSS | 安全扫描 |

---

## 🔵 LOW — 轻微问题（3项）

| # | 位置 | 行 | 问题 | 来源 |
|:-:|:-----|:--:|:------|:----:|
| 16 | `HomeScreen.kt` padding(0.dp)冗余 | L331 | 无功能影响 | UI扫描 |
| 17 | `ProductScreen.kt` as? Activity静默失败 | L632 | 非Activity上下文时屏幕常亮失效无提示 | UI扫描 |
| 18 | `auth.py` 令牌UUID长度 | uuid4.hex | 当前正确（32字符hex），仅备注状态 | 安全扫描 |

---

## 建议本次修复优先级

### 🚨 P0 — 必须立即修复（2项）

| 顺序 | 修改内容 | 文件 | 估算 |
|:----:|----------|:-----|:----:|
| 1 | 删除del _upload_counts[user_id]（防止KeyError回归） | `images.py` L45-47 | 2秒 |
| 2 | health_check加totalOrders查询 + admin.js改用totalOrders | `system.py` + `admin.py` L477 | 3分钟 |

### ⚠️ P1 — 建议修复（5项）

| 顺序 | 修改内容 | 文件 | 估算 |
|:----:|----------|:-----|:----:|
| 3 | 快麦session过期弹窗添加"前往设置"按钮 | `AppNavigation.kt` L220-232 | 2分钟 |
| 4 | PickDetail图片查询改为批量+N+1优化 | `PickDetailViewModel.kt` + `Screen.kt` | 10分钟 |
| 5 | 扫码键盘添加`keyboardController?.hide()` | `PickDetailScreen.kt` L244 | 2秒 |
| 6 | AsyncImage添加contentScale=Crop | `ProductScreen.kt` L568-574 | 2秒 |
| 7 | orders.py completed_count添加WHERE>0 | `orders.py` L285, L407 | 2秒 |

### 📝 P2 — 可选修复（6项）

| 顺序 | 修改内容 | 文件 |
|:----:|----------|:-----|
| 8 | config.py热重载task生命周期管理 | `config.py` |
| 9 | 全局AsyncImage添加error/placeholder | 所有AsyncImage |
| 10 | 删除ImageUploadSection.kt死代码 | `components/` |
| 11 | admin.py server_url加html.escape | `admin.py` |
| 12 | PickItemRow combinedClickable重构 | `PickItemRow.kt` |
| 13 | suppliers初始值设为listOf("全部") | `PickDetailViewModel.kt` |

---

## 验证步骤

1. `cd backend; python -c "from app.main import app; print('OK')"` 无报错
2. `.\gradlew :app:compileReleaseKotlin` 编译通过
3. `.\gradlew assembleRelease` 完整构建成功
4. `.\scripts\sync-to-docker-deploy.ps1 -Force` 同步 + 端口验证
5. `git add -A && git commit -m "v1.29: CRASH回归修复+health仪表盘+UI/集成修复" && git push`
