"""数据模型 - Pydantic请求/响应模型"""

from datetime import datetime
from enum import Enum
from typing import List, Optional

from pydantic import BaseModel, Field


# ==================== 通用模型 ====================

class BaseResponse(BaseModel):
    """通用响应模型"""
    success: bool = True
    message: str = "操作成功"


class ImageType(str, Enum):
    """图片类型枚举"""
    AREA = "area"
    BOX = "box"


# ==================== 拣货区模型 ====================

class AreaRequest(BaseModel):
    """创建拣货区请求"""
    name: str = Field(..., min_length=1, max_length=32, description="拣货区名称")


class AreaResponse(BaseModel):
    """拣货区响应"""
    id: int
    name: str
    createdAt: str


class AreaListResponse(BaseModel):
    """拣货区列表响应"""
    success: bool = True
    message: str = "操作成功"
    data: List[AreaResponse] = []


# ==================== 取货单模型 ====================

class CreateOrderRequest(BaseModel):
    """创建取货单请求"""
    areaName: str = Field(..., min_length=1, max_length=32, description="拣货区名称")


class ItemResponse(BaseModel):
    """取货明细响应"""
    id: int
    skuOuterId: str
    sysItemId: int
    sysSkuId: int
    propertiesName: str = ""
    picPath: str = ""
    status: int = 0
    supplierName: str = ""
    supplierCode: str = ""
    remark: str = ""
    createdAt: str
    completedAt: Optional[str] = None


class OrderResponse(BaseModel):
    """取货单响应"""
    id: int
    orderNo: str
    status: int = 0
    completionType: int = 0
    totalCount: int = 0
    completedCount: int = 0
    createdAt: str
    completedAt: Optional[str] = None
    expireAt: str


class OrderDetailResponse(BaseModel):
    """取货单详情响应（含明细）"""
    id: int
    orderNo: str
    status: int = 0
    completionType: int = 0
    totalCount: int = 0
    completedCount: int = 0
    createdAt: str
    completedAt: Optional[str] = None
    expireAt: str
    items: List[ItemResponse] = []


class OrderListResponse(BaseModel):
    """取货单列表响应"""
    success: bool = True
    message: str = "操作成功"
    data: List[OrderResponse] = []


class AddItemRequest(BaseModel):
    """添加取货明细请求"""
    skuOuterId: str = Field(..., min_length=1, max_length=64, description="SKU外部编码")


# ==================== 图片模型 ====================

class UploadImageRequest(BaseModel):
    """上传图片请求（表单字段）"""
    skuOuterId: str = Field(..., min_length=1, max_length=64, description="SKU外部编码")
    imageType: ImageType = Field(..., description="图片类型: area/box")


class ImageResponse(BaseModel):
    """图片响应"""
    id: int
    skuOuterId: str
    imageType: str
    imageUrl: str
    filePath: str
    createdAt: str


class ImageListResponse(BaseModel):
    """图片列表响应"""
    success: bool = True
    message: str = "操作成功"
    data: List[ImageResponse] = []


# ==================== 系统模型 ====================

class CrashReportRequest(BaseModel):
    """崩溃报告请求"""
    appVersion: str = Field(..., max_length=32, description="应用版本号")
    deviceModel: str = Field(..., max_length=64, description="设备型号")
    errorMessage: str = Field(..., description="错误消息")
    stackTrace: str = Field(..., description="堆栈跟踪")


class AppVersionResponse(BaseModel):
    """应用版本响应"""
    success: bool = True
    message: str = "操作成功"
    latestVersion: str = ""
    downloadUrl: str = ""


class HealthResponse(BaseModel):
    """健康检查响应"""
    status: str = "ok"
    database: str = "ok"
