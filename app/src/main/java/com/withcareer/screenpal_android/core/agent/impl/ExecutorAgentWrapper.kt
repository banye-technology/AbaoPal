package com.withcareer.screenpal_android.core.agent.impl

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.withcareer.screenpal_android.core.agent.framework.ActionExecutedMessage
import com.withcareer.screenpal_android.core.agent.framework.AgentBus
import com.withcareer.screenpal_android.core.agent.framework.AgentMessage
import com.withcareer.screenpal_android.core.agent.framework.BaseAgent
import com.withcareer.screenpal_android.core.agent.framework.CurrentAppUpdateMessage
import com.withcareer.screenpal_android.core.agent.framework.ExecutionGeneratedMessage
import com.withcareer.screenpal_android.core.agent.framework.PlanGeneratedMessage
import com.withcareer.screenpal_android.core.agent.framework.ExecutionRequestMessage
import com.withcareer.screenpal_android.core.agent.framework.StateUpdatedMessage
import com.withcareer.screenpal_android.core.agent.framework.TaskFailedMessage
import com.withcareer.screenpal_android.core.llm.ModelClient
import com.withcareer.screenpal_android.core.skill.SkillRegistry
import com.withcareer.screenpal_android.core.llm.ChatMessage
import com.withcareer.screenpal_android.core.llm.ContentItem
import com.withcareer.screenpal_android.core.llm.ImageUrl
import com.withcareer.screenpal_android.util.ActionJsonUtils
import com.withcareer.screenpal_android.util.BitmapUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

/**
 * 执行智能体 (Event-Driven)
 */
class ExecutorAgentWrapper(
    private val context: android.content.Context,
    skillRegistry: SkillRegistry,
    modelClient: ModelClient,
    private val apiKey: String,
    private val modelName: String,
    bus: AgentBus,
    scope: CoroutineScope,
    private val maxContextRounds: Int = 10,
    private val isDeveloperMode: () -> Boolean = { false },
    private val getCurrentApp: () -> String? = { null }
) : BaseAgent(bus, scope) {

    private val innerAgent = ExecutorAgent(skillRegistry, modelClient)
    private val history = mutableListOf<ChatMessage>()
    private var forceScreenshotNext: Boolean = false
    private var visualModeStickyCount: Int = 0 // Count of remaining forced visual mode steps
    private var lastObservationHash: Long? = null
    private var lastActionType: String? = null
    private var lastActionSuccess: Boolean? = null
    // private var currentApp: String? = null // Removed: Use provider instead

    override suspend fun handleMessage(message: AgentMessage) {
        when (message) {
            is CurrentAppUpdateMessage -> {
                // currentApp = message.packageName // No longer needed
            }
            is ActionExecutedMessage -> {
                lastActionType = message.actionType
                lastActionSuccess = message.success
                if (!message.success) {
                    forceScreenshotNext = true
                    visualModeStickyCount = 3 // Enable sticky visual mode on failure
                }
                val statusStr = if (message.success) "Success" else "Failed"
                val feedback = "System Notification: Action '${message.actionType}' execution $statusStr. Message: ${message.message}"

                Log.d("ExecutorAgent", "Received action feedback")

                history.add(ChatMessage(
                    role = "user",
                    content = listOf(ContentItem(type = "text", text = feedback))
                ))

                if (history.size > maxContextRounds * 2) {
                    history.removeAt(0)
                }
            }
            is PlanGeneratedMessage -> {
                val plannerHistory = message.plannerHistory ?: return
                if (plannerHistory.isEmpty()) return

                history.add(
                    ChatMessage(
                        role = "system",
                        content = listOf(
                            ContentItem(
                                type = "text",
                                text = "以下是规划阶段的对话上下文（供后续执行参考）："
                            )
                        )
                    )
                )
                history.addAll(plannerHistory)
                while (history.size > maxContextRounds * 2) {
                    history.removeAt(0)
                }
            }
            is ExecutionRequestMessage -> {
                Log.d("ExecutorAgent", "Executing...")
                val screenshot = message.screenshot
                try {
                    val currentHash = BitmapUtils.dHash64(screenshot)
                    val prevHash = lastObservationHash
                    val prevActionSuccess = lastActionSuccess
                    val prevActionType = lastActionType
                    if (prevHash != null && prevActionSuccess == true) {
                        val distance = BitmapUtils.hammingDistance(prevHash, currentHash)
                        val shouldCheck = !prevActionType.isNullOrBlank() && !prevActionType.equals("runinstructionset", ignoreCase = true)
                        if (shouldCheck) {
                            val uiChanged = distance > 8
                            forceScreenshotNext = !uiChanged
                            if (forceScreenshotNext) {
                                visualModeStickyCount = 3 // Enable sticky visual mode if UI stuck
                            } else if (visualModeStickyCount > 0) {
                                visualModeStickyCount-- // Decay sticky count on successful UI change
                            }
                            Log.d("ExecutorAgent", "UI_CHANGE_CHECK changed=$uiChanged forceScreenshotNext=$forceScreenshotNext sticky=$visualModeStickyCount")
                        }
                    }
                    lastObservationHash = currentHash

                    val isDev = isDeveloperMode()
                    var screenshotPath: String? = null
                    var aiPrompt: String? = null

                    if (isDev) {
                        try {
                            val filename = "task_${message.taskState.taskId}_${System.currentTimeMillis()}.jpg"
                            val dir = java.io.File(context.filesDir, "screenshots")
                            if (!dir.exists()) dir.mkdirs()
                            val file = java.io.File(dir, filename)
                            withContext(Dispatchers.IO) {
                                FileOutputStream(file).use { out ->
                                    screenshot.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, out)
                                }
                            }
                            screenshotPath = file.absolutePath

                            val sanitizedHistory = history.map { message ->
                                message.copy(content = message.content.map { item ->
                                    if (item.type == "image_url") {
                                        item.copy(imageUrl = ImageUrl(url = "[IMAGE_STORED_LOCALLY]"))
                                    } else {
                                        item
                                    }
                                })
                            }
                            // Placeholder for prompt; will be updated after execution if available
                            aiPrompt = Gson().toJson(sanitizedHistory)
                        } catch (e: Exception) {
                            Log.e("ExecutorAgent", "Failed to save debug info", e)
                        }
                    }

                    val response = innerAgent.execute(
                        screenshot = screenshot,
                        taskState = message.taskState,
                        currentApp = getCurrentApp(), // Use real-time provider
                        history = history,
                        modelName = modelName,
                        apiKey = apiKey,
                        maxContextRounds = maxContextRounds,
                        forceScreenshot = forceScreenshotNext || visualModeStickyCount > 0
                    )

                    if (isDev && response.fullPrompt != null) {
                        try {
                             val sanitizedPrompt = response.fullPrompt.map { message ->
                                message.copy(content = message.content.map { item ->
                                    if (item.type == "image_url") {
                                        item.copy(imageUrl = ImageUrl(url = "[IMAGE_STORED_LOCALLY]"))
                                    } else {
                                        item
                                    }
                                })
                            }
                            aiPrompt = Gson().toJson(sanitizedPrompt)
                        } catch (e: Exception) {
                            Log.e("ExecutorAgent", "Failed to update debug info with full prompt", e)
                        }
                    }

                    updateHistory(response.rawResponse)

                    response.contextUpdateJson?.let { updateJsonStr ->
                        try {
                            val updateJson = JsonParser.parseString(updateJsonStr).asJsonObject
                            ActionJsonUtils.processTaskStateUpdate(updateJson, message.taskState)
                            bus.emit(StateUpdatedMessage(message.taskState, null))
                            Log.d("ExecutorAgent", "Context updated via executor response.")
                        } catch (e: Exception) {
                            Log.e("ExecutorAgent", "Failed to process context update", e)
                        }
                    }

                    bus.emit(ExecutionGeneratedMessage(
                        description = response.description,
                        actionJson = response.rawResponse,
                        screenshotPath = screenshotPath,
                        aiPrompt = aiPrompt,
                        aiResponse = if (isDev) response.rawResponse else null
                    ))

                } catch (e: Exception) {
                    Log.e("ExecutorAgent", "Error executing", e)
                    bus.emit(TaskFailedMessage("Execution error: ${e.message}"))
                } finally {
                    if (!screenshot.isRecycled) {
                        screenshot.recycle()
                    }
                }
            }
            else -> {}
        }
    }

    private fun updateHistory(actionResponse: String) {
        try {
            history.add(ChatMessage(
                role = "user",
                content = listOf(
                    ContentItem(type = "text", text = "请根据当前屏幕截图执行下一步操作。注意：请仔细检查截图与上一步是否一致，避免死循环。如果连续多次操作无效，请尝试 Back 或 Scroll。")
                )
            ))

            history.add(ChatMessage(
                role = "assistant",
                content = listOf(ContentItem(type = "text", text = actionResponse))
            ))

            while (history.size > maxContextRounds * 2) {
                history.removeAt(0)
            }
        } catch (e: Exception) {
            Log.e("ExecutorAgent", "Error updating history", e)
        }
    }
}
