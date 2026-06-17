package com.kuaimai.pda.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * 图片压缩工具类
 * 上传前压缩到1024px宽/质量80%（约200KB）
 */
object ImageCompressor {

    private const val TAG = "ImageCompressor"

    /** 最大宽度（像素） */
    private const val MAX_WIDTH = 1024

    /** JPEG压缩质量（0-100） */
    private const val JPEG_QUALITY = 80

    /**
     * 压缩图片文件
     * 如果图片宽度超过MAX_WIDTH，等比缩放后以JPEG_QUALITY质量重新编码
     * @param sourceFile 原始图片文件
     * @return 压缩后的临时文件（调用方负责删除）
     */
    fun compress(sourceFile: File): File {
        // 读取图片尺寸（不加载全图到内存）
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(sourceFile.absolutePath, options)

        val originalWidth = options.outWidth
        val originalHeight = options.outHeight

        // 如果图片宽度未超过限制且文件较小，直接返回原文件
        if (originalWidth <= MAX_WIDTH && sourceFile.length() <= 200 * 1024) {
            Log.d(TAG, "图片无需压缩: ${sourceFile.length() / 1024}KB, ${originalWidth}x${originalHeight}")
            return sourceFile
        }

        // 计算采样率（inSampleSize必须是2的幂）
        val sampleSize = calculateSampleSize(originalWidth, originalHeight)

        // 按采样率加载图片
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOptions)
            ?: return sourceFile

        // 如果采样后仍超过最大宽度，进一步缩放
        val scaledBitmap = if (bitmap.width > MAX_WIDTH) {
            val scaleFactor = MAX_WIDTH.toFloat() / bitmap.width
            val newHeight = (bitmap.height * scaleFactor).toInt()
            Bitmap.createScaledBitmap(bitmap, MAX_WIDTH, newHeight, true).also {
                if (it !== bitmap) bitmap.recycle()
            }
        } else {
            bitmap
        }

        // 写入临时文件
        val compressedFile = File.createTempFile("compressed_", ".jpg", sourceFile.parentFile)
        val fos = FileOutputStream(compressedFile)
        try {
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos)
            fos.flush()
        } finally {
            fos.close()
            scaledBitmap.recycle()
        }

        Log.d(
            TAG,
            "图片压缩完成: ${sourceFile.length() / 1024}KB → ${compressedFile.length() / 1024}KB, " +
                    "${originalWidth}x${originalHeight} → ${scaledBitmap.width}x${scaledBitmap.height}"
        )

        return compressedFile
    }

    /**
     * 计算采样率
     * 采样率必须是2的幂，确保采样后图片宽度接近但不超过MAX_WIDTH的2倍
     */
    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        val targetWidth = MAX_WIDTH * 2 // 采样后宽度约为目标2倍，后续再精确缩放
        while (width / (sampleSize * 2) >= targetWidth) {
            sampleSize *= 2
        }
        return sampleSize
    }
}
