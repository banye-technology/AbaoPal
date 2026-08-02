package com.withcareer.screenpal_android.core.action.handler

import android.util.Log
import com.google.gson.JsonObject
import com.withcareer.screenpal_android.R
import com.withcareer.screenpal_android.core.action.ExecuteResult
import com.withcareer.screenpal_android.overlay.FloatingAssistantService
import com.withcareer.screenpal_android.service.ScreenPalAccessibilityService

/**
 * 滚动动作处理器
 * 支持动态参数配置
 *
 */
class ScrollHandler : ActionHandler {

    override fun getActionType(): String = "Scroll"

    override suspend fun execute(
        service: ScreenPalAccessibilityService,
        params: JsonObject,
        screenWidth: Int,
        screenHeight: Int
    ): ExecuteResult {
        val direction = params.get("direction")?.asString?.lowercase() ?: "down"

        // 动态参数处理
        // 默认改为 0.2，避免触碰屏幕边缘（如导航栏、状态栏）导致手势无效
        var distanceRatio = 0.2f
        if (params.has("distance_ratio")) {
             try {
                 distanceRatio = params.get("distance_ratio").asFloat.coerceIn(0.1f, 0.9f)
             } catch (e: Exception) {
                 Log.w("ScrollHandler", "Invalid distance_ratio", e)
             }
        }

        var distancePixels: Int? = null
        if (params.has("distance_pixels")) {
            try {
                distancePixels = params.get("distance_pixels").asInt
            } catch (e: Exception) {
                // ignore
            }
        }

        val duration = if (params.has("duration")) params.get("duration").asLong.coerceIn(100L, 2000L) else 300L

        val centerX = screenWidth / 2f
        val centerY = screenHeight / 2f

        var startX: Float
        var startY: Float
        var endX: Float
        var endY: Float

        val xOffset = distancePixels?.toFloat() ?: (screenWidth * distanceRatio)
        val yOffset = distancePixels?.toFloat() ?: (screenHeight * distanceRatio)

        when (direction) {
            "down", "bottom" -> {
                // 向下滑动内容 = 手指向上拖动
                startX = centerX
                startY = centerY + yOffset
                endX = centerX
                endY = centerY - yOffset
            }
            "up", "top" -> {
                // 向上滑动内容 = 手指向下拖动
                startX = centerX
                startY = centerY - yOffset
                endX = centerX
                endY = centerY + yOffset
            }
            "left" -> {
                // 向左滑动内容 = 手指向右拖动
                startX = centerX - xOffset
                startY = centerY
                endX = centerX + xOffset
                endY = centerY
            }
            "right" -> {
                // 向右滑动内容 = 手指向左拖动
                startX = centerX + xOffset
                startY = centerY
                endX = centerX - xOffset
                endY = centerY
            }
            else -> throw IllegalArgumentException(service.getString(R.string.invalid_scroll_direction, direction))
        }

        // 安全边距：屏幕宽高的 5%
        val safeMarginW = screenWidth * 0.05f
        val safeMarginH = screenHeight * 0.05f

        // 坐标越界保护 + 安全边距
        startX = startX.coerceIn(safeMarginW, screenWidth - safeMarginW)
        startY = startY.coerceIn(safeMarginH, screenHeight - safeMarginH)
        endX = endX.coerceIn(safeMarginW, screenWidth - safeMarginW)
        endY = endY.coerceIn(safeMarginH, screenHeight - safeMarginH)

        Log.d("ScrollHandler", "Scroll requested")

        FloatingAssistantService.showSwipeEffect(startX, startY, endX, endY)
        FloatingAssistantService.setActionMode(true)
        service.swipe(startX, startY, endX, endY, duration)
        FloatingAssistantService.setActionMode(false)

        return ExecuteResult(
            success = true,
            message = service.getString(R.string.scrolled_msg, direction)
        )
    }
}
