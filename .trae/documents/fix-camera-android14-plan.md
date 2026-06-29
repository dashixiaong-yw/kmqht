# 修复 Android 14 拍照优先调用图库而非相机

## 审查结论

基础设施确认全部正确：
- `FileProvider` authority 一致 ✅
- `file_paths.xml` `<cache-path path="/" />` 覆盖 temp 文件 ✅
- `CAMERA` 权限已声明 ✅
- `FEATURE_CAMERA_ANY` 检查存在 ✅
- 3 级 fallback 完整 ✅

**问题**：`ActivityResultContracts.TakePicture()` 无参构造函数（L146）在 Android 14 厂商设备上，系统相机无法正确写入 FileProvider content URI → 抛异常 → `catch (_: Exception)` 静默捕获 → fallback 到 `PickVisualMedia` 图库。

## 修复

将 `TakePicture()` contract 替换为 `StartActivityForResult()` + 手动 `ACTION_IMAGE_CAPTURE` intent + `FLAG_GRANT_*` 权限标志。

### ProductScreen.kt 改动

**1. 删除** `TakePicture` import（L89）：
```kotlin
// 删除
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
```

**2. 删除** cameraLauncher 旧定义（L145-157），**替换为**：
```kotlin
val cameraLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
        val file = pendingCameraFile
        val type = pendingImageType
        if (file != null && type != null) {
            viewModel.uploadImage(file, type)
        }
    }
    pendingCameraFile = null
    pendingImageType = null
}
```

**3. 修改** onUploadArea 的 camera 调用（L232-234）：
```kotlin
// 改前
cameraLauncher.launch(
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
)

// 改后
val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
    putExtra(MediaStore.EXTRA_OUTPUT, uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
}
cameraLauncher.launch(intent)
```

**4. 同样修改** onUploadBox 的 camera 调用（L254-256）

**5. 新增 import**（仅 1 个）：
```kotlin
import android.provider.MediaStore
```
（`Intent` 已存在 L7，`Activity` 已存在 L3）

### 变更量

| 文件 | 删除 | 新增 | 净变化 |
|:-----|:----:|:----:|:------:|
| ProductScreen.kt | ~7 行 | ~20 行 | +13 行 |

## 版本号

2.46 → 2.47
