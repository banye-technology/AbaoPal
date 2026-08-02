package com.withcareer.screenpal_android.core.action.handler

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.withcareer.screenpal_android.core.action.ExecuteResult
import com.withcareer.screenpal_android.overlay.FloatingAssistantService
import com.withcareer.screenpal_android.service.ScreenPalAccessibilityService
import com.withcareer.screenpal_android.util.ActionUtils

/**
 * 多点同时点击处理器
 */
class MultiTapHandler : ActionHandler {
    override fun getActionType(): String = "MultiTap"

    override suspend fun execute(
        service: ScreenPalAccessibilityService,
        params: JsonObject,
        screenWidth: Int,
        screenHeight: Int
    ): ExecuteResult {
        val pointsArray = params.getAsJsonArray("points")
            ?: throw IllegalArgumentException("Missing points for multiTap")

        val points = parsePoints(pointsArray, screenWidth, screenHeight)
        if (points.isEmpty()) {
            throw IllegalArgumentException("multiTap requires at least 1 point")
        }

        Log.d("MultiTapHandler", "MultiTap points=${points.size}")

        FloatingAssistantService.setActionMode(true)
        points.forEach { (x, y) ->
            FloatingAssistantService.showClickEffect(x.toFloat(), y.toFloat())
        }
        service.multiTap(points.map { it.first.toFloat() to it.second.toFloat() })
        FloatingAssistantService.setActionMode(false)

        return ExecuteResult(
            success = true,
            message = "MultiTap: ${points.size} points"
        )
    }

    private fun parsePoints(
        array: JsonArray,
        screenWidth: Int,
        screenHeight: Int
    ): List<Pair<Int, Int>> {
        val results = mutableListOf<Pair<Int, Int>>()
        array.forEach { element ->
            val point = when {
                element.isJsonArray -> {
                    val arr = element.asJsonArray
                    if (arr.size() >= 2) {
                        val x = arr[0].asFloat
                        val y = arr[1].asFloat
                        resolvePoint(x, y, screenWidth, screenHeight)
                    } else null
                }
                element.isJsonObject -> {
                    val obj = element.asJsonObject
                    val x = obj.get("x")?.asFloat
                    val y = obj.get("y")?.asFloat
                    if (x != null && y != null) resolvePoint(x, y, screenWidth, screenHeight) else null
                }
                else -> null
            }
            if (point != null) {
                results.add(point)
            }
        }
        return results
    }

    private fun resolvePoint(
        x: Float,
        y: Float,
        screenWidth: Int,
        screenHeight: Int
    ): Pair<Int, Int> {
        val isRelative = x in 0f..1.5f && y in 0f..1.5f
        return if (isRelative) {
            val (absX, absY) = ActionUtils.convertRelativeToAbsolute(listOf(x, y), screenWidth, screenHeight)
            absX.toInt() to absY.toInt()
        } else {
            x.toInt() to y.toInt()
        }
    }
}
