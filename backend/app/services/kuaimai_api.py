"""快麦API客户端 - MD5签名与API调用"""

import hashlib
import json
import logging
import threading
from typing import Any, Dict, Optional

import httpx

from app.config import KUAIMAI_API_BASE, kuaimai_config_lock, kuaimai_creds, save_kuaimai_config
from app.utils.time_utils import beijing_now, format_beijing

logger = logging.getLogger(__name__)

# HTTP客户端超时配置
_TIMEOUT = 30.0

# 模块级httpx客户端（连接池复用）
_client: Optional[httpx.AsyncClient] = None
_client_lock = threading.Lock()


def _get_client() -> httpx.AsyncClient:
    global _client
    if _client is None:
        with _client_lock:
            if _client is None:
                _client = httpx.AsyncClient(timeout=_TIMEOUT)
    return _client


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
    """构建公共请求参数（参数名与快麦开放平台官方文档一致）"""
    with kuaimai_config_lock:
        return {
            "appKey": kuaimai_creds.app_key,
            "method": method,
            "session": kuaimai_creds.session,
            "timestamp": beijing_now().strftime("%Y-%m-%d %H:%M:%S"),
            "format": "json",
            "version": "1.0",
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

    # 确保参数值为字符串（快麦API要求复杂参数如JSON数组/对象以JSON字符串格式传递）
    for key in list(params.keys()):
        if not isinstance(params[key], str):
            params[key] = json.dumps(params[key], ensure_ascii=False)

    # 在锁内快照凭证用于签名
    with kuaimai_config_lock:
        secret_snapshot = kuaimai_creds.app_secret

    # 生成签名
    params["sign"] = _sign(params, secret_snapshot)

    try:
        client = _get_client()
        response = await client.post(KUAIMAI_API_BASE, data=params)
        response.raise_for_status()
        result: Dict[str, Any] = response.json()

        # 检查API错误
        if "error_response" in result:
            error = result["error_response"]
            err_msg = f"快麦API错误: code={error.get('code')}, msg={error.get('zh_desc', error.get('msg'))}"
            logger.error(err_msg)
            raise ValueError(err_msg)

        # 提取API响应数据
        # V1: 响应包装在 {method}_response 中
        # V2: 响应直接在顶层（无包装）
        wrapper_key = f"{method.replace('.', '_')}_response"
        if wrapper_key in result:
            api_response = result[wrapper_key]
        else:
            api_response = result
        if not api_response:
            logger.warning(f"快麦API响应为空")

        return api_response if api_response else result
    except httpx.TimeoutException as e:
        logger.error(f"快麦API请求超时: {e}")
        raise
    except httpx.HTTPStatusError as e:
        logger.error(f"快麦API请求失败: status={e.response.status_code}")
        raise
    except httpx.RequestError as e:
        logger.error(f"快麦API网络错误: {e}")
        raise


# ==================== 1个API方法 ====================

async def get_sku_by_outer_id(sku_outer_id: str) -> Optional[Dict[str, Any]]:
    """
    根据外部编码获取SKU信息（V2）
    两步查询：erp.item.single.sku.get → itemSku → 如有供应商再查 item.supplier.list.get
    """
    try:
        # Step1: 查SKU基本信息
        result = await _call_api(
            "erp.item.single.sku.get",
            {"skuOuterId": sku_outer_id}
        )
        sku_list = result.get("itemSku", [])
        if not sku_list:
            return None
        sku_data = sku_list[0]

        # 映射 V2 camelCase → 内部 snake_case
        mapped = {
            "properties_name": sku_data.get("propertiesName", ""),
            "pic_path": sku_data.get("skuPicPath", ""),
            "remark": sku_data.get("skuRemark", ""),
            "sys_item_id": sku_data.get("sysItemId", 0),
            "sys_sku_id": sku_data.get("sysSkuId", 0),
            "item_outer_id": sku_data.get("itemOuterId", ""),
            "supplier_name": "",
            "supplier_code": "",
        }

        logger.info(f"快麦SKU数据 sku={sku_outer_id}: propertiesName={sku_data.get('propertiesName','')!r}, "
                    f"skuPicPath={sku_data.get('skuPicPath','')!r}, hasSupplier={sku_data.get('hasSupplier')}")

        # Step2: 如有供应商，查供应商信息
        if sku_data.get("hasSupplier") == 1 or str(sku_data.get("hasSupplier", "0")) == "1":
            supplier_result = await _call_api(
                "item.supplier.list.get",
                {"sysSkuIds": str(mapped["sys_sku_id"])}
            )
            suppliers = supplier_result.get("suppliers", [])
            if suppliers:
                mapped["supplier_name"] = suppliers[0].get("supplierName", "")
                mapped["supplier_code"] = suppliers[0].get("supplierCode", "")

        return mapped
    except Exception as e:
        logger.error(f"查询SKU失败 sku_outer_id={sku_outer_id}: {e}")
        return None


async def get_supplier_list() -> Optional[list]:
    """获取快麦供应商列表（含编码，走采购模块 supplier.list.query）"""
    if not kuaimai_creds.is_valid():
        raise ValueError("快麦凭证未配置")
    try:
        params = _build_common_params("supplier.list.query")
        params["pageNo"] = "1"
        params["pageSize"] = "200"
        with kuaimai_config_lock:
            secret_snapshot = kuaimai_creds.app_secret
        params["sign"] = _sign(params, secret_snapshot)

        client = _get_client()
        files = {key: (None, str(value)) for key, value in params.items()}
        response = await client.post(KUAIMAI_API_BASE, files=files)
        response.raise_for_status()
        result: Dict[str, Any] = response.json()
        if "error_response" in result:
            logger.error(f"快麦供应商列表API错误: {result['error_response']}")
            return None
        return result.get("supplier_list_query_response", result).get("list", [])
    except Exception as e:
        logger.error(f"获取供应商列表失败: {e}")
        return None


# ==================== 会话刷新 ====================

async def refresh_session() -> bool:
    """
    调用快麦开放平台刷新会话接口(open.token.refresh)
    刷新成功后accessToken和refreshToken值不变，仅延长30天有效期
    不通过_call_api通用逻辑，因为响应结构不同
    :return: 刷新是否成功
    """
    if not kuaimai_creds.has_refresh_token():
        logger.warning("refreshToken未配置，无法自动刷新session")
        return False

    try:
        # 在锁内快照凭证
        with kuaimai_config_lock:
            refresh_token = kuaimai_creds.refresh_token
            secret_snapshot = kuaimai_creds.app_secret

        # 构建请求参数（与_call_api一致但不走通用响应解析）
        params = _build_common_params("open.token.refresh")
        params["refreshToken"] = refresh_token
        params["sign"] = _sign(params, secret_snapshot)
        client = _get_client()
        files = {key: (None, str(value)) for key, value in params.items()}
        response = await client.post(KUAIMAI_API_BASE, files=files)
        response.raise_for_status()
        result: Dict[str, Any] = response.json()

        # 检查错误响应
        if "error_response" in result:
            error = result["error_response"]
            logger.error(f"刷新会话API错误: code={error.get('code')}, msg={error.get('zh_desc', error.get('msg'))}")
            return False

        # 解析响应：优先查找 open_token_refresh_response，其次直接查找 session
        session_data = {}
        response_key = "open_token_refresh_response"
        if response_key in result:
            session_data = result[response_key].get("session", {})
        elif "session" in result:
            session_data = result["session"]

        if not session_data:
            logger.error("刷新会话响应中缺少session数据")
            return False

        # 更新凭证（刷新后token值不变，但更新updated_at记录刷新时间）
        now = beijing_now()
        with kuaimai_config_lock:
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
