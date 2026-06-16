"""认证模块 - API Key中间件"""

import logging

from fastapi import Request
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.responses import JSONResponse

from app.config import API_KEY

logger = logging.getLogger(__name__)

# 不需要认证的路径前缀
SKIP_AUTH_PREFIXES = ("/images", "/health", "/docs", "/redoc", "/openapi.json")


class ApiKeyMiddleware(BaseHTTPMiddleware):
    """API Key认证中间件"""

    async def dispatch(self, request: Request, call_next):
        # 跳过不需要认证的路径
        for prefix in SKIP_AUTH_PREFIXES:
            if request.url.path.startswith(prefix):
                return await call_next(request)

        # 检查X-API-Key请求头
        api_key = request.headers.get("X-API-Key", "")
        if not api_key:
            logger.warning(f"缺少API Key: {request.url.path}")
            return JSONResponse(
                status_code=401,
                content={"success": False, "message": "缺少API Key"}
            )

        if api_key != API_KEY:
            logger.warning(f"API Key无效: {request.url.path}")
            return JSONResponse(
                status_code=403,
                content={"success": False, "message": "API Key无效"}
            )

        return await call_next(request)
