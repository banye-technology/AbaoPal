package com.withcareer.screenpal_android.core.action.handler

import android.util.Log
import com.google.gson.JsonObject
import com.withcareer.screenpal_android.R
import com.withcareer.screenpal_android.util.ActionUtils
import com.withcareer.screenpal_android.core.action.ExecuteResult
import com.withcareer.screenpal_android.overlay.FloatingAssistantService
import com.withcareer.screenpal_android.service.ScreenPalAccessibilityService

/**
 * 点击动作处理器
 * 支持坐标点击和文本定位点击
 *
 */
class TapHandler : ActionHandler {
    override fun getActionType(): String = "Tap"

    override suspend fun execute(
        service: ScreenPalAccessibilityService,
        params: JsonObject,
        screenWidth: Int,
        screenHeight: Int
    ): ExecuteResult {
        val element = params.get("element")

        // 1. 优先处理坐标点击 (element 是数组)
        if (element?.isJsonArray == true) {
            val array = element.asJsonArray
            if (array.size() >= 2) {
                val relativeX = array[0].asFloat
                val relativeY = array[1].asFloat

                val (absoluteX, absoluteY) = ActionUtils.convertAnyToAbsolute(
                    listOf(relativeX, relativeY),
                    screenWidth,
                    screenHeight
                )

                FloatingAssistantService.setActionMode(true)
                service.tap(absoluteX, absoluteY)
                FloatingAssistantService.showClickEffect(absoluteX, absoluteY)
                FloatingAssistantService.setActionMode(false)

                return ExecuteResult(
                    success = true,
                    message = service.getString(R.string.tapped_at_msg, absoluteX.toInt().toString(), absoluteY.toInt().toString())
                )
            }
        } else if (element?.isJsonPrimitive == true) {
            // 2. 处理文本/ID 点击 (element 是字符串)
            val text = element.asString
            Log.d("TapHandler", "Tap target resolved from text")

            val node = service.findNodeByText(text)
            if (node != null) {
                try {
                    val success = service.performClick(node)
                    if (success) {
                         return ExecuteResult(success = true, message = "Tapped element with text: $text")
                    } else {
                         throw Exception("Found element '$text' but failed to click")
                    }
                } finally {
                    @Suppress("DEPRECATION")
                    node.recycle()
                }
            } else {
                 throw Exception(service.getString(R.string.element_not_found, text))
            }
        }

        throw IllegalArgumentException("Invalid tap element parameters")
    }
}
