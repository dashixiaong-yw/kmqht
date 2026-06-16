package com.kuaimai.pda.scanner

/**
 * PDA扫码回调接口
 * 统一PDA硬件扫码和ML Kit相机扫码的回调
 */
interface OnBarcodeScannedCallback {

    /**
     * 扫码结果回调
     * @param barcode 扫描到的条码字符串
     */
    fun onBarcodeScanned(barcode: String)
}
