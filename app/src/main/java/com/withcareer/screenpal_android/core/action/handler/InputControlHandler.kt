package com.withcareer.screenpal_android.core.action.handler

import com.google.gson.JsonObject
import com.withcareer.screenpal_android.R
import com.withcareer.screenpal_android.core.action.ExecuteResult
import com.withcareer.screenpal_android.service.ScreenPalAccessibilityService

/**
 * 输入控制处理器
 * 处理 Enter, Copy, Cut, Paste
 *
 */
class InputControlHandler : ActionHandler {
    override fun getActionType(): String = "InputControl"

    override suspend fun execute(
        service: ScreenPalAccessibilityService,
        params: JsonObject,
        screenWidth: Int,
        screenHeight: Int
    ): ExecuteResult {
        val action = params.get("action")?.asString?.lowercase()
            ?: params.get("action_type")?.asString?.lowercase()
            ?: ""

        // 这些操作都需要焦点输入框
        val node = service.findFocusedInputNode()
            ?: throw Exception(service.getString(R.string.no_focused_input))

        try {
            val message = when (action) {
                "enter", "ime enter" -> {
                    val success = service.performImeEnter(node)
                    if (success) "Pressed Enter" else throw Exception("Failed to press Enter")
                }
                "copy" -> {
                    val success = service.performCopy(node)
                    if (success) "Copied text" else throw Exception("Copy failed")
                }
                "cut" -> {
                    val success = service.performCut(node)
                    if (success) "Cut text" else throw Exception("Cut failed")
                }
                "paste" -> {
                    val success = service.performPaste(node)
                    if (success) "Pasted text" else throw Exception("Paste failed")
                }
                else -> throw IllegalArgumentException("Unsupported input control action: $action")
            }

            return ExecuteResult(success = true, message = message)
        } finally {
            @Suppress("DEPRECATION")
            node.recycle()
        }
    }
}
