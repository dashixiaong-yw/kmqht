package com.kuaimai.pda.scanner

import android.os.Build

/**
 * PDA设备扫码配置
 * 4种设备配置：iData、Urovo、新大陆、通用
 * 支持根据Build.MANUFACTURER自动识别设备
 */
data class PdaDeviceConfig(
    val deviceName: String,
    val actionName: String,
    val actionKey: String
) {

    companion object {
        /** iData设备配置 */
        val IDATA = PdaDeviceConfig(
            deviceName = "iData",
            actionName = "com.android.server.scannerservice.broadcast",
            actionKey = "data"
        )

        /** Urovo设备配置 */
        val UROVO = PdaDeviceConfig(
            deviceName = "Urovo",
            actionName = "android.intent.ACTION_SCANNER_RESULT",
            actionKey = "barcode_string"
        )

        /** 新大陆设备配置 */
        val NEWLAND = PdaDeviceConfig(
            deviceName = "新大陆",
            actionName = "com.android.server.scannerservice.broadcast",
            actionKey = "data"
        )

        /** 通用设备配置 */
        val GENERIC = PdaDeviceConfig(
            deviceName = "通用",
            actionName = "com.scanner.broadcast",
            actionKey = "data"
        )

        /** 支持的PDA设备配置列表 */
        val CONFIGS = listOf(IDATA, UROVO, NEWLAND, GENERIC)

        /** 默认配置（通用） */
        val DEFAULT_CONFIG = GENERIC

        /**
         * 根据Build.MANUFACTURER自动识别设备配置
         * @return 匹配的设备配置，未匹配则返回通用配置
         */
        fun autoDetect(): PdaDeviceConfig {
            val manufacturer = Build.MANUFACTURER.lowercase()
            return when {
                manufacturer.contains("idata") -> IDATA
                manufacturer.contains("urovo") -> UROVO
                manufacturer.contains("newland") || manufacturer.contains("nls") -> NEWLAND
                else -> DEFAULT_CONFIG
            }
        }

        /**
         * 根据设备名称获取配置
         * @param name 设备名称
         * @return 匹配的设备配置，未匹配则返回通用配置
         */
        fun getByName(name: String): PdaDeviceConfig {
            return CONFIGS.find { it.deviceName == name } ?: DEFAULT_CONFIG
        }
    }
}
