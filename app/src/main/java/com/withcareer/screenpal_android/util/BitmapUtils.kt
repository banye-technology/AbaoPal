package com.withcareer.screenpal_android.util

import android.graphics.Bitmap
import android.os.Build
import android.util.Log

object BitmapUtils {
    /**
     * 检查位图是否全黑或几乎全黑
     * @param bitmap 要检查的位图
     * @param threshold 阈值，如果黑色像素比例超过此值，认为是全黑（默认 0.98，即 98%）
     * @return true 如果位图是全黑的
     */
    fun isBitmapBlack(bitmap: Bitmap, threshold: Double = 0.98): Boolean {
        if (bitmap.width == 0 || bitmap.height == 0) {
            Log.w("BitmapUtils", "Bitmap 尺寸为 0")
            return true
        }

        // 如果 Bitmap 是 HARDWARE 格式，需要先转换
        val accessibleBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
            Log.d("BitmapUtils", "转换 HARDWARE Bitmap 为 ARGB_8888")
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            null
        }

        val targetBitmap = accessibleBitmap ?: bitmap

        try {
            // 采样检查，使用更多的采样点以获得更准确的结果
            // 在 1080x1920 的屏幕上，采样约 100 个点
            val stepX = maxOf(1, targetBitmap.width / 10)
            val stepY = maxOf(1, targetBitmap.height / 10)

            var blackPixels = 0
            var totalPixels = 0
            var minR = 255
            var minG = 255
            var minB = 255
            var maxR = 0
            var maxG = 0
            var maxB = 0

            for (y in 0 until targetBitmap.height step stepY) {
                for (x in 0 until targetBitmap.width step stepX) {
                    val pixel = targetBitmap.getPixel(x, y)
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF

                    // 记录 RGB 值的范围
                    minR = minOf(minR, r)
                    minG = minOf(minG, g)
                    minB = minOf(minB, b)
                    maxR = maxOf(maxR, r)
                    maxG = maxOf(maxG, g)
                    maxB = maxOf(maxB, b)

                    // 如果 RGB 值都很低（小于 10），认为是黑色
                    if (r < 10 && g < 10 && b < 10) {
                        blackPixels++
                    }
                    totalPixels++
                }
            }

            val blackRatio = blackPixels.toDouble() / totalPixels

            val isBlack = blackRatio >= threshold
            if (isBlack) {
                Log.w("BitmapUtils", "检测到全黑截图")
            }

            return isBlack
        } finally {
            // 如果创建了临时 Bitmap，需要回收
            accessibleBitmap?.recycle()
        }
    }

    /**
     * 缩放 Bitmap
     * @param bitmap 原图
     * @param maxDimension 最大边长
     */
    fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // 如果不需要缩放，直接返回原图
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }

        val ratio = minOf(maxDimension.toFloat() / width, maxDimension.toFloat() / height)
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        Log.d("BitmapUtils", "Resizing bitmap")

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * 将 Bitmap 转换为 Base64 字符串 (JPEG 格式)
     * @param bitmap 源 Bitmap
     * @param quality 压缩质量 (0-100)
     * @return Base64 字符串
     */
    fun bitmapToBase64(bitmap: Bitmap, quality: Int = 80): String {
        val outputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val byteArray = outputStream.toByteArray()
        return android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
    }

    fun dHash64(bitmap: Bitmap): Long {
        if (bitmap.width <= 0 || bitmap.height <= 0) return 0L

        val accessibleBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            null
        }
        val targetBitmap = accessibleBitmap ?: bitmap
        var scaled: Bitmap? = null
        try {
            scaled = Bitmap.createScaledBitmap(targetBitmap, 9, 8, true)
            var hash = 0L
            var bitIndex = 0
            for (y in 0 until 8) {
                for (x in 0 until 8) {
                    val left = scaled.getPixel(x, y)
                    val right = scaled.getPixel(x + 1, y)
                    val lLum = ((left shr 16) and 0xFF) * 3 + ((left shr 8) and 0xFF) * 6 + (left and 0xFF)
                    val rLum = ((right shr 16) and 0xFF) * 3 + ((right shr 8) and 0xFF) * 6 + (right and 0xFF)
                    if (lLum > rLum) {
                        hash = hash or (1L shl bitIndex)
                    }
                    bitIndex++
                }
            }
            return hash
        } catch (_: Exception) {
            return 0L
        } finally {
            scaled?.recycle()
            accessibleBitmap?.recycle()
        }
    }

    fun hammingDistance(a: Long, b: Long): Int {
        return java.lang.Long.bitCount(a xor b)
    }
}
