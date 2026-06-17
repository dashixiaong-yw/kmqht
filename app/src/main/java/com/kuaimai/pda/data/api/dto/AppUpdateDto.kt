package com.kuaimai.pda.data.api.dto

data class AppVersionResponse(
    val success: Boolean = true,
    val message: String = "操作成功",
    val latestVersion: String = "",
    val downloadUrl: String = "",
    val updateNotes: String = "",
    val forceUpdate: Boolean = false,
    val apkSize: Long = 0,
    val publishedAt: String = ""
)
