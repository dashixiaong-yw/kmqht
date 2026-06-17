"""快麦API客户端 - MD5签名与API调用"""

import hashlib
import logging
from typing import Any, Dict, List, Optional

import httpx

from app.config import KUAIMAI_API_BASE, kuaimai_creds
from app.utils.time_utils import beijing_now

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


async def get_supplier_list() -> List[Dict[str, Any]]:
    """获取供应商列表"""
    try:
        result = await _call_api("kuaimai.supplier.list.get")
        return result.get("suppliers", [])
    except Exception as e:
        logger.error(f"查询供应商列表失败: {e}")
        return []


async def get_trade_list(status: str, page: int = 1, page_size: int = 50) -> Dict[str, Any]:
    """获取交易订单列表"""
    try:
        result = await _call_api(
            "kuaimai.trade.list.get",
            {"status": status, "page": page, "page_size": page_size}
        )
        return result
    except Exception as e:
        logger.error(f"查询交易列表失败: {e}")
        return {}


async def get_trade_detail(tid: int) -> Optional[Dict[str, Any]]:
    """获取交易订单详情"""
    try:
        result = await _call_api(
            "kuaimai.trade.detail.get",
            {"tid": tid}
        )
        return result.get("trade")
    except Exception as e:
        logger.error(f"查询交易详情失败 tid={tid}: {e}")
        return None


async def get_delivery_templates() -> List[Dict[str, Any]]:
    """获取物流模板列表"""
    try:
        result = await _call_api("kuaimai.delivery.template.get")
        return result.get("templates", [])
    except Exception as e:
        logger.error(f"查询物流模板失败: {e}")
        return []


async def search_items(keyword: str, page: int = 1, page_size: int = 50) -> Dict[str, Any]:
    """搜索商品"""
    try:
        result = await _call_api(
            "kuaimai.item.search",
            {"keyword": keyword, "page": page, "page_size": page_size}
        )
        return result
    except Exception as e:
        logger.error(f"搜索商品失败 keyword={keyword}: {e}")
        return {}
