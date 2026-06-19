# Docker 构建优化计划 — `apk add` 超慢问题

## 问题

NAS 上 `docker build` 时 `apk add --no-cache curl tzdata rust cargo` 已执行 **934 秒（15分钟）仍未完成**，远超正常时间（首次约 2 分钟）。

## 根因

NAS 位于中国大陆网络环境，**Alpine 默认软件源 `dl-cdn.alpinelinux.org`（位于欧洲/美国）下载极慢**。`rust` + `cargo` 包合计约 **200MB**，从外网下载速率可能不到 10KB/s，导致超时或挂起。

## 优化方案

### 改动 1 个文件：`Dockerfile`

使用**阿里云镜像**加速 Alpine 和 pip 的下载：

```dockerfile
FROM python:3.12-alpine
ARG BUILD_VERSION
WORKDIR /app

ENV TZ=Asia/Shanghai
# 使用阿里云 Alpine 镜像 + pip 镜像加速国内构建
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories && \
    apk add --no-cache curl tzdata rust cargo && \
    ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

COPY requirements.txt .
RUN --mount=type=cache,target=/root/.cache/pip \
    PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple/ \
    pip install -r requirements.txt && \
    apk del rust cargo

COPY . .

EXPOSE 8900
CMD uvicorn main:app --host 0.0.0.0 --port ${SERVER_PORT:-8900}
```

### 仅 2 处改动

| 行 | 改前 | 改后 | 作用 |
|:---|:-----|:-----|:-----|
| L6 前新增 | 无 | `sed -i 's/dl-cdn.../mirrors.aliyun.../'` | 将 Alpine 源切换为阿里云镜像 |
| L12 | `pip install ...` | `PIP_INDEX_URL=... pip install ...` | 将 PyPI 源切换为阿里云镜像 |

### 预期效果

| 阶段 | 改前（从外网） | 改后（阿里云镜像） |
|:-----|:--------------:|:------------------:|
| `apk add rust cargo` | ~120-200s（或超时） | **~20-40s** |
| `pip install bcrypt` | ~60-120s | **~10-20s** |
| 总构建时间 | 可能失败 | **~2-3 分钟** |

### 不修改其他文件的原因

| 方案 | 不采用的原因 |
|:-----|:------------|
| 降级 `bcrypt` 到纯 C 版 | 需要修改 requirements.txt，验证密码兼容性，风险较大 |
| 切换到 `passlib` | 增加新依赖，需要改 users.py 中的 hash/verify 逻辑 |
| 使用 pip 镜像 CN 环境变量 | 部分 NAS 可能不用国内网络，环境变量更灵活但要改 docker-compose |

`sed` 修改源是最小侵入方案，只影响当前构建，不改变基础镜像。
