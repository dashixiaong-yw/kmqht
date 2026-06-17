# 快麦取货通 - SakuraFrp 内网穿透接入

## 一句话

在 SakuraFrp 管理面板创建一条 TCP 隧道指向 `localhost:8900`，frpc 自动接管，无需新增容器。

---

## 操作步骤

### Step 1：登录 SakuraFrp 管理面板

打开 https://www.natfrp.com/ → 登录 → **隧道** → **创建隧道**

### Step 2：填写隧道参数

| 参数 | 值 | 说明 |
|------|-----|------|
| 隧道类型 | **TCP 隧道** | 固定 |
| 本地 IP | `localhost` | 与 frpc 共享 host 网络 |
| 本地端口 | **`8900`** | 快麦后端暴露在 NAS 上的端口 |
| 远程端口 | **随机** | 系统自动分配 |
| 自动 HTTPS | **是** ✅ | 勾选 |
| 隧道名称 | **`kuaimai-pda`** | 管理面板识别用 |

### Step 3：启动隧道

打开 https://www.natfrp.com/remote/v2 → 输入远程管理密码 → 找到 `kuaimai-pda` 隧道 → **双击启动**

### Step 4：获取外网链接

隧道启动后日志会显示 `https://xxxxx.sakura.frp`，这就是外网访问地址。

---

## 验证

```bash
curl https://xxxxx.sakura.frp/health
# 应返回 200 OK
```

---

## 重要说明

- ✅ **无需重启 frpc 容器**，创建隧道后自动生效
- ✅ **不影响原隧道**（在线基金管理的链接照常使用）
- ❌ **不需要新建 frpc 容器**，现有容器已覆盖
