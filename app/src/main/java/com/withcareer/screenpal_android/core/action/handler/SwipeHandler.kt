package com.withcareer.screenpal_android.core.action.handler

import android.util.Log
import com.google.gson.JsonObject
import com.withcareer.screenpal_android.R
import com.withcareer.screenpal_android.util.ActionUtils
import com.withcareer.screenpal_android.core.action.ExecuteResult
import com.withcareer.screenpal_android.overlay.FloatingAssistantService
import com.withcareer.screenpal_android.service.ScreenPalAccessibilityService

/**
 * 滑动动作处理器
 * 处理 Swipe 和 TapAndSwipe
 *
 */
class SwipeHandler : ActionHandler {
    override fun getActionType(): String = "Swipe"

    override suspend fun execute(
        service: ScreenPalAccessibilityService,
        params: JsonObject,
        screenWidth: Int,
        screenHeight: Int
    ): ExecuteResult {
        val action = params.get("action")?.asString?.lowercase() ?: "swipe"

        // 检查是否包含完整轨迹
        val pathArray = params.get("path")?.asJsonArray

        if (pathArray != null && pathArray.size() >= 2) {
            val firstElement = pathArray[0]
            val pathPoints: List<Triple<Float, Float, Long>>

            // 增强判断逻辑：如果是 JsonObject 且包含时间戳字段，则认为是新格式
            val isNewFormat = firstElement.isJsonObject &&
                (firstElement.asJsonObject.has("t") || firstElement.asJsonObject.has("time") || firstElement.asJsonObject.has("timestamp"))

            if (isNewFormat) {
                Log.d("SwipeHandler", "Parsing swipe path with NEW format (timed)")
                // 包含时间戳的对象格式 [{"x":0.1, "y":0.2, "t":0}, ...]
                pathPoints = pathArray.map { element ->
                    val obj = element.asJsonObject
                    val xRaw = obj.get("x").asFloat
                    val yRaw = obj.get("y").asFloat
                    val t = obj.get("t")?.asLong
                        ?: obj.get("time")?.asLong
                        ?: obj.get("timestamp")?.asLong
                        ?: 0L

                    val (x, y) = ActionUtils.convertRelativeToAbsolute(
                        listOf(xRaw, yRaw),
                        screenWidth,
                        screenHeight
                    )
                    Triple(x, y, t)
                }
            } else {
                Log.w("SwipeHandler", "Parsing swipe path with legacy uniform-speed format")
                // 旧格式：数组格式 [[0.1, 0.2], ...] 或不带时间戳的对象
                // 需要根据总 duration 估算每个点的时间戳
                val duration = params.get("duration")?.asLong ?: 300L
                val count = pathArray.size()

                pathPoints = pathArray.mapIndexed { index, element ->
                    val (xRaw, yRaw) = if (element.isJsonArray) {
                        val arr = element.asJsonArray
                        Pair(arr[0].asFloat, arr[1].asFloat)
                    } else if (element.isJsonObject) {
                        val obj = element.asJsonObject
                        Pair(obj.get("x").asFloat, obj.get("y").asFloat)
                    } else {
                        Pair(0f, 0f)
                    }

                    val (x, y) = ActionUtils.convertRelativeToAbsolute(
                        listOf(xRaw, yRaw),
                        screenWidth,
                        screenHeight
                    )
                    val t = if (count > 1) (index * duration) / (count - 1) else 0L
                    Triple(x, y, t)
                }
            }

            val startX = pathPoints.first().first
            val startY = pathPoints.first().second
            val endX = pathPoints.last().first
            val endY = pathPoints.last().second

            Log.d("SwipeHandler", "Swipe with path: ${pathPoints.size} points")

            // 显示滑动特效（仍使用起点和终点）
            FloatingAssistantService.showSwipeEffect(startX, startY, endX, endY)
            FloatingAssistantService.setActionMode(true)

            // 使用带时间的轨迹滑动
            service.swipeWithTimedPath(pathPoints)

            FloatingAssistantService.setActionMode(false)
            return ExecuteResult(
                success = true,
                message = "Swiped with ${pathPoints.size} points"
            )
        } else {
            // 旧格式兼容：起点和终点
            val start = params.get("start")?.asJsonArray
            val end = params.get("end")?.asJsonArray

            if (start == null || end == null || start.size() < 2 || end.size() < 2) {
                throw IllegalArgumentException(service.getString(R.string.swipe_missing_params))
            }

            val startRawX = start[0].asFloat
            val startRawY = start[1].asFloat
            val endRawX = end[0].asFloat
            val endRawY = end[1].asFloat

            // Validate coordinates are in 0-1 range
            if (startRawX !in 0f..1.5f || startRawY !in 0f..1.5f ||
                endRawX !in 0f..1.5f || endRawY !in 0f..1.5f) {
                Log.w("SwipeHandler", "Coordinates should be in the normalized 0-1 range")
                Log.w("SwipeHandler", "This may cause gesture to fail. Please use normalized coordinates (e.g., [0.5, 0.8] for swipe down).")
            }

            val (startX, startY) = ActionUtils.convertAnyToAbsolute(
                listOf(startRawX, startRawY),
                screenWidth,
                screenHeight
            )
            val (endX, endY) = ActionUtils.convertAnyToAbsolute(
                listOf(endRawX, endRawY),
                screenWidth,
                screenHeight
            )

            Log.d("SwipeHandler", "Swipe action requested")

            // 显示滑动特效
            FloatingAssistantService.showSwipeEffect(startX, startY, endX, endY)

            FloatingAssistantService.setActionMode(true)

            val message: String
            if (action == "tapandswipe" || action == "tap and swipe") {
                 service.tapAndSwipe(startX, startY, endX, endY)
                 message = service.getString(R.string.tap_and_swiped_msg, startX.toInt().toString(), startY.toInt().toString(), endX.toInt().toString(), endY.toInt().toString())
            } else {
                 service.swipe(startX, startY, endX, endY)
                 message = service.getString(R.string.swiped_msg, startX.toInt().toString(), startY.toInt().toString(), endX.toInt().toString(), endY.toInt().toString())
            }

            FloatingAssistantService.setActionMode(false)
            return ExecuteResult(success = true, message = message)
        }
    }
}
