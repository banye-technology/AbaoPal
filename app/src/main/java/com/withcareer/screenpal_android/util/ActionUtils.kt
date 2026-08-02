package com.withcareer.screenpal_android.util

import com.google.gson.JsonElement

/**
 * 动作工具类
 * 提供坐标转换、时间解析等通用功能
 *
 */
object ActionUtils {
    /**
     * 将相对坐标转换为绝对像素坐标
     * - 兼容 0~1 与 0~1000 两种坐标系
     * - 使用截图的实际尺寸，确保与识别坐标空间一致
     */
    fun convertRelativeToAbsolute(
        element: List<Float>,
        passedScreenWidth: Int,
        passedScreenHeight: Int
    ): Pair<Float, Float> {
        val rawX = element.getOrNull(0) ?: 0f
        val rawY = element.getOrNull(1) ?: 0f
        val isZeroToOne = rawX in 0f..1.5f && rawY in 0f..1.5f
        val x = if (isZeroToOne) rawX * passedScreenWidth else (rawX / 1000f) * passedScreenWidth
        val y = if (isZeroToOne) rawY * passedScreenHeight else (rawY / 1000f) * passedScreenHeight
        return Pair(x, y)
    }

    fun convertAbsoluteToRelative(
        xPx: Float,
        yPx: Float,
        passedScreenWidth: Int,
        passedScreenHeight: Int
    ): Pair<Float, Float> {
        val w = passedScreenWidth.coerceAtLeast(1)
        val h = passedScreenHeight.coerceAtLeast(1)
        val x = (xPx / w) * 1000f
        val y = (yPx / h) * 1000f
        return Pair(x, y)
    }

    fun convertAnyToAbsolute(
        element: List<Float>,
        passedScreenWidth: Int,
        passedScreenHeight: Int
    ): Pair<Float, Float> {
        if (element.size < 2) return Pair(0f, 0f)
        val x = element[0]
        val y = element[1]
        val w = passedScreenWidth.toFloat().coerceAtLeast(1f)
        val h = passedScreenHeight.toFloat().coerceAtLeast(1f)
        val looksLikeAbsolute = (x > 1000f || y > 1000f) && x in 0f..w && y in 0f..h
        return if (looksLikeAbsolute) Pair(x, y) else convertRelativeToAbsolute(element, passedScreenWidth, passedScreenHeight)
    }

    /**
     * 解析持续时间
     * 支持字符串格式（如 "2 seconds"）与数字（毫秒）
     */
    fun parseDurationMillis(durationElement: JsonElement?): Long {
        if (durationElement == null) return 1000L // 默认等待 1 秒

        if (durationElement.isJsonPrimitive) {
            val prim = durationElement.asJsonPrimitive

            // 纯数字：按毫秒处理
            if (prim.isNumber) {
                return prim.asLong.coerceAtLeast(0L)
            }

            // 字符串：解析秒或毫秒
            if (prim.isString) {
                val raw = prim.asString.trim()
                // 支持 "2 seconds" / "1.5 s" / "800 ms" / "1000"
                val regex = Regex("""(?i)(\d+(?:\.\d+)?)\s*(ms|millisecond|milliseconds|s|sec|secs|second|seconds)?""")
                val match = regex.find(raw)
                if (match != null) {
                    val value = match.groupValues[1].toDoubleOrNull() ?: 1.0
                    val unit = match.groupValues.getOrNull(2)?.lowercase()
                    val millis = when (unit) {
                        "ms", "millisecond", "milliseconds" -> value
                        // 默认按秒处理
                        "s", "sec", "secs", "second", "seconds" -> value * 1000
                        else -> {
                            // 无单位则默认视为秒
                            value * 1000
                        }
                    }
                    return millis.toLong().coerceAtLeast(0L)
                }
            }
        }

        // 兜底 1 秒
        return 1000L
    }
}
