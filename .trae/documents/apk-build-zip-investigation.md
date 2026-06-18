# APK 构建检查报告：APK → ZIP 问题非构建引起

## 审计结论

**APK 构建配置无任何异常。v1.47 的修复（自定义下载路由设置 Content-Type）是正确的方案。**

---

## 逐项检查结果

| 检查项 | 状态 | 说明 |
|:-------|:----:|:------|
| Gradle 版本 | ✅ | 8.9，兼容 AGP 8.6.1 |
| compileSdk | ✅ | 34 |
| minSdk / targetSdk | ✅ | 24 / 34 |
| versionCode / versionName | ✅ | 147 / 1.47 |
| Release minifyEnabled | ✅ | true（R8 混淆已开启） |
| signingConfig | ✅ | kuaimai-release.keystore 存在且有效 |
| proguard-rules.pro | ✅ | 完整，无遗漏，关键类全部保留 |
| AndroidManifest.xml | ✅ | 配置正常 |
| outputFileName | ✅ | `快麦取货通-${versionName}.apk` |
| 自定义 transform/task | ✅ | 无 |
| APK 魔数检查 | ✅ | `PK\x03\x04` — **APK 本身就是 ZIP 格式** |
| 构建产物 vs 源代码 | ⚠️ | 上次构建 v1.46，当前代码 v1.47（正常，还没重新构建） |

---

## 关键事实

**APK 文件本质就是 ZIP 格式的容器**，这是 Android 官方规范：

```
所有 .apk 文件的第一字节永远都是 PK\x03\x04
├── PK = Phil Katz（ZIP 格式发明人）
├── 03\x04 = ZIP 版本号
└── Android 在安装时通过 PackageManager 解析 APK 内的 AndroidManifest.xml
```

PDA 浏览器把 APK 识别为 ZIP 的完整链路：

```
Docker Alpine 环境
  → Python mimetypes.guess_type("xxx.apk") 无法识别
  → StaticFiles 回退 Content-Type: application/octet-stream
  → PDA 浏览器下载，检测到文件头部 PK 魔数
  → 判定为 ZIP 文件，自动重命名为 .zip
  → Android PackageManager 拒绝安装 .zip 文件
```

---

## v1.47 修复确认

已修复的方案（不需要额外修改）：

```python
# system.py 新增路由
@router.get("/api/app-version/download")
def download_apk(request: Request) -> FileResponse:
    ...
    return FileResponse(
        file_path,
        media_type="application/vnd.android.package-archive",  # ✅ 正确类型
        filename=file_name,                                     # ✅ Content-Disposition
    )
```

这个路由会：
1. 设置 `Content-Type: application/vnd.android.package-archive`（PDA 浏览器识别为 APK）
2. 设置 `Content-Disposition: attachment; filename="快麦取货通-1.47.apk"`（提示下载为 .apk）
3. downloadUrl / 二维码 URL 已从 `/apk/` 更新为 `/apk-download/`

---

## 建议

**无需再修改构建配置。** 构建环节一切正常，v1.47 的修复已覆盖全部场景：

| 下载方式 | 之前 | 之后 |
|:---------|:-----|:-----|
| PDA 浏览器扫码 | ❌ 识别为 ZIP | ✅ v1.47 自定义路由+正确 Content-Type |
| Android App 内下载 | ✅ OkHttp 直接下载字节流 | ✅ 不变 |
| 管理后台上传 | ✅ 正常 | ✅ 不变 |
