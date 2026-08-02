package com.withcareer.screenpal_android.core.action.handler

import android.util.Log
import com.google.gson.JsonObject
import com.withcareer.screenpal_android.R
import com.withcareer.screenpal_android.core.action.ExecuteResult
import com.withcareer.screenpal_android.service.ScreenPalAccessibilityService
import com.withcareer.screenpal_android.util.DeviceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用启动处理器
 * 处理 Launch 操作
 *
 */
class LaunchHandler : ActionHandler {
    override fun getActionType(): String = "Launch"

    override suspend fun execute(
        service: ScreenPalAccessibilityService,
        params: JsonObject,
        screenWidth: Int,
        screenHeight: Int
    ): ExecuteResult {
        val appName = params.get("app")?.asString
        if (appName.isNullOrEmpty()) {
             throw IllegalArgumentException(service.getString(R.string.launch_missing_app))
        }

        return try {
            val launchIntent = withContext(Dispatchers.IO) {
                DeviceUtils.getLaunchIntentByLabel(service, appName)
            }

            if (launchIntent != null) {
                service.startActivity(launchIntent)
                ExecuteResult(success = true, message = service.getString(R.string.launched_app, appName))
            } else {
                Log.w("LaunchHandler", "Requested app was not found")
                throw Exception(service.getString(R.string.app_not_found_manual, appName))
            }
        } catch (e: Exception) {
            throw Exception(service.getString(R.string.launch_failed, e.message))
        }
    }
}
