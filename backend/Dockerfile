FROM python:3.12-alpine
WORKDIR /app

ENV TZ=Asia/Shanghai
RUN apk add --no-cache curl tzdata rust cargo && \
    ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

COPY requirements.txt .
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install -r requirements.txt

COPY . .

EXPOSE 8900
CMD uvicorn main:app --host 0.0.0.0 --port ${SERVER_PORT:-8900}
