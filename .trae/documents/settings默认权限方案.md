# settings 设为默认权限

## 修改方案

**修改文件**：`backend/app/routers/users.py` — 在 `create_user` 函数中加 3 行

**修改位置**：L168-205，在权限校验之后、插入数据库之前

```python
# 修改前（L173-177）
def create_user(req, user):
    # 校验权限代码
    for perm in req.permissions:
        if perm not in VALID_PERMISSIONS:
            raise HTTPException(...)
    # ... 创建用户流程

# 修改后
def create_user(req, user):
    # 校验权限代码
    for perm in req.permissions:
        if perm not in VALID_PERMISSIONS:
            raise HTTPException(...)
    # 自动添加 settings 权限（默认必选）
    if "settings" not in req.permissions:
        req.permissions.insert(0, "settings")
    # ... 创建用户流程
```

## 不修改

| 文件 | 原因 |
|:----|:-----|
| `update_user` | 编辑已有用户时仍可手动管理权限，不自动补 |
| `database.py` | 默认管理员已包含 settings，无需改 |
| 管理后台 HTML | 权限复选框仍可正常显示和勾选 |
| Android 端代码 | 无需修改 |

## 验证

| 步 | 操作 | 预期 |
|:--:|:-----|:-----|
| 1 | 管理后台创建新用户，不勾选 settings | 用户创建成功，自动拥有 settings 权限 |
| 2 | PDA 用新账号登录 → 修改备注 | syncKuaimaiCredentials 成功，Worker 正常执行 |