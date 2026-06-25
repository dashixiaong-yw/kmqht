# 浏览器下载APK失败修复方案

## 根因分析

用户测试：浏览器安全警告点"继续"→ 加入下载队列 → 提示下载失败

所有未点"分发"的 APK 都会在 `GET /api/app-version/download` 返回 404，浏览器显示"下载失败"。

**结论：`publishedAt` 缺失导致 404 + 中文文件名 Content-Disposition 编码问题，两个因素叠加。**

---

## 改动

### 文件：[system.py](file:///d:/trea项目/快麦取货通/backend/app/routers/system.py)

#### 改动 1：即使未分发，APK 文件存在时也允许下载

```python
@router.get("/api/app-version/download")
def download_apk(request: Request) -> FileResponse:
    """下载APK文件"""
    info = _load_version_info()
    # 【改前】必须 publishedAt 才能下载
    # if not info or not info.get("currentVersion") or not info.get("publishedAt"):
    # 【改后】APK 文件存在即可下载
    if not info or not info.get("currentVersion"):
        raise HTTPException(status_code=404, detail="暂无已分发的版本")

    file_name = info.get("apkFileName", "")
    if not file_name:
        raise HTTPException(status_code=404, detail="暂无已分发的版本")
    file_path = os.path.normpath(os.path.join(APK_DIR, file_name))
    apk_dir_norm = os.path.normpath(APK_DIR)
    if not file_path.startswith(apk_dir_norm):
        raise HTTPException(status_code=403, detail="非法文件名")
    if not os.path.exists(file_path):
        # ... 模糊匹配逻辑不变 ...
        raise HTTPException(status_code=404, detail="文件不存在")

    # 【新增】显式设置 Content-Disposition，中文文件名用 RFC 5987 编码
    safe_filename = f"kuaimai-{info.get('currentVersion', 'unknown')}.apk"
    encoded = urllib.parse.quote(safe_filename, safe='')
    headers = {
        "Content-Disposition": f"attachment; filename=\"{safe_filename}\"; filename*=UTF-8''{encoded}"
    }

    return FileResponse(
        file_path,
        media_type="application/vnd.android.package-archive",
        headers=headers,
    )
```

关键点：
- `safe_filename` 使用纯 ASCII 文件名 `kuaimai-{version}.apk`，避免中文编码问题
- `filename*` 提供 UTF-8 编码版本给支持 RFC 5987 的浏览器
- 不再依赖 `publishedAt` 字段，文件存在即可下载

#### 改动 2：新增 import

```python
import urllib.parse  # 文件顶部新增
```

---

## 改动清单

| 文件 | 改动 | 行数 |
|:-----|:-----|:----:|
| system.py | 新增 `import urllib.parse` | 1 行 |
| system.py | 下载端点移除 `publishedAt` 检查 | 1 行 |
| system.py | 下载端点使用 ASCII 文件名 + RFC 5987 Content-Disposition | 5 行 |

**不涉及 Android 端修改**，但按规则走完整版本号流程。

## 完整 7 步流程

| Step | 内容 |
|:----:|------|
| 1 | 查阅知识图谱 |
| 2 | 修改 system.py（后端） |
| 3 | 版本号 2.21→**2.22** |
| 4 | 构建 APK |
| 5 | 更新知识图谱 |
| 6 | 同步 docker-deploy |
| 7 | Git 提交 `v2.22: 修复浏览器下载APK失败（publishedAt检查 + Content-Disposition中文文件名）` |

> **注意**：后端修改生效需要重启容器 `docker-compose restart`，需用户手动执行。
