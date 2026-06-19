package com.kuaimai.pda.util

/**
 * 应用全局常量
 * 集中管理服务器地址、端口等配置常量，避免多处硬编码
 */
object AppConstants {

    /** 后端服务器默认地址（预置FRP内网穿透地址，开箱即用） */
    const val DEFAULT_SERVER_URL = "https://frp-off.com:64623"

    /** 快麦开放平台API基础URL（Retrofit baseUrl必须以/结尾，@POST("router")拼接完整路径） */
    const val KUAIMAI_API_URL = "https://gw.superboss.cc/"

    /** 会话过期预警天数（距过期天数小于此值时显示警告） */
    const val SESSION_WARNING_DAYS = 5

    /** 扫码配置协议前缀 */
    const val SETUP_SCHEME = "kuaimai://setup"
}
