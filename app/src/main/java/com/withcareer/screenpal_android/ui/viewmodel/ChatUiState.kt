package com.withcareer.screenpal_android.core.model

import com.withcareer.screenpal_android.data.preference.TaskStep

data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val thinking: String? = null,
    val action: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val interactiveActions: List<InteractiveAction>? = null
)

data class InteractiveAction(
    val label: String,
    val actionId: String,
    val payload: String? = null
)

enum class MessageRole {
    USER, ASSISTANT
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentApp: String? = null,
    val taskCompletedMessage: String? = null, // 任务完成消息，用于显示 toast
    val lastCompletedTaskId: String? = null, // 最近完成的任务ID，用于“添加到快捷指令”按钮
    val executionSteps: List<String> = emptyList(),
    val rawExecutionSteps: List<String> = emptyList(), // 存储原始执行指令，用于回放
    val taskSteps: List<TaskStep> = emptyList(), // 存储完整的任务步骤（包含思考和动作）
    val thinkingContent: String = "" // 实时思考内容
)
