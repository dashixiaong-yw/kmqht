"""快麦API客户端 - MD5签名与API调用"""

import hashlib
import logging
from typing import Any, Dict, List, Optional

import httpx

from app.config import KUAIMAI_API_BASE, kuaimai_creds, save_kuaimai_config
from app.utils.time_utils import beijing_now, format_beijing

logger = logging.getLogger(__name__)

# HTTP客户端超时配置
_TIMEOUT = 30.0


def _sign(params: Dict[str, Any], app_secret: str) -> str:
    """
    生成MD5签名
    算法：将所有参数按key字母序排列，拼接为 key1value1key2value2...，
    前后各加app_secret，取MD5
    """
    sorted_keys = sorted(params.keys())
    sign_str = app_secret
    for key in sorted_keys:
        val = params[key]
        sign_str += f"{key}{val}"
    sign_str += app_secret
    return hashlib.md5(sign_str.encode("utf-8")).hexdigest().upper()


def _build_common_params(method: str) -> Dict[str, Any]:
    """构建公共请求参数"""
    return {
        "app_key": kuaimai_creds.app_key,
        "method": method,
        "session": kuaimai_creds.session,
        "timestamp": beijing_now().strftime("%Y-%m-%d %H:%M:%S"),
        "format": "json",
        "v": "2.0",
        "sign_method": "md5",
    }


async def _call_api(method: str, extra_params: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    """
    调用快麦API
    :param method: API方法名
    :param extra_params: 业务参数
    :return: API响应数据
    """
    if not kuaimai_creds.is_valid():
        raise ValueError("快麦凭证未配置")

    params = _build_common_params(method)
    if extra_params:
        params.update(extra_params)

    # 生成签名
    params["sign"] = _sign(params, kuaimai_creds.app_secret)

    try:
        async with httpx.AsyncClient(timeout=_TIMEOUT) as client:
            response = await client.post(KUAIMAI_API_BASE, data=params)
            response.raise_for_status()
            result: Dict[str, Any] = response.json()

        # 检查API错误
        error_response = result.get(f"{method.replace('.', '_')}_response", {})
        if "error_response" in result:
            error = result["error_response"]
            logger.error(f"快麦API错误: code={error.get('code')}, msg={error.get('zh_desc', error.get('msg'))}")
            raise ValueError(f"快麦API错误: {error.get('msg', '未知错误')}")

        return error_response
    except httpx.TimeoutException as e:
        logger.error(f"快麦API请求超时: {e}")
        raise
    except httpx.HTTPStatusError as e:
        logger.error(f"快麦API请求失败: status={e.response.status_code}")
        raise
    except httpx.RequestError as e:
        logger.error(f"快麦API网络错误: {e}")
        raise


# ==================== 7个API方法 ====================

async def get_sku_by_outer_id(sku_outer_id: str) -> Optional[Dict[str, Any]]:
    """根据外部编码获取SKU信息"""
    try:
        result = await _call_api(
            "kuaimai.item.sku.get",
            {"sku_outer_id": sku_outer_id}
        )
        sku_list = result.get("skus", [])
        if sku_list:
            return sku_list[0]
        return None
    except Exception as e:
        logger.error(f"查询SKU失败 sku_outer_id={sku_outer_id}: {e}")
        return None


async def get_item_detail(sys_item_id: int) -> Optional[Dict[str, Any]]:
    """获取商品详情"""
    try:
        result = await _call_api(
            "kuaimai.item.detail.get",
            {"sys_item_id": sys_item_id}
        )
        return result.get("item")
    except Exception as e:
        logger.error(f"查询商品详情失败 sys_item_id={sys_item_id}: {e}")
        return None


# ==================== 会话刷新 ====================

async def refresh_session() -> bool:
    """
    调用快麦开放平台刷新会话接口(open.token.refresh)
    刷新成功后accessToken和refreshToken值不变，仅延长30天有效期
    :return: 刷新是否成功
    """
    if not kuaimai_creds.has_refresh_token():
        logger.warning("refreshToken未配置，无法自动刷新session")
        return False

    try:
        result = await _call_api(
            "open.token.refresh",
            {"refreshToken": kuaimai_creds.refresh_token}
        )

        # 解析响应
        session_data = result.get("session", {})
        if not session_data:
            logger.error("刷新会话响应中缺少session数据")
            return False

        # 更新凭证（刷新后token值不变，但更新updated_at记录刷新时间）
        now = beijing_now()
        kuaimai_creds.updated_at = format_beijing(now)

        # 如果响应中返回了新的token，也更新（防御性处理）
        new_access_token = session_data.get("accessToken", "")
        new_refresh_token = session_data.get("refreshToken", "")
        if new_access_token:
            kuaimai_creds.session = new_access_token
        if new_refresh_token:
            kuaimai_creds.refresh_token = new_refresh_token

        # 持久化到文件
        save_kuaimai_config()

        days_left = kuaimai_creds.get_days_left()
        logger.info(f"快麦session刷新成功，剩余天数: {days_left}")
        return True

    except Exception as e:
        logger.error(f"刷新快麦session失败: {e}")
        return False
