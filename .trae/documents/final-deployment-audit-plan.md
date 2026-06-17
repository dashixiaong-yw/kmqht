# 部署前最终审计计划

> 日期：2026-06-17
> 背景：经过8次审计修复65+问题，本次为Docker部署前最后一轮检查

---

## 发现

### A（严重）：main.py 缺少 APK_DIR 导入

`backend/main.py` 第97-100行使用 `APK_DIR` 变量（用于挂载APK静态文件目录），但 `from app.config import (...)` 语句中未导入 `APK_DIR`。

- **影响**：服务启动时会抛出 `NameError: name 'APK_DIR' is not defined`，服务无法启动
- **文件**：[main.py:L14-L22](../../backend/main.py#L14-L22)
- **修复**：在 import 语句中添加 `APK_DIR`

### B（无影响）：AppDatabase.kt 注释过期

`version = 1` 注释与实际 `version = 2` 不一致，不影响功能

### C（无影响）：get_item_detail 死代码

`kuaimai_api.py` 中存在 `get_item_detail` 方法但未被任何代码调用

---

## 修复

仅1处修复：在 `main.py` 的 import 中添加 `APK_DIR`

---

## 验证

- `docker-compose up -d --build` 启动成功
