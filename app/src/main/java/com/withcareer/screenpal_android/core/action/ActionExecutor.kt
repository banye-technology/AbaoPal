package com.withcareer.screenpal_android.core.action

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.withcareer.screenpal_android.R
import com.withcareer.screenpal_android.core.action.handler.*
import com.withcareer.screenpal_android.core.llm.ModelClient
import com.withcareer.screenpal_android.core.safety.SafetyGuard
import com.withcareer.screenpal_android.service.ScreenPalAccessibilityService
import com.withcareer.screenpal_android.util.ActionJsonUtils
import kotlin.coroutines.cancellation.CancellationException

class ActionParsingException(message: String) : Exception(message)

/**
 * 动作执行器
 * 使用策略模式分发动作
 *
 */
class ActionExecutor(
    private val service: ScreenPalAccessibilityService,
    private val modelClient: ModelClient,
    private val apiKeyProvider: () -> String,
    private val modelName: String
) {

    private val handlers: Map<String, ActionHandler> by lazy {
        val map = mutableMapOf<String, ActionHandler>()

        val tapHandler = TapHandler()
        map["tap"] = tapHandler
        map["click"] = tapHandler

        val multiTapHandler = MultiTapHandler()
        map["multitap"] = multiTapHandler
        map["multi_tap"] = multiTapHandler

        val scrollHandler = ScrollHandler()
        map["scroll"] = scrollHandler

        val swipeHandler = SwipeHandler()
        map["swipe"] = swipeHandler
        map["longpressswipe"] = swipeHandler
        map["long_press_swipe"] = swipeHandler

        val typeHandler = TypeHandler()
        map["type"] = typeHandler
        map["input"] = typeHandler

        val launchHandler = LaunchHandler()
        map["launch"] = launchHandler
        map["open_app"] = launchHandler

        val waitHandler = WaitHandler()
        map["wait"] = waitHandler
        map["sleep"] = waitHandler

        val systemHandler = SystemHandler()
        val systemActions = listOf(
            "system", "back", "home", "recents", "notifications",
            "quicksettings", "quick_settings", "lockscreen", "lock_screen",
            "power", "splitscreen", "split_screen", "screenshot", "screen_shot"
        )
        systemActions.forEach { map[it] = systemHandler }

        val gestureHandler = GestureHandler()
        val gestureActions = listOf(
            "gesture", "longpress", "long_press", "doubletap", "double_tap",
            "zoomin", "zoom_in", "zoomout", "zoom_out",
            "pinchout", "pinch_out", "pinchin", "pinch_in"
        )
        gestureActions.forEach { map[it] = gestureHandler }

        val inputControlHandler = InputControlHandler()
        val inputControlActions = listOf(
            "inputcontrol", "enter", "copy", "cut", "paste", "ime_enter"
        )
        inputControlActions.forEach { map[it] = inputControlHandler }

        val assistantHandler = AssistantHandler(modelClient, apiKeyProvider, modelName)
        val assistantActions = listOf(
            "assistant", "websearch", "web_search", "recordnote", "record_note",
            "interact", "takeover", "take_over"
        )
        assistantActions.forEach { map[it] = assistantHandler }

        val runInstructionSetHandler = RunInstructionSetHandler()
        val runInstructionActions = listOf(
            "runinstructionset", "run_instruction_set", "run_instruction",
            "instruction_set", "runinstruction"
        )
        runInstructionActions.forEach { map[it] = runInstructionSetHandler }

        val systemIntentHandler = SystemIntentHandler()
        val systemIntentActions = listOf("systemintent", "system_intent")
        systemIntentActions.forEach { map[it] = systemIntentHandler }

        val finishHandler = FinishHandler()
        val finishActions = listOf("finish", "done", "complete", "completed")
        finishActions.forEach { map[it] = finishHandler }

        map
    }

    suspend fun execute(actionJson: String, screenWidth: Int, screenHeight: Int): ExecuteResult {
        return try {
            val startMs = android.os.SystemClock.elapsedRealtime()
            Log.d("ActionExecutor", "Start parsing action")

            val extractStart = android.os.SystemClock.elapsedRealtime()
            val jsonString = ActionJsonUtils.extractJsonFromText(actionJson)
            Log.d("ActionExecutor", "Action JSON extracted")
            Log.d("ActionExecutor", "Timing extract JSON: ${android.os.SystemClock.elapsedRealtime() - extractStart}ms")

            if (jsonString.isEmpty()) {
                throw ActionParsingException(service.getString(R.string.failed_to_extract_json))
            }

            val parseStart = android.os.SystemClock.elapsedRealtime()
            val jsonElement = try {
                JsonParser.parseString(jsonString)
            } catch (e: Exception) {
                Log.w("ActionExecutor", "Standard parsing failed", e)
                throw ActionParsingException(service.getString(R.string.json_parse_failed, e.message))
            }
            Log.d("ActionExecutor", "Timing parse JSON: ${android.os.SystemClock.elapsedRealtime() - parseStart}ms")

            if (!jsonElement.isJsonObject) {
                throw ActionParsingException(service.getString(R.string.invalid_json_object))
            }

            val actionObj = jsonElement.asJsonObject

            val contextUpdate = if (actionObj.has("context_update")) {
                try {
                    actionObj.getAsJsonObject("context_update")
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }

            val processStart = android.os.SystemClock.elapsedRealtime()
            val result = processActionObject(actionObj, screenWidth, screenHeight)
            Log.d("ActionExecutor", "Timing process action: ${android.os.SystemClock.elapsedRealtime() - processStart}ms")

            if (contextUpdate != null) {
                val newActionData = result.actionData ?: JsonObject()
                contextUpdate.entrySet().forEach { (key, value) ->
                    newActionData.add(key, value)
                }
                return result.copy(actionData = newActionData)
            }

            Log.d("ActionExecutor", "Timing total execute: ${android.os.SystemClock.elapsedRealtime() - startMs}ms")
            return result
        } catch (e: ActionParsingException) {
            throw e
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("ActionExecutor", "Action execution failed", e)
            ExecuteResult(success = false, message = service.getString(R.string.execution_exception, e.message))
        }
    }

    private suspend fun processActionObject(inputObj: JsonObject, screenWidth: Int, screenHeight: Int): ExecuteResult {
        var actionObj = inputObj

        if (!actionObj.has("action_type") && actionObj.has("action") && actionObj.get("action").isJsonObject) {
            actionObj = actionObj.getAsJsonObject("action")
        }

        var actionType = actionObj.get("action_type")?.asString?.lowercase() ?: ""

        if (actionType == "do" && actionObj.has("action")) {
            val subAction = actionObj.get("action")
            if (subAction.isJsonPrimitive) {
                actionType = subAction.asString.lowercase()
            }
        }

        // 检查是否需要切换应用
        val targetPackage = actionObj.get("target_package")?.asString
        if (!targetPackage.isNullOrBlank()) {
            val currentApp = service.getActivePackageName()
            if (currentApp != targetPackage) {
                Log.d("ActionExecutor", "Switching to target app")
                // 尝试启动目标应用
                try {
                    val launchIntent = service.packageManager.getLaunchIntentForPackage(targetPackage)
                    if (launchIntent != null) {
                        launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        service.startActivity(launchIntent)

                        // 轮询等待应用启动 (最多 5 秒)
                        var retries = 0
                        while (retries < 10) { // 20 * 250ms = 5000ms
                            kotlinx.coroutines.delay(250)
                            if (service.getActivePackageName() == targetPackage) {
                                Log.d("ActionExecutor", "Target app switch succeeded")
                                break
                            }
                            retries++
                        }
                        if (service.getActivePackageName() != targetPackage) {
                             Log.w("ActionExecutor", "Target app switch timed out")
                        }
                    } else {
                        Log.w("ActionExecutor", "Cannot find launch intent for target app")
                    }
                } catch (e: Exception) {
                    Log.e("ActionExecutor", "Failed to switch target app", e)
                }
            }
        }

        val currentApp = service.getActivePackageName()
        val safetyResult = SafetyGuard.checkAction(currentApp, actionType)
        if (safetyResult is SafetyGuard.SafetyResult.Blocked) {
            // Directly finish the task if safety guard is triggered
            return ExecuteResult(
                success = true,
                message = safetyResult.reason,
                actionType = "finish"
            )
        }

        val handler = handlers[actionType]

        return if (handler != null) {
            Log.d("ActionExecutor", "Dispatching action to handler")

            try {
                val handlerStart = android.os.SystemClock.elapsedRealtime()
                val result = handler.execute(service, actionObj, screenWidth, screenHeight)
                Log.d("ActionExecutor", "Timing handler ${handler.javaClass.simpleName}: ${android.os.SystemClock.elapsedRealtime() - handlerStart}ms")
                if (result.actionType == null) {
                    result.copy(actionType = actionType)
                } else {
                    result
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("ActionExecutor", "Handler execution failed", e)
                throw e
            }
        } else {
            Log.w("ActionExecutor", "Unknown action type")
            ExecuteResult(
                success = false,
                message = service.getString(R.string.unsupported_action, actionType)
            )
        }
    }
}
