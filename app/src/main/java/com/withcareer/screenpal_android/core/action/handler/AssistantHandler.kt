package com.withcareer.screenpal_android.core.action.handler

import android.util.Log
import com.google.gson.JsonObject
import com.withcareer.screenpal_android.R
import com.withcareer.screenpal_android.ScreenPalApplication
import com.withcareer.screenpal_android.core.action.ExecuteResult
import com.withcareer.screenpal_android.core.context.ChinesePromptResources
import com.withcareer.screenpal_android.core.llm.ModelClient
import com.withcareer.screenpal_android.data.preference.TaskStatus
import com.withcareer.screenpal_android.core.llm.ChatMessage
import com.withcareer.screenpal_android.core.llm.ContentItem
import com.withcareer.screenpal_android.overlay.FloatingAssistantService
import com.withcareer.screenpal_android.service.ScreenPalAccessibilityService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 助手功能处理器
 * 处理 WebSearch, RecordNote, Interact, TakeOver
 *
 */
class AssistantHandler(
    private val modelClient: ModelClient,
    private val apiKeyProvider: () -> String,
    private val modelName: String
) : ActionHandler {
    override fun getActionType(): String = "Assistant"

    override suspend fun execute(
        service: ScreenPalAccessibilityService,
        params: JsonObject,
        screenWidth: Int,
        screenHeight: Int
    ): ExecuteResult {
        val action = params.get("action")?.asString?.lowercase()
            ?: params.get("action_type")?.asString?.lowercase()
            ?: ""

        return when (action) {
            "websearch", "web search" -> webSearch(params)
            "recordnote", "record note" -> recordNote(service, params)
            "interact" -> interact(service, params)
            "take_over", "takeover" -> takeOver(service, params)
            else -> throw IllegalArgumentException("Unsupported assistant action: $action")
        }
    }

    private suspend fun webSearch(params: JsonObject): ExecuteResult {
        val query = params.get("query")?.asString
        if (query.isNullOrBlank()) {
            throw IllegalArgumentException("Web Search query is empty")
        }

        try {
            val messages = listOf(
                ChatMessage(
                    role = "system",
                    content = listOf(ContentItem(type = "text", text = ChinesePromptResources.webSearchPrompt))
                ),
                ChatMessage(
                    role = "user",
                    content = listOf(ContentItem(type = "text", text = "查询词：$query"))
                )
            )

            val response = modelClient.request(
                messages = messages,
                modelName = modelName,
                apiKey = apiKeyProvider(),
                temperature = 0.5,
                enableSearch = true
            )

            val answer = if (response.thinking.isNotBlank()) {
                "${response.thinking}\n${response.action}"
            } else {
                response.action
            }

            return ExecuteResult(
                success = true,
                message = answer,
                actionType = "web_search",
                actionData = params
            )
        } catch (e: Exception) {
            Log.e("AssistantHandler", "Web Search failed", e)
            throw Exception("Web Search failed: ${e.message}")
        }
    }

    private fun recordNote(service: ScreenPalAccessibilityService, params: JsonObject): ExecuteResult {
        val note = params.get("note")?.asString
        if (note.isNullOrEmpty()) {
            throw IllegalArgumentException(service.getString(R.string.note_missing_content))
        }

        try {
            val taskRepository = (service.application as ScreenPalApplication).taskRepository
            val currentTasks = taskRepository.tasks.value

            val activeTask = currentTasks.find { it.state?.status == TaskStatus.IN_PROGRESS }
                ?: currentTasks.lastOrNull()

            if (activeTask != null) {
                taskRepository.updateTaskNote(activeTask.id, note)
                Log.d("AssistantHandler", "Note saved to active task")

                val updatedTask = taskRepository.tasks.value.find { it.id == activeTask.id }

                return ExecuteResult(
                    success = true,
                    message = service.getString(R.string.note_saved_success, note),
                    actionType = "RecordNote",
                    actionData = params,
                    updatedTaskState = updatedTask?.state
                )
            } else {
                Log.w("AssistantHandler", "No active task found to save note")
                return ExecuteResult(
                    success = false,
                    message = service.getString(R.string.note_no_active_task),
                    actionType = "RecordNote"
                )
            }

        } catch (e: Exception) {
            Log.e("AssistantHandler", "Failed to save note", e)
            throw Exception(service.getString(R.string.note_save_failed, e.message))
        }
    }

    private suspend fun interact(service: ScreenPalAccessibilityService, params: JsonObject): ExecuteResult {
        val message = params.get("message")?.asString ?: service.getString(R.string.manual_selection_prompt)
        val choicesJson = params.get("choices")?.asJsonArray
        val choices = choicesJson?.map { it.asString } ?: emptyList()

        return suspendCancellableCoroutine { continuation ->
            FloatingAssistantService.showInteraction(message, choices) { selectedChoice ->
                if (continuation.isActive) {
                    val resultMessage = if (selectedChoice != null) {
                        service.getString(R.string.user_selected, selectedChoice)
                    } else {
                        service.getString(R.string.interaction_no_selection)
                    }
                    continuation.resume(ExecuteResult(success = true, message = resultMessage))
                }
            }
        }
    }

    private suspend fun takeOver(service: ScreenPalAccessibilityService, params: JsonObject): ExecuteResult {
        val message = params.get("message")?.asString ?: service.getString(R.string.user_assistance_required)

        return suspendCancellableCoroutine { continuation ->
            FloatingAssistantService.showInteraction(service.getString(R.string.takeover_prompt, message)) {
                if (continuation.isActive) {
                    continuation.resume(ExecuteResult(success = true, message = service.getString(R.string.takeover_completed)))
                }
            }
        }
    }
}
