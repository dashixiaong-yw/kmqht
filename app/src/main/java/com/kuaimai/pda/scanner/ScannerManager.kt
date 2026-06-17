package com.kuaimai.pda.scanner

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 扫码反馈类型
 */
enum class ScanFeedbackType {
    SUCCESS, FAILURE, DUPLICATE
}

/**
 * PDA扫码管理器
 * 统一管理不同品牌PDA的扫码广播接收
 * 支持震动和声音反馈
 */
@Singleton
class ScannerManager @Inject constructor() {

    private val _scanResult = MutableStateFlow("")
    val scanResult: StateFlow<String> = _scanResult

    private var receiver: BroadcastReceiver? = null
    private var isRegistered: Boolean = false

    /** 300ms防抖：上次扫码时间戳 */
    private var lastScanTime: Long = 0L

    companion object {
        /** 防抖间隔：300ms */
        private const val DEBOUNCE_INTERVAL_MS = 300L
    }

    // 反馈设置
    private var vibrationEnabled: Boolean = true
    private var soundEnabled: Boolean = true

    // 震动和声音
    private var vibrator: Vibrator? = null
    private var soundPool: SoundPool? = null
    private var successSoundId: Int = 0
    private var errorSoundId: Int = 0

    /**
     * 注册PDA扫码广播接收器
     * @param context 上下文
     */
    fun register(context: Context) {
        if (isRegistered) return

        // 初始化震动器
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        // 初始化声音池
        soundPool = SoundPool.Builder().setMaxStreams(2).build()
        successSoundId = loadSoundUri(context, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
        errorSoundId = loadSoundUri(context, android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI)

        val config = PdaDeviceConfig.autoDetect()
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val barcode = intent.getStringExtra(config.actionKey) ?: ""
                if (barcode.isNotEmpty()) {
                    // 300ms防抖
                    val now = System.currentTimeMillis()
                    if (now - lastScanTime < DEBOUNCE_INTERVAL_MS) return
                    lastScanTime = now
                    _scanResult.value = barcode
                }
            }
        }

        val filter = IntentFilter(config.actionName)
        if (Build.VERSION.SDK_INT >= 26) {
            val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Context.RECEIVER_EXPORTED
            } else {
                0
            }
            context.registerReceiver(receiver, filter, flag)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        isRegistered = true
    }

    /**
     * 注销PDA扫码广播接收器
     * @param context 上下文
     */
    fun unregister(context: Context) {
        if (!isRegistered) return
        receiver?.let { context.unregisterReceiver(it) }
        receiver = null
        isRegistered = false

        // 释放声音池
        soundPool?.release()
        soundPool = null
    }

    /**
     * 清除扫码结果
     */
    fun clearResult() {
        _scanResult.value = ""
    }

    /**
     * 触发扫码反馈
     * @param context 上下文
     * @param type 反馈类型
     */
    fun provideFeedback(context: Context, type: ScanFeedbackType) {
        if (vibrationEnabled) {
            vibrate(type)
        }
        if (soundEnabled) {
            playSound(type)
        }
    }

    /**
     * 设置震动开关
     */
    fun setVibrationEnabled(enabled: Boolean) {
        vibrationEnabled = enabled
    }

    /**
     * 设置声音开关
     */
    fun setSoundEnabled(enabled: Boolean) {
        soundEnabled = enabled
    }

    /**
     * 通过URI加载系统提示音到SoundPool
     */
    private fun loadSoundUri(context: Context, uri: Uri): Int {
        val sp = soundPool ?: return 0
        return try {
            val fd = context.contentResolver.openAssetFileDescriptor(uri, "r") ?: return 0
            fd.use { afd -> sp.load(afd, 1) }
        } catch (e: Exception) {
            Log.w("ScannerManager", "加载提示音失败: ${e.message}")
            0
        }
    }

    /**
     * 震动反馈
     */
    private fun vibrate(type: ScanFeedbackType) {
        val vib = vibrator ?: return
        val durationMs = when (type) {
            ScanFeedbackType.SUCCESS -> 50L
            ScanFeedbackType.FAILURE -> 200L
            ScanFeedbackType.DUPLICATE -> 100L
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(durationMs)
        }
    }

    /**
     * 声音反馈
     */
    private fun playSound(type: ScanFeedbackType) {
        val pool = soundPool ?: return
        val soundId = when (type) {
            ScanFeedbackType.SUCCESS -> successSoundId
            ScanFeedbackType.FAILURE -> errorSoundId
            ScanFeedbackType.DUPLICATE -> successSoundId
        }
        if (soundId != 0) {
            pool.play(soundId, 0.5f, 0.5f, 1, 0, 1.0f)
        }
    }
}
