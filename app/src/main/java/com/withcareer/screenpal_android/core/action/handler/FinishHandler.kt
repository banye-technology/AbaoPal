package com.withcareer.screenpal_android.core.action.handler

import com.google.gson.JsonObject
import com.withcareer.screenpal_android.core.action.ExecuteResult
import com.withcareer.screenpal_android.service.ScreenPalAccessibilityService

/**
 * 完成任务处理器
 * 处理 Finish 操作
 *
 */
class FinishHandler : ActionHandler {
    override fun getActionType(): String = "Finish"

    override suspend fun execute(
        service: ScreenPalAccessibilityService,
        params: JsonObject,
        screenWidth: Int,
        screenHeight: Int
    ): ExecuteResult {
        val message = params.get("message")?.asString ?: "Task Completed"
        return ExecuteResult(
            success = true,
            message = message,
            actionType = "Finish"
        )
    }
}
