# 快麦API全量测试计划

## 一、系统快麦API全景

经过三路并行探索，全系统共涉及 **8 个独立快麦 API method** + **5 个后端中转端点** + **6 个已有测试脚本**。

### 1.1 快麦 API method（直接调用）

| # | API method | 调用方 | 编码 | 读写 | 测试风险 |
|---|-----------|--------|:--:|:--:|------|
| A1 | `erp.item.single.sku.get` | 后端 `get_sku_by_outer_id` | form-urlencoded | 读 | 关键：SKU详情源数据 |
| A2 | `item.supplier.list.get` | 后端 `get_sku_by_outer_id`（条件） | form-urlencoded | 读 | 关键：供应商关联信息 |
| A3 | `supplier.list.query` | 后端 `get_supplier_list` | multipart | 读 | 关键：供应商选择列表 |
| A4 | `open.token.refresh` | 后端 `refresh_session` | multipart | 读 | 关键：session有效性 |
| A5 | `item.single.get` | 后端 `system.get_sku_detail` | form-urlencoded | 读 | 关键：商品标题获取 |
| A6 | `erp.item.general.addorupdate` | Android Worker 备注更新 | form-urlencoded | **写** | ⚠️ 高风险：修改商品 |
| A7 | `erp.item.general.addorupdate` | Android Worker 供应商更新 | form-urlencoded | **写** | ⚠️ 高风险：修改商品 |
| A8 | `erp.item.single.sku.get` | Android Worker `getLatestTitle` | form-urlencoded | 读 | 关键：title实时获取 |

### 1.2 后端中转端点（Android → 后端 → 快麦）

| # | 端点 | 用途 |
|---|------|------|
| B1 | `GET /api/sku/{sku_outer_id}` | SKU详情：备注/供应商/标题 |
| B2 | `GET /api/kuaimai/suppliers` | 供应商列表 |
| B3 | `GET /api/kuaimai/session-status` | session剩余天数 |
| B4 | `POST /api/kuaimai/refresh-session` | 手动刷新session |
| B5 | `GET /api/kuaimai/credentials` | 登录后同步凭证 |

- **测试 SKU**：`B08-24`
- **凭证来源**：从 `docker-deploy/kuaimai.json` 读取生产凭证
- **写操作**：全部执行（T5/T6），回读验证后自动恢复原值

---

## 二、测试矩阵

### T0：前置检查 — 凭证连通性

**目的**：确认测试凭证有效，快麦API可达。

| 步骤 | 操作 | 验证点 |
|:--:|------|--------|
| T0.1 | 后端 `kuaimai.json` 是否存在且格式正确 | 文件存在，含 app_key/app_secret/session/refresh_token |
| T0.2 | 后端 `/api/kuaimai/session-status` | 返回 session 有效，剩余天数 > 0 |
| T0.3 | 测试脚本 `call_erp("erp.item.single.sku.get", ...)` | 返回有效 SKU 数据 |
| T0.4 | Android direct: `erp.item.single.sku.get` 调用 | 返回与 T0.3 相同数据 |

### T1：商品 SKU 详情获取（读操作，3 条路径）

**涉及 API**：A1 `erp.item.single.sku.get`, A2 `item.supplier.list.get`, B1 `/api/sku/{sku_outer_id}`

| 用例 | 操作 | 验证点 |
|:--:|------|--------|
| T1.1 | 后端直接：`get_sku_by_outer_id("B08-24")` | 返回含 sysSkuId/sysItemId/propertiesName/remark/supplierName/supplierCode |
| T1.2 | 后端中转：`GET /api/sku/B08-24` | 返回 SkuDetailResponse 格式，含 itemTitle |
| T1.3 | Android direct：`KuaimaiApiService.getSkuInfo()` | 返回含 itemOuterId/sysItemId |
| T1.4 | Android via backend：`SystemApiService.getSkuDetail("B08-24")` | 返回 supplierName/supplierCode/remark/propertiesName |
| T1.5 | hasSupplier==1 场景：后端自动调用 `item.supplier.list.get` | 返回 supplier_name/supplier_code 非空 |

### T2：商品标题获取（读操作，验证不覆盖）

**涉及 API**：A5 `item.single.get`, A8（Worker 内部调用）

| 用例 | 操作 | 验证点 |
|:--:|------|--------|
| T2.1 | `item.single.get` 用 outerId="B-08" | 返回真实标题（非 "." 非 "-" 非空串） |
| T2.2 | `get_sku_by_outer_id` + `item.single.get` 完整链路 | 先查 SKU 获取 itemOuterId，再查商品获取 title |
| T2.3 | 标题为 `"."` 或 `"-"` 的商品数量 | **必须为 0**（之前被污染的已全部修复） |

### T3：供应商列表获取（读操作，2 条路径）

**涉及 API**：A3 `supplier.list.query`, B2 `/api/kuaimai/suppliers`

| 用例 | 操作 | 验证点 |
|:--:|------|--------|
| T3.1 | 后端直接：`get_supplier_list()` | 返回 list 长度 > 0，含 code/name/id |
| T3.2 | 后端中转：`GET /api/kuaimai/suppliers` | 返回 KuaimaiSuppliersResponse 格式 |
| T3.3 | multipart 扁平响应正确解析 | 供应商列表非空（v1.77 修复 `{}`→`result` 后） |
| T3.4 | `supplier.list.query` 返回 total 字段 | total 与 list 长度一致 |

### T4：Session 刷新（读操作）

**涉及 API**：A4 `open.token.refresh`, B3/B4/B5

| 用例 | 操作 | 验证点 |
|:--:|------|--------|
| T4.1 | 后端直接：`refresh_session()` | 返回 True |
| T4.2 | 后端中转：`POST /api/kuaimai/refresh-session` | 返回 success: true |
| T4.3 | `GET /api/kuaimai/session-status` | 剩余天数 > 0 |
| T4.4 | Android：settings 页面显示"快麦已连接" | 非"已过期"状态 |

### T5：备注更新（写操作 ⚠️ 高风险）

**涉及 API**：A6 `erp.item.general.addorupdate`（备注更新）

| 用例 | 操作 | 验证点 |
|:--:|------|--------|
| T5.1 | 测试脚本模拟：更新备注为时间戳 | success: true, code: 0 |
| T5.2 | 回读 SKU 确认备注已更新 | skuRemark == 刚设的时间戳 |
| T5.3 | **验证商品标题未被修改** | title == 原始标题（非时间戳/非 propertiesName/非 "."） |
| T5.4 | **验证 SKU 其他字段未被修改** | propertiesName/skuOuterId/skuId 与原值一致 |
| T5.5 | **验证供应商未被修改** | 原供应商列表不变 |
| T5.6 | 测试脚本模拟：v1.79 ItemUpdateWrapper 解包 | response.success = true |

### T6：供应商更新（写操作 ⚠️ 高风险）

**涉及 API**：A7 `erp.item.general.addorupdate`（供应商更新）

| 用例 | 操作 | 验证点 |
|:--:|------|--------|
| T6.1 | 查询当前供应商列表 | 记录原供应商 code + name |
| T6.2 | 模拟供应商更新（改供应商名+时间戳） | success: true, code: 0 |
| T6.3 | 回读 SKU 确认供应商已更新 | 新供应商名含时间戳 |
| T6.4 | **验证商品标题未被修改** | title == 原始标题 |
| T6.5 | **验证 SKU 备注未被修改** | remark == 原始备注 |
| T6.6 | **恢复原供应商名** | 回写原值后确认恢复 |
| T6.7 | 测试脚本模拟：v1.79 ItemUpdateWrapper 解包 | response.success = true |

### T7：Android 端到端验证

| 用例 | 操作 | 验证点 |
|:--:|------|--------|
| T7.1 | 扫码取货单中 SKU → 修改备注 → 保存 | 快麦 ERP 验证备注已更新 |
| T7.2 | 独立扫码 SKU → 修改备注 → 保存 | 离线队列入队 → Worker 同步 → 备注更新 |
| T7.3 | 点击"切换供应商" → 选择新供应商 | 快麦 ERP 验证供应商已更新 |
| T7.4 | 独立扫码修改供应商 | 离线队列入队 → Worker 同步 → 供应商更新 |
| T7.5 | 商品详情页加载 | 备注/供应商/规格信息正确显示 |
| T7.6 | 新建取货单 → 选择拣货区 | 拣货区列表正确加载（后端 areas 端点，非快麦） |

### T8：数据完整性验证（无意外修改）

| 验证项 | 验证方式 |
|--------|---------|
| 备注更新不修改标题 | T5 回读对比 |
| 备注更新不修改供应商 | T5 回读对比 |
| 供应商更新不修改标题 | T6 回读对比 |
| 供应商更新不修改备注 | T6 回读对比 |
| getLatestTitle 返回 null 时拒绝发送 | v1.78 保障，需模拟网络故障验证 |
| barcode/price/stock 永不被发送 | DTO 层面已确认不存在这些字段 |

---

## 三、可复用测试脚本

| 脚本 | 可复用 | 需更新 |
|------|:--:|------|
| `test_remark_update.py` | T5（备注更新+回读） | 增加 ItemUpdateWrapper 解包验证 |
| `test_supplier_permission.py` | T3（供应商列表）, T6（供应商更新+回读） | 供应商 ID `6520908` 需确认 |
| `test_find_title.py` | T2（标题查询+修复） | 字段名 `itemOuterId` vs `item_outer_id` |

### 需要新增的测试脚本

`test_kuaimai_full_integration.py` — 整合所有 T1-T8 测试，一次性运行，输出汇总报告。

---

## 四、测试执行计划

### 执行顺序（安全优先）

```
Phase 1: T0 凭证检查（5分钟）
Phase 2: T1 SKU详情 + T2 标题查询 + T3 供应商列表（纯读，15分钟）
Phase 3: T4 Session刷新（读操作，5分钟）
Phase 4: T5 备注更新 ⚠️（写操作，需回读验证，20分钟）
Phase 5: T6 供应商更新 ⚠️（写操作，需回读验证+恢复，25分钟）
Phase 6: T7 Android端到端（手动验证，30分钟）
Phase 7: T8 数据完整性汇总（5分钟）
```

**总计**：约 2 小时（含异常处理）

### T5/T6 高风险管理

1. 使用**用户指定的测试商品 B08-24**，避免影响其他数据
2. 每次写操作后**立刻回读**验证
3. T6 供应商更新后**自动恢复原值**
4. title 覆盖检查：每个写操作后验证 title == 原始值
5. **凭证从生产 kuaimai.json 读取**：脚本启动时自动加载 `docker-deploy/kuaimai.json`

---

## 五、新测试脚本

### `test_kuaimai_full.py` — 整合测试脚本（自动从 kuaimai.json 读凭证）

```python
"""
快麦API全量回归测试
测试 SKU: B08-24
凭证来源: docker-deploy/kuaimai.json
"""
import hashlib, json, httpx, os
from datetime import datetime

# 从生产配置读取凭证
def load_credentials():
    for path in ["docker-deploy/kuaimai.json", "backend/kuaimai.json", "kuaimai.json"]:
        if os.path.exists(path):
            with open(path) as f:
                cfg = json.load(f)
                return {
                    "app_key": cfg["app_key"],
                    "app_secret": cfg["app_secret"],
                    "session": cfg["session"],
                    "refresh_token": cfg.get("refresh_token", "")
                }
    raise FileNotFoundError("未找到 kuaimai.json")

CREDS = load_credentials()
APP_KEY = CREDS["app_key"]
APP_SECRET = CREDS["app_secret"]
SESSION = CREDS["session"]
API_BASE = "https://gw.superboss.cc/router"
TEST_SKU = "B08-24"

# ... 签名函数 + call_erp 复用 test_remark_update.py 逻辑 ...

# T0: 凭证检查
# T1: SKU详情 + item.single.get 标题
# T2: supplier.list.query
# T3: session刷新
# T4: 备注更新+回读+标题验证
# T5: 供应商更新+回读+标题验证+恢复
```

该脚本整合 T0-T6 全部读/写测试，输出 ✅/❌ 汇总报告。**写操作后自动验证标题未变、其他字段未变，供应商修改后自动恢复原值。**

---

## 六、验证成功标准

| 标准 | 门槛 |
|------|:--:|
| 所有读API成功返回数据 | 100% |
| 写API返回 success:true | 100% |
| 备注更新后标题不变 | 100% |
| 供应商更新后标题不变 | 100% |
| 供应商更新后备注不变 | 100% |
| 回读数据与写入一致 | 100% |
| v1.79 ItemUpdateWrapper 正确解包 | 100% |
| 无标题被覆盖为 "."/"-"/propertiesName | 100% |
| Android 端到端正常 | 100% |

---

## 六、风险评估

| 风险 | 等级 | 缓解措施 |
|------|:--:|------|
| 测试脚本使用生产凭证 | 中 | 使用测试商品，写操作后恢复 |
| `test_find_title.py` Step1 写入真实数据 | 低 | 写入的是修复操作（标题已正确），重复运行无害 |
| multipart vs form-urlencoded 编码差异 | 低 | T3/T4 已覆盖两种编码方式 |
| session 刷新影响并发请求 | 极低 | refresh_session 内部有锁保护 |
