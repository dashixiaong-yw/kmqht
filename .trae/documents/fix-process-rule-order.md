# v1.72 流程顺序错误分析 + 项目规则修正

## 一、v1.72 实际执行顺序回顾

| 实际步骤 | 对应Rule Step | 做了什么 |
|:--------:|:------------:|---------|
| ① | Step 2 | 修改PickDetailScreen + PickDetailViewModel |
| ② | Step 3 | `./gradlew lint` → OK |
| ③ | **❌ 越位** | `./gradlew assembleRelease` → OK **(用旧版本号1.71构建!)** |
| ④ | Step 4 | 更新版本号到 1.72 (4处手动) |
| ⑤ | — | **缺失：未用新版本号重建APK** |
| ⑥ | Step 6-8 | 知识图谱 + docker-deploy + Git |

**问题：APK 构建在版本号更新之前，构建出的 APK 文件名是 `快麦取货通-1.71.apk`，但仓库代码版本已是 1.72，版本号不匹配。**

---

## 二、根本原因：规则存在内在矛盾

### 矛盾1：准入条件要求 Step 5 的产物作为 Step 5 的前置条件

```
Step 5: 构建APK (assembleRelease)
  ↓
准入条件(进入Step 5前):  ✅ assembleRelease 构建成功
```

**这在逻辑上不可能同时满足**——你还没执行 Step 5，却要求 Step 5 的产物已存在。

这就迫使我必须在 Step 4(版本号)之前先做一次 `assembleRelease`，但做完后忘记 Step 5 需要**用新版本号重建**。

### 矛盾2：Step 3 范围与"开发阶段可循环"冲突

Step 表：
| Step | 阶段 | 内容 |
|:----:|:----:|------|
| 3 | 开发 | 验证代码（仅 lint） |
| 4 | **收尾** | 更新版本号 |

封版规则：
```
开发阶段可循环 → Step 1-4 可重复
收尾阶段不可逆 → 进入Step 5后禁止修改
```

这里有两个矛盾：
- Step 4 到底属于"开发"还是"收尾"？Step 表说收尾，封版规则说开发
- 如果 Step 4 是收尾且不可逆，那 lint 不过时无法回到 Step 2（因为版本号已更新）

### 矛盾3：缺少"最终构建"的明确意识

Step 3 只要求 lint，没有要求 pre-flight build check。而准入条件又暗示需要 pre-build。导致执行时有两种解读：
1. 严格按表：lint → 改版本 → assemble（正确但没预检）
2. 按准入条件暗示：lint → assemble（预检）→ 改版本 → 忘了重建（**v1.72 的bug**）

---

## 三、修正方案

核心思路：**将 pre-flight build check 和 final versioned build 明确分离**。

### 改动前后对比

| 位置 | 当前（有问题） | 修正 |
|:-----|:-------------|:-----|
| Step 3 内容 | `./gradlew lint` 必须通过 | `./gradlew lint` + `./gradlew assembleRelease` 均必须通过（进入收尾前的预检） |
| Step 4 说明 | （无强调） | 新增：**更新后立即用新版本号重建APK** |
| 准入条件 | lint + assembleRelease 均通过 | **仅保留 lint 通过**（删除 assembleRelease，避免循环依赖） |
| 封版规则 | Step 1-4 可循环 | 修正为 Step 1-3 可循环 |

### 具体文本修改（.trae/rules/README.md）

#### 修改1：Step 表

```markdown
| Step | 阶段 | 内容 | 说明 |
|:----:|:----:|------|------|
| 1 | 开发 | **查阅知识图谱** | 首次修改前必须查阅；批量任务中后续任务按需查阅 |
| 2 | 开发 | 修改代码 | 在 `app/` 目录修改，**支持批量完成多个任务后再进入收尾** |
| 3 | 开发 | 验证代码 + 预构建 | `./gradlew lint` + `./gradlew assembleRelease` 均必须通过；**失败则回到Step 2修复**。此步构建的APK为预检用途，不保留 |
| 4 | 收尾 | 更新版本号（含Docker BUILD_VERSION） | **⚠️ 进入收尾后禁止再修改代码**；**先读取6处当前版本号取最大值，再+1递增**，更新6处并验证一致 |
| 5 | 收尾 | 用新版本号构建APK | `./gradlew assembleRelease`（签名+混淆）构建成功，**APK文件名对应新版本号**；**失败则回到Step 2修复**（需在新版本号基础上修复或升级版本号） |
```

#### 修改2：准入条件

```markdown
**收尾阶段准入条件（进入Step 4前必须同时满足）**：

- ✅ 所有任务代码修改已完成（逐项确认，无遗漏）
- ✅ `./gradlew lint` 通过
- ✅ `./gradlew assembleRelease` 预构建通过
```

> 注意：进入条件从"Step 5前"改为"Step 4前"，这样 Step 4 的版本更新和 Step 5 的最终构建都在预检通过之后。

#### 修改3：封版规则

```markdown
| 规则 | 说明 |
|------|------|
| **开发阶段可循环** | Step 1-3可重复执行，直到所有任务完成且验证构建通过 |
| **收尾阶段不可逆** | 进入Step 4后禁止再修改任何代码，必须完成Step 4-8 |
```

> Step 4 是"更新版本号"，进入后即锁定代码。如果 Step 5 构建失败，必须**递增版本号**重新走完整流程（因为代码可能已修改，原版本号对应不上）。

#### 修改4：Step 5 说明强化

```markdown
| 5 | 收尾 | 用新版本号重建APK | `./gradlew assembleRelease`（签名+混淆）构建成功，**此APK为最终发布版本**（文件名含新版本号）；**失败则回到Step 2修复**（需递增版本号，因为Step 4已锁定） |
```

---

## 四、修正后的标准流程

```
开发阶段（可循环 Step 1-3）：
  Step 1: 查阅知识图谱
  Step 2: 修改代码     ←──────────┐
  Step 3: lint + assembleRelease    │ 失败循环
     ↓ 通过                        ┘
收尾阶段（不可逆 Step 4-8）：
  Step 4: 更新版本号 v1.7x → v1.7y（4处手动 + 验证6处一致）
  Step 5: 用新版本号重建APK（assembleRelease，文件名含新版本号）
  Step 6: 更新知识图谱
  Step 7: 同步docker-deploy
  Step 8: Git提交推送
```

关键区分：
- **Step 3 的 assembleRelease** = 预检（APK可丢弃，只是检查能编译通过）
- **Step 5 的 assembleRelease** = 最终产物（APK文件名含新版本号，保留交付）

---

## 五、修正文件清单

| # | 文件 | 改动 |
|:--:|:-----|:-----|
| 1 | [.trae/rules/README.md](file:///d:/trea项目/快麦取货通/.trae/rules/README.md) | Step表(Step3/4/5修改)、准入条件(Step5前→Step4前,删assemble)、封版规则(1-4→1-3) |

## 六、验证步骤

1. 手工通读修正后的规则，确认无逻辑矛盾
2. 下次开发时严格按新规则执行：预检build → 改版本 → 最终build
