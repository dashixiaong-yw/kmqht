package com.kuaimai.pda.util

/**
 * 应用全局常量
 * 集中管理服务器地址、端口等配置常量，避免多处硬编码
 *
 * 注意：真机部署时需修改DEFAULT_SERVER_URL为实际服务器地址
 * - 同网络：使用内网IP（如 http://192.168.1.100:8000）
 * - 跨网络：使用Tailscale组网地址
 */
object AppConstants {

    /** 后端服务器默认地址（10.0.2.2为Android模拟器访问宿主机的特殊IP） */
    const val DEFAULT_SERVER_URL = "http://10.0.2.2:8000"

    /** 快麦开放平台API地址 */
    const val KUAIMAI_API_URL = "https://openapi.kuaimai.com/router"

    /** 会话过期预警天数（距过期天数小于此值时显示警告） */
    const val SESSION_WARNING_DAYS = 5
}
