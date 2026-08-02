package com.withcareer.screenpal_android.core.action.handler

import android.util.Log
import com.google.gson.JsonObject
import com.withcareer.screenpal_android.R
import com.withcareer.screenpal_android.core.action.ExecuteResult
import com.withcareer.screenpal_android.service.ScreenPalAccessibilityService

/**
 * 文本输入处理器
 * 处理 Type 操作
 *
 */
class TypeHandler : ActionHandler {
    override fun getActionType(): String = "Type"

    override suspend fun execute(
        service: ScreenPalAccessibilityService,
        params: JsonObject,
        screenWidth: Int,
        screenHeight: Int
    ): ExecuteResult {
        val text = params.get("text")?.asString
        if (text.isNullOrEmpty()) {
            throw IllegalArgumentException(service.getString(R.string.type_missing_text))
        }

        Log.d("TypeHandler", "Executing type action")

        // Retry mechanism for finding focused input node
        var inputNode: android.view.accessibility.AccessibilityNodeInfo? = null
        for (i in 0 until 2) {
            inputNode = service.findFocusedInputNode()
            if (inputNode != null) break
            Log.d("TypeHandler", "Focused input node not found, retrying... ($i/2)")
        }

        if (inputNode == null) {
            Log.w("TypeHandler", "No focused input node found after retries")
            return ExecuteResult(
                success = false,
                message = "${service.getString(R.string.no_focused_input)}。请基于截图定位输入框并先点击聚焦，再执行输入。",
                actionType = "type"
            )
        }

        try {
            val success = service.setText(inputNode, text)

            return if (success) {
                ExecuteResult(success = true, message = service.getString(R.string.type_success), actionType = "type")
            } else {
                ExecuteResult(success = false, message = service.getString(R.string.type_failed), actionType = "type")
            }
        } finally {
            @Suppress("DEPRECATION")
            inputNode.recycle()
        }
    }
}
