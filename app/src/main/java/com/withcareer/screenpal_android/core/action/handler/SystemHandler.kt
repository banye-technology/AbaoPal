package com.withcareer.screenpal_android.core.action.handler

import com.google.gson.JsonObject
import com.withcareer.screenpal_android.R
import com.withcareer.screenpal_android.core.action.ExecuteResult
import com.withcareer.screenpal_android.service.ScreenPalAccessibilityService

/**
 * 系统级动作处理器
 * 处理 Back, Home, Recents, Notifications 等系统操作
 *
 */
class SystemHandler : ActionHandler {
    override fun getActionType(): String = "System"

    override suspend fun execute(
        service: ScreenPalAccessibilityService,
        params: JsonObject,
        screenWidth: Int,
        screenHeight: Int
    ): ExecuteResult {
        val action = params.get("action")?.asString?.lowercase()
            ?: params.get("action_type")?.asString?.lowercase()
            ?: ""

        val message = when (action) {
            "back" -> {
                service.performBack()
                service.getString(R.string.action_back)
            }
            "home" -> {
                service.performHome()
                service.getString(R.string.action_home)
            }
            "recents" -> {
                service.performRecents()
                service.getString(R.string.opened_recents)
            }
            "notifications" -> {
                service.performNotifications()
                service.getString(R.string.opened_notifications)
            }
            "quicksettings", "quick settings" -> {
                service.performQuickSettings()
                service.getString(R.string.opened_quick_settings)
            }
            "lockscreen", "lock screen" -> {
                service.performLockScreen()
                service.getString(R.string.locked_screen)
            }
            "power", "power dialog" -> {
                service.performPowerDialog()
                service.getString(R.string.opened_power_dialog)
            }
            "splitscreen", "split screen" -> {
                service.performToggleSplitScreen()
                service.getString(R.string.toggled_split_screen)
            }
            "screenshot", "screen shot" -> {
                service.performScreenshot()
                "Screenshot captured"
            }
            else -> throw IllegalArgumentException("Unsupported system action: $action")
        }

        return ExecuteResult(success = true, message = message)
    }
}
