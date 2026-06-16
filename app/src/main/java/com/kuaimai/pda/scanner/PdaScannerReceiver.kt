package com.kuaimai.pda.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * PDA扫码广播接收器
 * 接收PDA硬件扫码结果，通过OnBarcodeScannedCallback分发
 * Activity生命周期：onResume注册，onPause注销
 */
class PdaScannerReceiver(
    private val config: PdaDeviceConfig,
    private val callback: OnBarcodeScannedCallback
) : BroadcastReceiver() {

    companion object {
        // iData扫码广播Action和Key
        const val ACTION_IDATA_SCAN = "com.android.server.scannerservice.broadcast"
        const val KEY_IDATA = "data"

        // Urovo扫码广播Action和Key
        const val ACTION_UROVO_SCAN = "android.intent.ACTION_SCANNER_RESULT"
        const val KEY_UROVO = "barcode_string"

        // 新大陆扫码广播Action和Key（与iData相同action，key为data）
        const val ACTION_NEWLAND_SCAN = "com.android.server.scannerservice.broadcast"
        const val KEY_NEWLAND = "data"

        // 通用扫码广播Action和Key
        const val ACTION_GENERIC_SCAN = "com.scanner.broadcast"
        const val KEY_GENERIC = "data"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != config.actionName) return

        val barcode = intent.getStringExtra(config.actionKey) ?: ""
        if (barcode.isNotEmpty()) {
            callback.onBarcodeScanned(barcode)
        }
    }
}
