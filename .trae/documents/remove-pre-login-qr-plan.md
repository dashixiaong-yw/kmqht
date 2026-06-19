# v1.57: 移除管理后台 pre-login PDA扫码配置区域

## 改动

**文件**: [admin.py](file:///d:/trea项目\快麦取货通/backend/app/routers/admin.py)

删除 L224-240 的 pre-login PDA扫码配置卡片：

```html
<!-- ====== 公开区域：扫码配置（无需API Key） ====== -->
<div class="card" style="margin-bottom:24px;...">
  <h3>PDA 扫码配置</h3>
  ...
</div>
<hr style="...">
```

仪表盘 Tab 内的 PDA扫码配置（L272-286）**保留**，供内网切换时使用。

## 影响

- 未输入 API Key 时的管理后台页面不再显示 PDA 扫码配置二维码，更加简洁
- 登录后仪表盘内的扫码配置仍然存在
- 纯后端 HTML 改动，不需要重新构建 APK

## 步骤

| Step | 操作 |
|:----:|------|
| 1 | 删除 admin.py L224-242 的 pre-login 扫码配置区块 |
| 2 | 版本号 1.56→1.57(6处) |
| 3 | 知识图谱 + sync + Git |
