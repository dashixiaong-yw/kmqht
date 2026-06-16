package com.kuaimai.pda.data.api.dto

/**
 * 供应商列表查询响应 erp.item.supplier.list.get / supplier.list.query
 */
data class SupplierListResponse(
    val code: Int = 0,
    val msg: String = "",
    val suppliers: List<SupplierDto> = emptyList()
)

data class SupplierDto(
    val supplierName: String = "",
    val supplierCode: String = ""
)
