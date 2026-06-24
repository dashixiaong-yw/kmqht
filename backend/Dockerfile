FROM python:3.12-alpine
ARG BUILD_VERSION
ENV BUILD_VERSION=$BUILD_VERSION
WORKDIR /app

ENV TZ=Asia/Shanghai
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
