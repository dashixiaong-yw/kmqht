# 第七轮代码审查计划 - 流程规范确认与残留缺陷排查

## 审查范围
1. 重新确认项目文档流程规范变更
2. 全面审查代码残留缺陷

## 流程规范变更确认

项目规则 `.trae/rules/README.md` 已更新，主要变更：

| 变更项 | 旧流程 | 新流程 |
|--------|--------|--------|
| Step 3 | 验证代码: `./gradlew lint` | 验证代码: `./gradlew lint` 必须通过 |
| Step 4 | **新增** | 构建APK: `./gradlew assembleDebug` 构建成功，确认 `app-debug.apk` 产出 |
| Step 5 | 更新版本号 | 更新版本号(3处一致+验证) |
| Step 6 | 更新知识图谱 | 更新知识图谱 |
| Step 7 | 同步到docker-deploy | 同步到docker-deploy |
| Step 8 | Git提交推送 | Git提交推送 |

**关键变更**：新增Step 4"构建APK"，将lint和build分开为两个独立步骤，确保APK产出验证。

## 当前版本
- build.gradle.kts: versionName = "0.8", versionCode = 8
- gradle.properties: Version: 0.8
- CHANGELOG.md: 最新为0.8

---

## 代码审查结果

经过全面审查，代码质量已大幅改善，仅发现以下少量问题：

### BUG-83: 后端orders.py未使用的datetime导入（轻微）
- **文件**: `backend/app/routers/orders.py` 第6行
- **问题**: `from datetime import datetime, timedelta` — `datetime`未直接使用（所有时间操作都通过`beijing_now()`和`format_beijing()`），但`timedelta`仍在使用
- **修复**: 删除`datetime`，只保留`timedelta`

### BUG-84: 前端ProductViewModel loadImages空catch（中等）
- **文件**: `app/.../ui/product/ProductViewModel.kt` 第152行
- **问题**: `catch (_: Exception) { }` — 空catch块，违反规则"异步操作必须try-catch，禁止空catch"。虽然注释说"图片加载失败不阻塞主流程"，但应至少记录日志
- **修复**: 添加Log.w记录图片加载失败

### BUG-85: 前端OrderSyncWorker syncRemarkUpdate/syncSupplierUpdate payload缺少sys_sku_id/sys_item_id（严重）
- **文件**: `app/.../data/OrderSyncWorker.kt` 第109-139行
- **问题**: `syncRemarkUpdate()`从payload中提取`sys_sku_id`和`sys_item_id`，但`PickOrderRepository.updateRemarkWithQueue()`构建payload时只包含`remark`字段，不包含`sys_sku_id`和`sys_item_id`。同理`syncSupplierUpdate()`也缺少`sys_item_id`。这导致离线同步备注/供应商时`extractPayloadValue`返回null，同步操作直接返回false
- **修复**: 在`PickOrderRepository.updateRemarkWithQueue()`和`updateSupplierWithQueue()`的payload中添加`sys_sku_id`和`sys_item_id`字段

### BUG-86: 前端PickOrderRepository updateRemarkWithQueue/updateSupplierWithQueue payload不完整（严重）
- **文件**: `app/.../data/repository/PickOrderRepository.kt` 第118-146行
- **问题**: 与BUG-85关联。`updateRemarkWithQueue`的payload只有`{"remark":"xxx"}`，缺少`sys_sku_id`和`sys_item_id`。`updateSupplierWithQueue`的payload只有`{"supplier_name":"xxx","supplier_code":"xxx"}`，缺少`sys_item_id`
- **修复**: 从`currentItem`中读取`sysItemId`和`sysSkuId`，添加到payload中

---

## 修复优先级

### P0 - 严重Bug（必须修复）
1. **BUG-85/86**: 离线同步payload缺少关键字段，导致备注/供应商同步始终失败

### P1 - 中等问题（应该修复）
2. **BUG-84**: ProductViewModel空catch块

### P2 - 轻微问题（建议修复）
3. **BUG-83**: orders.py未使用的datetime导入

---

## 修复步骤

### Step 1: 修复P0严重Bug
1. BUG-86: PickOrderRepository.updateRemarkWithQueue() payload添加sys_sku_id和sys_item_id
2. BUG-86: PickOrderRepository.updateSupplierWithQueue() payload添加sys_item_id

### Step 2: 修复P1/P2问题
3. BUG-84: ProductViewModel loadImages添加日志
4. BUG-83: 删除orders.py未使用的datetime导入

### Step 3: 验证（按新流程规范）
- `./gradlew lint` (Step 3)
- `./gradlew assembleDebug` (Step 4)
- 版本号更新到0.9（3处一致+验证）(Step 5)
- CHANGELOG.md更新
- 知识图谱更新 (Step 6)
- 同步到docker-deploy (Step 7)
- Git提交推送 (Step 8)

---

## 假设与决策
1. payload中添加的sys_sku_id/sys_item_id从PickItemEntity中读取
2. ProductViewModel空catch改为Log.w（警告级别，不阻塞主流程）
3. 按新流程规范执行：lint → assembleDebug → 版本号 → 知识图谱 → docker-deploy → Git
