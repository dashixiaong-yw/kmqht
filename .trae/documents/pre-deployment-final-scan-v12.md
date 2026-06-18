# 快麦API上线前最终审计修复 v1.36

## 🔴 P0 - 部署阻断（修复后必须先重启验证）

| # | 问题 | 文件 | 修复方案 | 风险 |
|:-:|------|------|---------|:----:|
| **1** | `kuaimai_config_lock` **未定义**——config.py L116/L137 使用但无定义，kuaimai_api.py L10 导入但不存在 | [config.py](file:///d:/trea项目/快麦取货通/backend/app/config.py) 检查API_KEY和SERVER_PORT之间 | 补充 `kuaimai_config_lock = threading.Lock()` | 服务器启动即 `NameError` |
| **2** | kuaimai_api.py 残留 `_config_lock` 旧锁(L18-L19) + `threading` 导入(L6) | [kuaimai_api.py](file:///d:/trea项目/快麦取货通/backend/app/services/kuaimai_api.py) | 删除旧锁定义+旧导入 | 两把锁并发脏读+死锁 |
| **3** | `refresh_session()` 用 `_config_lock` 而非 `kuaimai_config_lock` | [kuaimai_api.py](file:///d:/trea项目/快麦取货通/backend/app/services/kuaimai_api.py) L235 | 改为 `kuaimai_config_lock` | 锁嵌套顺序相反→死锁 |
| **4** | system.py 跨模块导入私有 `_config_lock` | [system.py](file:///d:/trea项目/快麦取货通/backend/app/routers/system.py) | 改为 `kuaimai_config_lock` | 死锁风险 |

## 🟡 P1 - 必须修复

| # | 问题 | 文件 | 修复方案 |
|:-:|------|------|---------|
| **5** | `get_supplier_list()` 响应解析缺少wrapper_key解包 | [kuaimai_api.py](file:///d:/trea项目/快麦取货通/backend/app/services/kuaimai_api.py) L178 | `result.get("supplier_list_query_response", {}).get("list", [])` |
| **6** | `hasSupplier` 整数比较不兼容字符串类型 | [kuaimai_api.py](file:///d:/trea项目/快麦取货通/backend/app/services/kuaimai_api.py) L143 | `str(sku_data.get("hasSupplier", "0")) == "1"` |
| **7** | `_call_api()` 错误信息丢失完整code/zh_desc | [kuaimai_api.py](file:///d:/trea项目/快麦取货通/backend/app/services/kuaimai_api.py) L86-L87 | 完整记录 `code={} msg={}` |
| **8** | `get_supplier_list` 无法区分"空数据"与"调用失败" | [system.py](file:///d:/trea项目/快麦取货通/backend/app/routers/system.py) L129 | 已修复为返回502 |

## 🟢 P2 - 建议修复

| # | 问题 | 文件 | 修复方案 |
|:-:|------|------|---------|
| **9** | `_call_api()` 每次创建新 httpx.AsyncClient 无连接池 | [kuaimai_api.py](file:///d:/trea项目/快麦取货通/backend/app/services/kuaimai_api.py) L78 | 提取为模块级client单例 |
| **10** | `get_sku_by_outer_id()` 两步查创建两个独立连接 | [kuaimai_api.py](file:///d:/trea项目/快麦取货通/backend/app/services/kuaimai_api.py) L78/L80 | 同上 |
| **11** | Sku缓存清理太激进（1小时清24h前） | [main.py](file:///d:/trea项目/快麦取货通/backend/main.py) | 改为每天清7天前 |

## 修复步骤

### Step 1: 读取config.py + kuaimai_api.py + system.py → 修复P0#1-4
### Step 2: 读取并修复P1#5-8
### Step 3: 读取并修复P2#9-11  
### Step 4: 全量API测试
```bash
python test_kuaimai_full.py
```
### Step 5: 构建验证
```bash
.\gradlew assembleRelease
```
### Step 6: 收尾
- 版本号 1.35 → 1.36
- 更新知识图谱
- sync-to-docker-deploy
- Git push
