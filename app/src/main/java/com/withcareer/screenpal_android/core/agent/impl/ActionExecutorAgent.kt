package com.withcareer.screenpal_android.core.agent.impl

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.withcareer.screenpal_android.core.action.ActionExecutor
import com.withcareer.screenpal_android.core.agent.framework.*
import com.withcareer.screenpal_android.util.ActionUtils
import com.withcareer.screenpal_android.util.DeviceUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.withcareer.screenpal_android.service.ScreenPalAccessibilityService
import com.withcareer.screenpal_android.core.ui.UiTreeProcessor

/**
 * 动作执行代理 (Action Runner)
 * 负责实际执行 UI 操作
 */
class ActionExecutorAgent(
    private val actionExecutor: ActionExecutor,
    private val context: Context,
    bus: AgentBus,
    scope: CoroutineScope
) : BaseAgent(bus, scope) {

    override suspend fun handleMessage(message: AgentMessage) {
        when (message) {
            is ActionRequestMessage -> {
                Log.d("ActionExecutorAgent", "Executing action request")
                try {
                    val groundedJson = try {
                        groundActionWithUiTree(message.actionJson)
                    } catch (e: Exception) {
                        Log.w("ActionExecutorAgent", "GROUNDING failed, use original action", e)
                        message.actionJson
                    }
                    val (width, height) = DeviceUtils.getRealScreenSize(context)
                    Log.d("ActionExecutorAgent", "Screen dimensions resolved")
                    val result = withContext(Dispatchers.Default) {
                        actionExecutor.execute(
                            groundedJson,
                            width,
                            height
                        )
                    }
                    if (result.updatedTaskState != null) {
                        Log.d("ActionExecutorAgent", "Emitting StateUpdatedMessage from action result")
                        bus.emit(StateUpdatedMessage(result.updatedTaskState))
                    }
                    bus.emit(ActionExecutedMessage(
                        success = result.success,
                        message = result.message,
                        actionType = result.actionType ?: "unknown",
                        resultData = result.actionData?.toString()
                    ))
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        Log.i("ActionExecutorAgent", "Action execution cancelled")
                        return
                    }
                    Log.e("ActionExecutorAgent", "Error executing action", e)
                    bus.emit(ActionExecutedMessage(
                        success = false,
                        message = "Exception: ${e.message}",
                        actionType = "error"
                    ))
                }
            }
            else -> {}
        }
    }
    private fun groundActionWithUiTree(actionJson: String): String {
        val root = JSONObject(actionJson)
        val actionObj = root.optJSONObject("action") ?: return actionJson
        val actionType = actionObj.optString("action", "")
        if (actionType.lowercase() != "tap") return actionJson
        val elementArr = actionObj.optJSONArray("element") ?: return actionJson
        if (elementArr.length() != 2) return actionJson
        val xRaw = elementArr.optDouble(0).toFloat()
        val yRaw = elementArr.optDouble(1).toFloat()
        val service = ScreenPalAccessibilityService.getInstance() ?: return actionJson
        val (screenW, screenH) = DeviceUtils.getRealScreenSize(context)
        val (xPx, yPx) = ActionUtils.convertAnyToAbsolute(listOf(xRaw, yRaw), screenW, screenH)
        val snapshot = service.getUiTreeSnapshot(
            maxNodes = 240,
            maxDepth = 8,
            targetPackage = service.getActivePackageName()
        ) ?: return actionJson

        val pxX = xPx.toInt()
        val pxY = yPx.toInt()

        fun area(el: com.withcareer.screenpal_android.core.ui.UiElement): Long {
            val w = el.bounds.width().toLong().coerceAtLeast(1)
            val h = el.bounds.height().toLong().coerceAtLeast(1)
            return w * h
        }

        val inside = snapshot.elements.filter { it.clickable && it.bounds.contains(pxX, pxY) }
        if (inside.isEmpty()) {
            return actionJson
        }

        val chosen = inside.minWithOrNull(
            compareBy<com.withcareer.screenpal_android.core.ui.UiElement> { area(it) }
                .thenByDescending { !it.resourceId.isNullOrBlank() }
                .thenByDescending { it.visible }
        ) ?: return actionJson

        val centerX = chosen.centerX.toFloat()
        val centerY = chosen.centerY.toFloat()
        val (relX, relY) = ActionUtils.convertAbsoluteToRelative(centerX, centerY, screenW, screenH)
        val outX = relX.toInt().coerceIn(0, 1000)
        val outY = relY.toInt().coerceIn(0, 1000)
        val newAction = JSONObject(actionObj.toString())
        newAction.put("element", org.json.JSONArray(listOf(outX, outY)))
        val newRoot = JSONObject(root.toString())
        newRoot.put("action", newAction)
        Log.i("ActionExecutorAgent", "GROUNDING candidate selected")
        Log.i("ActionExecutorAgent", "GROUNDING coordinates normalized")
        return newRoot.toString()
    }
}
