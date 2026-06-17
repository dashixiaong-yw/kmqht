package com.kuaimai.pda.util

/**
 * 应用全局常量
 * 集中管理服务器地址、端口等配置常量，避免多处硬编码
 *
 * 注意：真机部署时需通过扫码配置或手动输入实际服务器地址
 * - 同网络：使用内网IP（如 http://192.168.1.100:8900）
 * - 跨网络：使用Tailscale组网地址
 * - 扫码配置：电脑浏览器访问后端 /setup 页面，PDA扫码自动填入
 */
object AppConstants {

    /** 后端服务器默认地址（空字符串，真机部署需通过扫码或手动配置） */
    const val DEFAULT_SERVER_URL = ""

    /** 快麦开放平台API基础URL（Retrofit baseUrl必须以/结尾，@POST("router")拼接完整路径） */
    const val KUAIMAI_API_URL = "https://gw.superboss.cc/"

    /** 会话过期预警天数（距过期天数小于此值时显示警告） */
    const val SESSION_WARNING_DAYS = 5

    /** 扫码配置协议前缀 */
    const val SETUP_SCHEME = "kuaimai://setup"
}
