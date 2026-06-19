package com.kuaimai.pda.util

import android.net.Uri

/**
 * 扫码配置二维码解析工具
 * 二维码格式：kuaimai://setup?server=<URL>&apikey=<KEY>
 * 兼容纯URL格式：http://192.168.1.100:8900
 */
object SetupQrParser {

    /**
     * 解析扫码配置二维码
     * @param content 扫码内容
     * @return SetupConfig(serverUrl) 或 null
     */
    fun parse(content: String): SetupConfig? {
        // 协议格式：kuaimai://setup?server=xxx&apikey=xxx
        if (content.startsWith(AppConstants.SETUP_SCHEME)) {
            val queryPart = content.substringAfter("?", "")
            if (queryPart.isEmpty()) return null

            var server = ""

            for (param in queryPart.split("&")) {
                val parts = param.split("=", limit = 2)
                if (parts.size == 2 && parts[0] == "server") {
                    server = Uri.decode(parts[1])
                }
            }

            if (server.isNotEmpty()) {
                return SetupConfig(server)
            }
        }

        // 兼容纯URL格式
        if (content.startsWith("http")) {
            return SetupConfig(content)
        }

        return null
    }
}

/**
 * 扫码配置结果
 */
data class SetupConfig(
    val serverUrl: String
)
