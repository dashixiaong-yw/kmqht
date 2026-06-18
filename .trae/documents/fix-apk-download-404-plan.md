# APK 下载 404 修复计划

## 根因

v1.47 修复 APK 下载 Content-Type 时，存在 **3 个路径不匹配**：

```python
# system.py L101 - downloadUrl 生成路径
downloadUrl=f"{base_url}/apk-download/{fileName}"   # ← 指向 /apk-download/

# system.py L109 - 实际存在的路由
@router.get("/api/app-version/download")              # ← 实际路由是 /api/app-version/download

# main.py L102 - 静态文件挂载路径
app.mount("/apk", StaticFiles(...))                    # ← 静态挂载是 /apk/
```

扫码访问 `https://frp-off.com:64623/apk-download/xxx.apk` 时，**没有任何路由或静态挂载匹配这个路径**，返回 404。

FRP 穿透本身正常（管理后台能打开），问题纯粹是后端路由路径不一致。

## 方案

**最小改动**：将 downloadUrl / 二维码 URL 指向已有路由 `/api/app-version/download`。

这个路由不需要文件名参数——它内部调用 `_load_version_info()` 自动读取已发布的最新 APK 信息。

### 改动文件

#### [system.py:L101](file:///d:/trea项目/快麦取货通/backend/app/routers/system.py#L101)

```python
# 改前
downloadUrl=f"{base_url}/apk-download/{info.get('apkFileName', '')}",
# 改后
downloadUrl=f"{base_url}/api/app-version/download",
```

#### [system.py:L139](file:///d:/trea项目/快麦取货通/backend/app/routers/system.py#L139)

```python
# 改前
download_url = f"{base_url}/apk-download/{info.get('apkFileName', '')}"
# 改后
download_url = f"{base_url}/api/app-version/download"
```

#### auth.py (可能不需要改)

`/api/app-version/download` 已经在 `SKIP_AUTH_PREFIXES` 覆盖范围内（因前缀 `/api/app-version` 已存在），**不需要修改**。

## 验证

1. `python -c "from app.routers.system import router"` 导入检查
2. 本地启动后端：`uvicorn main:app --port 8900`
3. 访问 `http://localhost:8900/api/app-version/download` 应直接下载 APK
4. 检查响应头 `Content-Type: application/vnd.android.package-archive`
5. 检查响应头 `Content-Disposition: attachment; filename="快麦取货通-xxx.apk"`
