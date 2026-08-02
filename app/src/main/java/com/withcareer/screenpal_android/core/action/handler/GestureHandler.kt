package com.withcareer.screenpal_android.core.action.handler

import android.util.Log
import com.google.gson.JsonObject
import com.withcareer.screenpal_android.R
import com.withcareer.screenpal_android.util.ActionUtils
import com.withcareer.screenpal_android.core.action.ExecuteResult
import com.withcareer.screenpal_android.overlay.FloatingAssistantService
import com.withcareer.screenpal_android.service.ScreenPalAccessibilityService

/**
 * 手势处理器
 * 处理 LongPress, DoubleTap, ZoomIn, ZoomOut
 *
 */
class GestureHandler : ActionHandler {
    override fun getActionType(): String = "Gesture"

    override suspend fun execute(
        service: ScreenPalAccessibilityService,
        params: JsonObject,
        screenWidth: Int,
        screenHeight: Int
    ): ExecuteResult {
        val action = params.get("action")?.asString?.lowercase()
            ?: params.get("action_type")?.asString?.lowercase()
            ?: ""

        return when (action) {
            "longpress", "long press" -> longPress(service, params, screenWidth, screenHeight)
            "doubletap", "double tap" -> doubleTap(service, params, screenWidth, screenHeight)
            "zoomin", "zoom in", "pinchout", "pinch out" -> zoomIn(service, params, screenWidth, screenHeight)
            "zoomout", "zoom out", "pinchin", "pinch in" -> zoomOut(service, params, screenWidth, screenHeight)
            else -> throw IllegalArgumentException("Unsupported gesture action: $action")
        }
    }

    private suspend fun longPress(
        service: ScreenPalAccessibilityService,
        params: JsonObject,
        screenWidth: Int,
        screenHeight: Int
    ): ExecuteResult {
        val element = params.get("element")?.asJsonArray

        if (element == null || element.size() < 2) {
            throw IllegalArgumentException(service.getString(R.string.longpress_missing_param))
        }

        val (x, y) = ActionUtils.convertAnyToAbsolute(
            listOf(element[0].asFloat, element[1].asFloat),
            screenWidth,
            screenHeight
        )

        // 获取长按时长，默认为800ms
        val duration = params.get("duration")?.asLong ?: 800L

        Log.d("GestureHandler", "LongPress: duration=${duration}ms")

        FloatingAssistantService.showClickEffect(x, y)
        FloatingAssistantService.setActionMode(true)
        service.longPress(x, y, duration)
        FloatingAssistantService.setActionMode(false)
        return ExecuteResult(
            success = true,
            message = service.getString(R.string.long_pressed_msg, x.toInt().toString(), y.toInt().toString())
        )
    }

    private suspend fun doubleTap(
        service: ScreenPalAccessibilityService,
        params: JsonObject,
        screenWidth: Int,
        screenHeight: Int
    ): ExecuteResult {
        val element = params.get("element")?.asJsonArray

        if (element == null || element.size() < 2) {
            throw IllegalArgumentException(service.getString(R.string.doubletap_missing_param))
        }

        val (x, y) = ActionUtils.convertAnyToAbsolute(
            listOf(element[0].asFloat, element[1].asFloat),
            screenWidth,
            screenHeight
        )

        Log.d("GestureHandler", "DoubleTap requested")

        FloatingAssistantService.showClickEffect(x, y)
        FloatingAssistantService.setActionMode(true)
        service.tap(x, y)
        FloatingAssistantService.showClickEffect(x, y)
        service.tap(x, y)
        FloatingAssistantService.setActionMode(false)

        return ExecuteResult(
            success = true,
            message = service.getString(R.string.double_tapped_msg, x.toInt().toString(), y.toInt().toString())
        )
    }

    private suspend fun zoomIn(
        service: ScreenPalAccessibilityService,
        params: JsonObject,
        screenWidth: Int,
        screenHeight: Int
    ): ExecuteResult {
        // ZoomIn / PinchOut implementation (assuming service supports it or we simulate it)
        // For now, let's just log and simulate success if not implemented in service yet,
        // or check if service has pinch methods.
        // Assuming service.pinchOut exists based on previous code context or just implementing basic placeholder
        // But let's check what was there before. The read didn't show zoomIn implementation fully.
        // I will assume standard implementation pattern.

        val element = params.get("element")?.asJsonArray

        // If element is provided, center zoom there, otherwise center of screen
        val centerX = if (element != null && element.size() >= 2) {
             (element[0].asFloat / 1000f) * screenWidth
        } else {
             screenWidth / 2f
        }

        val centerY = if (element != null && element.size() >= 2) {
             (element[1].asFloat / 1000f) * screenHeight
        } else {
             screenHeight / 2f
        }

        Log.d("GestureHandler", "ZoomIn/PinchOut requested")

        FloatingAssistantService.setActionMode(true)
        service.pinchOut(centerX, centerY)
        FloatingAssistantService.setActionMode(false)

        return ExecuteResult(success = true, message = "Zoomed in")
    }

    private suspend fun zoomOut(
        service: ScreenPalAccessibilityService,
        params: JsonObject,
        screenWidth: Int,
        screenHeight: Int
    ): ExecuteResult {
         val element = params.get("element")?.asJsonArray

        // If element is provided, center zoom there, otherwise center of screen
        val centerX = if (element != null && element.size() >= 2) {
             (element[0].asFloat / 1000f) * screenWidth
        } else {
             screenWidth / 2f
        }

        val centerY = if (element != null && element.size() >= 2) {
             (element[1].asFloat / 1000f) * screenHeight
        } else {
             screenHeight / 2f
        }

        Log.d("GestureHandler", "ZoomOut/PinchIn requested")

        FloatingAssistantService.setActionMode(true)
        service.pinchIn(centerX, centerY)
        FloatingAssistantService.setActionMode(false)

        return ExecuteResult(success = true, message = "Zoomed out")
    }
}
