package com.withcareer.screenpal_android.core.action.handler

import com.google.gson.JsonObject
import com.withcareer.screenpal_android.R
import com.withcareer.screenpal_android.util.ActionUtils
import com.withcareer.screenpal_android.core.action.ExecuteResult
import com.withcareer.screenpal_android.service.ScreenPalAccessibilityService
import kotlinx.coroutines.delay

/**
 * 等待处理器
 * 处理 Wait 操作
 *
 */
class WaitHandler : ActionHandler {
    override fun getActionType(): String = "Wait"

    override suspend fun execute(
        service: ScreenPalAccessibilityService,
        params: JsonObject,
        screenWidth: Int,
        screenHeight: Int
    ): ExecuteResult {
        val durationMs = ActionUtils.parseDurationMillis(params.get("duration"))
        delay(durationMs)
        return ExecuteResult(
            success = true,
            message = service.getString(R.string.waited_msg, durationMs)
        )
    }
}
