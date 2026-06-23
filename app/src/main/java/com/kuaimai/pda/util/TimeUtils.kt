package com.kuaimai.pda.util

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * 时间工具类
 * 所有时间使用北京时间（UTC+8），时间戳使用Long类型
 * SimpleDateFormat线程安全：通过ThreadLocal包装，每个线程独立实例
 */
object TimeUtils {

    private const val BEIJING_ZONE_ID = "Asia/Shanghai"

    /** 取货单默认过期时间：12小时（毫秒） */
    const val DEFAULT_EXPIRE_MS: Long = 12 * 60 * 60 * 1000L

    /** 已完成取货单查询范围：7天（毫秒） */
    const val COMPLETED_ORDER_RANGE_MS: Long = 7 * 24 * 60 * 60 * 1000L

    private val beijingZone: TimeZone = TimeZone.getTimeZone(BEIJING_ZONE_ID)

    /** 日期时间格式（使用ThreadLocal保证线程安全） */
    private val dateTimeFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).apply {
                timeZone = beijingZone
            }
        }
    }

    /** 日期格式 */
    private val dateFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).apply {
                timeZone = beijingZone
            }
        }
    }

    /** 时间格式 */
    private val timeFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("HH:mm:ss", Locale.CHINA).apply {
                timeZone = beijingZone
            }
        }
    }

    /**
     * 格式化时间戳为日期时间字符串
     * @param timestamp 毫秒级时间戳
     * @return 格式化后的字符串，如 "2026-06-15 14:30:00"
     */
    fun formatTimestamp(timestamp: Long): String {
        if (timestamp <= 0) return ""
        return dateTimeFormat.get().format(timestamp)
    }

    /**
     * 格式化时间戳为日期字符串
     * @param timestamp 毫秒级时间戳
     * @return 格式化后的字符串，如 "2026-06-15"
     */
    fun formatDate(timestamp: Long): String {
        if (timestamp <= 0) return ""
        return dateFormat.get().format(timestamp)
    }

    /**
     * 格式化时间戳为时间字符串
     * @param timestamp 毫秒级时间戳
     * @return 格式化后的字符串，如 "14:30:00"
     */
    fun formatTime(timestamp: Long): String {
        if (timestamp <= 0) return ""
        return timeFormat.get().format(timestamp)
    }

    /**
     * 获取当前北京时间戳
     */
    fun now(): Long {
        return System.currentTimeMillis()
    }

    /**
     * 解析北京时间字符串为毫秒时间戳
     * @param timeStr 北京时间字符串，格式 "yyyy-MM-dd HH:mm:ss"
     * @return 毫秒级时间戳，解析失败返回0
     */
    fun parseBeijingTime(timeStr: String): Long {
        if (timeStr.isBlank()) return 0L
        return try {
            dateTimeFormat.get().parse(timeStr)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 解析北京时间字符串为毫秒时间戳（空值安全）
     */
    fun parseBeijingTimeOrNull(timeStr: String?): Long? {
        if (timeStr.isNullOrBlank()) return null
        val result = parseBeijingTime(timeStr)
        return if (result > 0) result else null
    }

    /**
     * 计算两个时间戳之间的分钟差
     */
    fun diffMinutes(start: Long, end: Long): Long {
        return (end - start) / (60 * 1000)
    }

    /**
     * JSON字符串转义（防止双引号等特殊字符破坏JSON格式）
     */
    fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
