# 安全审计与加密加固计划

## 安全现状总览

经过全面代码审计，确认本项目存在**两个明文存储风险点**：

| 风险点 | 位置 | 当前存储方式 | 风险等级 | 处理方式 |
|:-------|:-----|:------------:|:--------:|:--------:|
| 快麦凭证 | `backend/kuaimai.json` 磁盘文件 | **明文 JSON** ❌ | **高** | AES-256-GCM 文件加密 |
| 用户密码 | Android `EncryptedSharedPreferences` | **明文 value** ⚠️ | **中** | Android KeyStore + AES/GCM 再加密 |

### 已验证为安全的项目

| 项 | 状态 | 说明 |
|:---|:----:|:-----|
| 后端用户密码 | ✅ bcrypt 哈希 | `_hash_password()` 使用 bcrypt，不可逆 |
| Android 快麦凭证 | ✅ EncryptedSharedPreferences | 文件系统级 AES-256-GCM 加密 |
| Android 用户 Token | ✅ EncryptedSharedPreferences | 同上 |
| 管理后台 API Key | ✅ 环境变量 | 不在代码仓库中硬编码 |
| 凭证文件未进 Git | ✅ .gitignore | kuaimai.json 已排除 |
| 登录限流 | ✅ 5次失败锁定5分钟 | `_LOGIN_FAIL_COUNTS` |

---

## 方案一：后端 kuaimai.json AES-256-GCM 文件加密（高优）

### 背景
`backend/kuaimai.json` 以明文存储 appKey/appSecret/session/refreshToken，若服务器被入侵则凭证直接泄露。

### 设计

#### 加密算法
- 算法: `AES-256-GCM`（认证加密，防篡改）
- 密钥: 32 字节（256 bit），Base64 编码后通过环境变量 `KUAIMAI_CONFIG_KEY` 传入
- Nonce: 12 字节（96 bit），每次加密随机生成
- 输出格式: `12字节 nonce || AES-GCM 密文（含16字节认证标签）`
- 依赖: `cryptography` Python 库

#### 关键修改：`save_kuaimai_config()` ⚠️

**当前实现的问题（已发现）**：
```python
# 当前代码：先读取旧文件，再更新字段
if config_path.exists():
    with open(config_path, "r", encoding="utf-8") as f:
        data = json.load(f)  # ← 加密后文件是二进制，json.load 会崩溃！
```

**修复方案**：改为不读取旧文件，直接从内存中的 `kuaimai_creds` 构建字典：

```python
def save_kuaimai_config() -> None:
    config_path = Path(KUAIMAI_CONFIG_PATH)
    try:
        # 直接从内存构建数据（不依赖旧文件格式）
        with kuaimai_config_lock:
            data = {
                "app_key": kuaimai_creds.app_key,
                "app_secret": kuaimai_creds.app_secret,
                "session": kuaimai_creds.session,
                "refresh_token": kuaimai_creds.refresh_token,
                "updated_at": kuaimai_creds.updated_at,
            }

        config_key = _get_config_key()  # 从环境变量读取
        tmp_path = config_path.with_suffix(".json.tmp")
        if config_key:
            encrypted = _encrypt_config_data(data, config_key)
            with open(tmp_path, "wb") as f:
                f.write(encrypted)
        else:
            with open(tmp_path, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
        tmp_path.replace(config_path)
    except Exception as e:
        logger.error(f"保存快麦凭证失败: {e}")
```

#### 关键修改：`load_kuaimai_config()` 兼容两种格式

```python
def load_kuaimai_config() -> None:
    config_path = Path(KUAIMAI_CONFIG_PATH)
    if not config_path.exists():
        logger.warning(f"快麦凭证文件不存在: {KUAIMAI_CONFIG_PATH}")
        return

    try:
        raw = config_path.read_bytes()
        config_key = _get_config_key()

        data = None
        if config_key:
            try:
                data = _decrypt_config_data(raw, config_key)
            except Exception:
                pass  # 解密失败，尝试明文

        if data is None:
            # 明文回退
            data = json.loads(raw.decode("utf-8"))
            if config_key:
                logger.warning("旧版明文配置文件 detected，建议运行部署脚本更新")

        with kuaimai_config_lock:
            kuaimai_creds.app_key = data.get("app_key", "")
            kuaimai_creds.app_secret = data.get("app_secret", "")
            kuaimai_creds.session = data.get("session", "")
            kuaimai_creds.refresh_token = data.get("refresh_token", "")
            kuaimai_creds.updated_at = data.get("updated_at", "")
        logger.info("快麦凭证加载成功")
    except Exception as e:
        logger.error(f"加载快麦凭证失败: {e}")
```

#### 兼容性矩阵

| 旧文件格式 | 密钥配置 | 行为 | 日志 |
|:----------|:--------:|:-----|:-----|
| 明文 JSON | 无 | 正常读取 ✅ | 正常 |
| 明文 JSON | 有 | 正常读取 + 下次保存转为加密 ✅ | 警告"旧版明文" |
| 加密格式 | 有 | 正常解密 ✅ | 正常 |
| 加密格式 | 无 | 解密失败 → 明文回退失败 → 加载失败 ❌ | 错误日志 |
| 文件不存在 | 任意 | 跳过加载 ✅ | 警告文件不存在 |

#### 密钥生命周期

```
生成:  openssl rand -base64 32
存储:  docker-deploy/.env 的 KUAIMAI_CONFIG_KEY 字段
使用:  仅在后端进程启动时读取到内存
轮换:  手动替换密钥 + 重新加密 kuaimai.json
```

#### 涉及修改

| 文件 | 修改内容 | 风险 |
|:----|:---------|:----|
| `backend/requirements.txt` | 新增 `cryptography` | 低 — 纯 Python 库 |
| `backend/app/config.py` | 新增 `_encrypt_config_data()`/`_decrypt_config_data()`/`_get_config_key()`；重写 `save_kuaimai_config()`/`load_kuaimai_config()` | 中 — 核心逻辑变更，需验证加解密 + 向后兼容 |
| `backend/.env.docker.example` | 新增 `KUAIMAI_CONFIG_KEY=` 注释 | 低 |
| `docker-deploy/.env` | 新增 `KUAIMAI_CONFIG_KEY` 占位 | 低 |
| `docs/快麦凭证配置说明.md` | 补充密钥管理章节 | 低 |

#### ✅ 已确认不受影响的路径

| 路径 | 理由 |
|:-----|:------|
| `main.py` startup 复制 (`shutil.copy`) | `shutil.copy` 是二进制复制，加密文件格式不受影响 |
| `_watch_config` 热重载 | 调用 `load_kuaimai_config()`，已兼容加密格式 |
| `admin.py` 管理后台凭证更新 | 通过 API → `save_kuaimai_config()` → 自动加密 |
| `system.py` API 凭证查询/更新 | 修改 `kuaimai_creds` 内存对象 → 调用 `save_kuaimai_config()` |
| `kuaimai_api.py` session 刷新 | 修改 `kuaimai_creds` → 调用 `save_kuaimai_config()` |
| sync-to-docker-deploy.ps1 | `Copy-Item` 是二进制复制 |
| Dockerfile `COPY . .` | 复制整个 backend 目录，包含 kuaimai.json |
| `.dockerignore` | 不排除 `kuaimai.json` ✅ |
| `.gitignore` | 已排除 `kuaimai.json` ✅ |

---

## 方案二：Android 记住密码应用层加密（中优）

### 背景
`saveCredentials()` 将密码明文存入 `EncryptedSharedPreferences`。虽然存储介质已加密，但在 root 设备上通过进程内存 dump 可读取。用户选择再额外加密一层。

### 设计

#### 加密方案
- 使用 **Android KeyStore** 存储加密密钥（系统级安全硬件）
- 算法: `AES/GCM/NoPadding`，256 bit
- 密钥别名: `"kuaimai_password_key"`
- 存储格式: `Base64(12字节IV || AES-GCM密文（含认证标签）)`

#### 向前兼容（关键！）
当前存储的密码是明文（如 `"myPassword123"`），加密后变为 `"qK3x...=="`。新代码的 `getSavedPassword()` 需要能区分两种格式：

```
getSavedPassword()
  ├── 尝试 Base64 解码 + AES-GCM 解密
  │     ├── 成功 → 返回明文密码 ✅
  │     └── 失败 → 返回原字符串（兼容旧明文）✅
  └── 自动迁移：下次登录保存时转为加密格式
```

#### 涉及修改

| 文件 | 修改内容 |
|:----|:---------|
| `app/.../UserRepository.kt` | 新增 `initPasswordCipherKey()`/`encryptPassword()`/`decryptPassword()`，修改 `saveCredentials()`/`getSavedPassword()` |
| `app/.../di/NetworkModule.kt` | 不需要修改 — `MasterKey` 已存在于 `provideEncryptedSharedPreferences` 内部 |

#### 详细实现（`UserRepositoryImpl`）

```kotlin
private fun getPasswordCipherKey(): SecretKey {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    if (!keyStore.containsAlias(PASSWORD_KEY_ALIAS)) {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                PASSWORD_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }
    return (keyStore.getEntry(PASSWORD_KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
}

private fun encryptPassword(plaintext: String): String {
    return try {
        val secretKey = getPasswordCipherKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv  // 12 bytes
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    } catch (e: Exception) {
        Log.e(TAG, "加密密码失败: ${e.message}")
        plaintext  // 降级：不阻塞登录
    }
}

private fun decryptPassword(encrypted: String): String {
    return try {
        val raw = Base64.decode(encrypted, Base64.NO_WRAP)
        val secretKey = getPasswordCipherKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, raw, 0, 12)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        cipher.doFinal(raw, 12, raw.size - 12).toString(Charsets.UTF_8)
    } catch (e: Exception) {
        // 解密失败 → 可能是旧版明文密码，直接返回原值
        encrypted
    }
}
```

#### ⚠️ 内存安全：登录成功后清除密码

`LoginScreen.kt` line 262-263 在登录成功后仍持有密码变量。需在登录完成后清除：

```kotlin
// 登录成功后
if (savePasswordChecked && password.isNotEmpty()) {
    userRepository.saveCredentials(username, password)
} else {
    userRepository.clearSavedCredentials()
}
password = ""  // ← 新增：清除内存中的密码
```

#### 不再修改的文件

| 文件 | 理由 |
|:----|:------|
| `LoginScreen.kt` | 除添加 `password = ""` 外，接口不变 |
| `PrefsKeys.kt` | Key 名称不变 |
| `AuthRepository.kt` | 快麦凭证存储逻辑不变 |
| `NetworkModule.kt` | 密钥不依赖 MasterKey |

---

## 不修改项（用户确认）

| 项 | 理由 |
|:---|:------|
| 网络传输（HTTP） | 维持现状。内网/PDA 专用网络环境。但 `AppConstants.DEFAULT_SERVER_URL` 已配置为 `https://frp-off.com:64623` ✅ |
| SSL 证书信任所有 | 同上，维持 `unsafeTrustManager` |
| 数据库文件加密 | 作为低优后续任务 |

---

## 前置条件检查清单

### 后端

- [x] `cryptography` 库不在现有 `requirements.txt` 中 → **需要新增**
- [x] `.dockerignore` 排除 `.env.*` → 不影响 `env_file` 运行时传递环境变量 ✅
- [x] `.gitignore` 已排除 `kuaimai.json` → 加密文件不会进 Git ✅
- [x] `main.py` 启动时从 `backend/kuaimai.json` 复制到 `/data/kuaimai.json` → `shutil.copy` 二进制复制 ✅
- [x] `_watch_config` 热重载调用 `load_kuaimai_config()` → 已兼容加密格式 ✅
- [x] 所有调用 `save_kuaimai_config()` 的路径（system.py、kuaimai_api.py）→ 写入加密格式 ✅
- [x] `save_kuaimai_config()` 当前依赖 `json.load()` 读取旧文件 → **需要改为从内存构建** ⚠️
- [x] Docker 中 `.env` 文件被 dockerignore 排除 → `docker-compose.yml` 使用 `env_file: .env.docker.example`，这是运行时特性，不受 dockerignore 影响 ✅

### Android

- [x] `androidx.security:security-crypto` 已存在于 `build.gradle.kts` ✅
- [x] `proguard-rules.pro` 已包含 Tink 相关规则 ✅
- [x] minSdk 23 → Android KeyStore 的 `AES/GCM/NoPadding` 可用 ✅
- [x] 当前 `EncryptedSharedPreferences` 存储明文密码 → 需要迁移到加密存储 ⚠️
- [x] 旧版明文密码的向前兼容 → `decryptPassword()` 失败时返回原文 ✅
- [x] 登录成功后需要清除内存中的密码变量 → `password = ""` ✅

---

## 执行步骤

### Step 1：查阅知识图谱
- 查询 kuaimai-memory 确认无已存在的安全加密决策

### Step 2：修改代码

**子任务 A：后端 kuaimai.json AES-256-GCM 加密（4 个文件修改）**

1. `backend/requirements.txt` — 添加 `cryptography==43.0.1`
2. `backend/app/config.py`：
   - 新增 `_get_config_key()` — 从环境变量读取 Base64 密钥
   - 新增 `_encrypt_config_data(data: dict, key: bytes) -> bytes` — AES-GCM 加密
   - 新增 `_decrypt_config_data(data: bytes, key: bytes) -> dict` — AES-GCM 解密
   - 重写 `save_kuaimai_config()` — 从内存构建，密钥存在时加密
   - 重写 `load_kuaimai_config()` — 自动检测加密/明文
3. `backend/.env.docker.example` — 添加 `KUAIMAI_CONFIG_KEY=`
4. `docs/快麦凭证配置说明.md` — 新增"密钥管理"章节

**子任务 B：Android 记住密码加密（2 个文件修改）**

1. `app/.../UserRepository.kt`：
   - 新增 `PASSWORD_KEY_ALIAS` 常量
   - 新增 `getPasswordCipherKey()` — KeyStore 密钥生成/获取
   - 新增 `encryptPassword()` / `decryptPassword()`
   - 修改 `saveCredentials()` — 加密后存储
   - 修改 `getSavedPassword()` — 解密后返回
2. `app/.../LoginScreen.kt`：
   - 登录成功后添加 `password = ""` 清除内存

### Step 3：更新版本号
- 读取 6 处版本号 → 取最大值+1

### Step 4：构建 APK
- `./gradlew assembleRelease`

### Step 5：更新知识图谱
- 记录本次安全决策到 kuaimai-memory

### Step 6：同步 docker-deploy
- `.\scripts\sync-to-docker-deploy.ps1 -Force`

### Step 7：Git 提交
- `git commit -m "v版本号: 安全加固 - 后端kuaimai.json AES-256-GCM加密 + Android记住密码应用层加密"`

---

## 验证清单

| # | 验证项 | 方法 |
|:--|:-------|:-----|
| 1 | 无密钥时明文文件可读 | 删除 `KUAIMAI_CONFIG_KEY`，启动后端，确认凭证可加载 |
| 2 | 有密钥时加密文件可解密 | 设置 `KUAIMAI_CONFIG_KEY`，在管理后台修改凭证，确认文件变为二进制 |
| 3 | 管理后台修改凭证后流转正常 | 修改 → 保存 → 确认快麦 API 调用正常 |
| 4 | session 刷新后保存正常 | 触发 session 刷新 → 确认文件不损坏 |
| 5 | 明文文件不会被自动覆盖 | 无密钥启动 → 读取明文 → 不调用 save → 文件不变 |
| 6 | Android 记住密码正常 | 登录勾选"记住密码" → 重启 App → 密码回填正常 |
| 7 | Android 旧密码迁移 | 已有明文密码 → 新代码读取正常 → 下次保存加密 |
| 8 | Android 登录后密码清除 | 登录成功 → 确认 `password` 变量为空 |
| 9 | Docker 部署正常 | `docker-compose up -d --build` + 配置 `KUAIMAI_CONFIG_KEY` → 服务正常 |

---

## 风险与限制

| 风险 | 说明 | 缓解措施 |
|:----|:-----|:---------|
| 密钥泄露 | `KUAIMAI_CONFIG_KEY` 存储在 `.env` 文件 | .env 文件在 Docker 容器内，权限受控；后续可改为 Docker secrets |
| 密钥丢失 | 密钥丢失后无法解密 kuaimai.json | 备份密钥到密码管理器；保留明文旧版兼容机制 |
| 性能影响 | AES-GCM 加解密带来微小延迟 | 仅发生在启动时 + 凭证修改时，不影响运行时 API 调用 |
| Android KeyStore 故障 | 极低概率的 KeyStore 损坏 | `encryptPassword()` 降级返回原文，不阻塞登录 |
| Docker .env 文件排除 | `.dockerignore` 排除了 `.env.*` | `env_file` 是 compose 运行时特性，不受影响 ✅ |

---

## 代码变更总结

### 后端（4 个文件）

| 文件 | 变更行数（估算） | 复杂度 |
|:-----|:---------------:|:------:|
| `backend/requirements.txt` | +1 | 低 |
| `backend/app/config.py` | ~+60 / ~-15 | 高 |
| `backend/.env.docker.example` | +1 | 低 |
| `docs/快麦凭证配置说明.md` | ~+20 | 低 |

### Android（2 个文件）

| 文件 | 变更行数（估算） | 复杂度 |
|:-----|:---------------:|:------:|
| `app/.../UserRepository.kt` | ~+60 | 中 |
| `app/.../LoginScreen.kt` | +1 | 低 |

**总计：约 6 个文件，~140 行新增/修改**
