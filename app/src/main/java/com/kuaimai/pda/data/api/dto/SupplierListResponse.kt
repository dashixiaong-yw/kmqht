package com.kuaimai.pda.data.api.dto

/**
 * 供应商信息（本地模型，用于展示和选择）
 */
data class SupplierDto(
    val supplierName: String = "",
    val supplierCode: String = "",
    val supplierId: Long = 0
)
