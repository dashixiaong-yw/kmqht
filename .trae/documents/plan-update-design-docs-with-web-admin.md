# 计划：将Web管理后台更新到项目设计文档与spec文档

## 摘要

将v1.9/v1.10已实现的Web管理后台（F37）和扫码配置（F38）功能同步到项目设计文档（kuaimai-pda-app-plan.md）和规格文档（spec.md、checklist.md），确保后续可查找不遗漏。

## 当前状态分析

### 已完成（上一会话）
- ✅ `kuaimai-pda-app-plan.md` 功能需求表新增F37/F38（第44-45行）
- ✅ `kuaimai-pda-app-plan.md` 后端职责新增3条（第92-94行）
- ✅ `kuaimai-pda-app-plan.md` 数据流向图新增管理员浏览器和PDA首次配置路径（第103-104行）
- ✅ `kuaimai-pda-app-plan.md` 项目结构中设置页面描述更新为"个人设置页面"（第213行）

### 待完成
1. `kuaimai-pda-app-plan.md` 页面设计章节（第225行起）缺少Web管理后台和扫码配置页面描述
2. `kuaimai-pda-app-plan.md` 项目结构缺少SetupQrParser工具类
3. `spec.md` 缺少F37/F38的Requirement定义
4. `spec.md` 缺少后端环境变量SERVER_URL的更新
5. `checklist.md` 缺少Web管理后台和扫码配置的验证项

## 具体修改计划

### 1. kuaimai-pda-app-plan.md - 页面设计章节新增

**位置**：第335行（商品详情页描述结束后、UI设计规范章节开始前）

**新增内容**：
- `### Web管理后台页面（浏览器访问 /admin）` - 包含6个标签页的ASCII布局图和功能描述
- `### 扫码配置页面（浏览器访问 /setup）` - 二维码配置页面描述

**Web管理后台6个标签页描述**：
1. 仪表盘：系统概览统计（取货单数/用户数/拣货区数）+ 扫码配置二维码
2. 用户管理：增删改查+权限分配+启用禁用
3. 拣货区管理：增删拣货区
4. 快麦配置：凭证状态+刷新+手动更新
5. 系统配置：API Key+服务器地址
6. 图片查看：按SKU只读查看

**扫码配置页面描述**：
- 显示服务器地址+API Key的二维码
- 协议格式：`kuaimai://setup?server=xxx&apikey=xxx`
- PDA扫码后自动填入GuideScreen

### 2. kuaimai-pda-app-plan.md - 项目结构补充

**位置**：第220-222行（util/目录下）

**新增**：`SetupQrParser.kt` 工具类到util目录

### 3. spec.md - 新增F37/F38 Requirement

**位置**：第694行（文件末尾，F32冷启动优化细节之后）

**新增内容**：
- `### Requirement: F37 Web管理后台` - 7个Scenario：
  1. 管理后台访问与认证
  2. 仪表盘概览
  3. 用户管理
  4. 拣货区管理
  5. 快麦配置
  6. 系统配置
  7. 图片查看

- `### Requirement: F38 扫码配置` - 2个Scenario：
  1. 扫码配置页面
  2. PDA扫码配置解析

### 4. spec.md - 更新后端环境变量配置

**位置**：第577-582行（后端环境变量配置Requirement）

**修改**：新增SERVER_URL环境变量说明

### 5. checklist.md - 新增验证项

**位置**：Phase 5验证末尾（第109行之后）

**新增内容**：13个验证项
- Web管理后台：6项（访问认证/仪表盘/用户管理/拣货区管理/快麦配置/系统配置/图片查看）
- 扫码配置：3项（/setup页面/二维码协议/PDA扫码解析）
- App与Web权限分离：2项（SettingsScreen精简/权限边界清晰）
- 环境变量：2项（SERVER_URL配置/.env.docker.example）

## 假设与决策

1. **文档风格一致**：新增内容严格匹配现有格式（ASCII图、WHEN/THEN Scenario、checklist格式）
2. **以实际代码为准**：文档描述基于admin.py和system.py的实际实现
3. **不修改代码**：本次仅更新文档，不涉及代码修改，因此不需要更新版本号和Git提交
4. **App与Web权限边界**：明确记录Web后台负责系统管理，App端负责日常业务

## 验证步骤

1. 检查kuaimai-pda-app-plan.md页面设计章节包含Web管理后台和扫码配置页面描述
2. 检查spec.md包含F37/F38的Requirement定义
3. 检查checklist.md包含13个新增验证项
4. 检查所有新增内容与现有格式一致
