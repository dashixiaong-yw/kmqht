# 第七次审计 — 最终收尾计划

> 日期：2026-06-17
> 背景：六次审计已修复64个问题，本次仅处理回归验证发现的两个真实问题

---

## 发现

### B1：PickDetailViewModel.getImageUrls() 修复未提交

v1.18 期间对 `PickDetailViewModel.kt` 的 `getImageUrls()` 修改（将编译时常量改为运行时读取 `SharedPreferences`）**虽然在磁盘上已编辑，但未包含在 git commit 中**。`git status` 显示该文件有未暂存的修改。

### B2：docker-deploy/backend/ 遗留目录

同步脚本只操作 `docker-deploy/` 根目录和 `docker-deploy/app/`，但 `docker-deploy/backend/` 是之前目录结构遗留的旧文件，内部包含过时的多阶段 Dockerfile，需要使用 `git rm -r` 清理。

---

## 操作步骤

1. `git add app/src/main/java/com/kuaimai/pda/ui/pickdetail/PickDetailViewModel.kt`
2. `git rm -r docker-deploy/backend/`
3. 重新运行同步脚本覆盖确保一致
4. `git commit -m "fix: v1.18遗漏的getImageUrls修复提交 + 清理docker-deploy过期目录"`
5. `git push`

---

## 验证标准

- `./gradlew assembleDebug` 构建成功
- 无 `docker-deploy/backend/` 遗留文件
