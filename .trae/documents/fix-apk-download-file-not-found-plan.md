# APK下载"文件不存在"深入诊断与修复 (v1.58)

## 确认的事实

- `apk_version.json` 存在且有 `currentVersion`/`publishedAt`/`apkFileName`（否则返回"暂无已分发的版本"）
- 但 `os.path.exists(file_path)` 返回 False
- 说明 JSON 元数据在，APK 文件不在磁盘上

## 逐一排查：3个可能根因

### 可能1：`latestVersion` 带空格导致文件名不一致 → 低概率

上传时 L42 `latestVersion.strip()` 只用于校验，L66 构造函数名用的是原始值。如果用户输入 `"1.57 "`，文件名为 `快麦取货通-1.57 .apk`（带空格）。下载和 JSON 引用使用同一变量，理论上一致，但**应该在保存前 strip**。

### 可能2：Docker volume 路径问题 → 中等概率

`docker-compose.yml` 用 `./data:/data` 相对路径。如果容器被从不同目录启动过，可能产生两个不同的 `data/` 目录，上传时写到一个，下载时读到另一个。

### 可能3：之前手动创建的 `apk_version.json` 残留在服务器 → 高概率

之前我们在本地 `docker-deploy/data/apk_version.json` 手动创建了 v1.50 的元数据。如果这个文件被部署到服务器但 APK 没有（APK 在本地构建的），用户上传新 APK 后**覆盖了** `apk_version.json`，但 FRP 大文件上传可能在某个环节失败（上传显示成功但文件未完整写入）。

## 修复（三层防御）

### 改动 1：upload_app_version() — strip 版本号 + 保存后立即验证

[admin.py](file:///d:/trea项目\快麦取货通/backend/app/routers/admin.py) L41-L82：

```python
# ① 先 strip
latestVersion = latestVersion.strip()
# ② 保存后验证
with open(apk_path, "wb") as f:
    f.write(content)
if not os.path.exists(apk_path) or os.path.getsize(apk_path) != len(content):
    logger.error(f"APK保存验证失败: path={apk_path} size={os.path.getsize(apk_path) if os.path.exists(apk_path) else 0} expected={len(content)}")
    raise HTTPException(status_code=500, detail="APK保存失败，请重试")
```

### 改动 2：download_apk() — 增加模糊匹配 + 诊断日志

[system.py](file:///d:/trea项目\快麦取货通/backend/app/routers/system.py) L110-L130：

```python
# 文件不存在时，搜索 APK_DIR 中任意 .apk 文件作为备选
if not os.path.exists(file_path):
    logger.warning(f"APK文件不存在: {file_path}，尝试模糊匹配")
    if os.path.exists(APK_DIR):
        candidates = sorted([f for f in os.listdir(APK_DIR) if f.endswith('.apk')], reverse=True)
        if candidates:
            file_path = os.path.join(APK_DIR, candidates[0])
            logger.info(f"模糊匹配到: {file_path}")
    if not os.path.exists(file_path):
        logger.error(f"APK_DIR={APK_DIR} 内容: {os.listdir(APK_DIR) if os.path.exists(APK_DIR) else 'NOT_EXIST'}")
        raise HTTPException(status_code=404, detail="文件不存在")
```

## 实现步骤

| Step | 操作 | 文件 |
|:----:|------|------|
| 1 | upload_app_version: strip + 保存验证 | admin.py |
| 2 | download_apk: 模糊匹配 + 诊断日志 | system.py |
| 3 | 版本号 1.57→1.58 (6处) | 全项目 |
| 4 | 知识图谱 + sync + Git | |

## 验证

部署容器重启后，重新上传 APK（管理后台 → APK管理 → 上传 → 分发），再访问 `https://frp-off.com:64623/api/app-version/download` 应返回 APK 文件流。
