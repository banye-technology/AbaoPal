package com.withcareer.screenpal_android.core.agent.framework

import android.graphics.Bitmap
import com.withcareer.screenpal_android.data.preference.TaskState
import com.withcareer.screenpal_android.core.llm.ChatMessage as LlmChatMessage

/**
 * 基础 Agent 消息
 * 所有 Agent 之间的通信都通过 Message 进行
 */
sealed interface AgentMessage

/**
 * 通用事件
 */
data class UserRequestMessage(val content: String) : AgentMessage
data class SystemErrorMessage(val error: String) : AgentMessage

/**
 * 任务生命周期事件
 */
data class TaskStartedMessage(val goal: String, val taskId: String? = null) : AgentMessage
data class StartExecutionMessage(val taskState: TaskState) : AgentMessage
data class TaskCompletedMessage(val summary: String) : AgentMessage
data class TaskFailedMessage(val reason: String) : AgentMessage

/**
 * 规划相关
 */
data class PlanRequestMessage(
    val goal: String,
    val taskId: String? = null,
    val lastError: String? = null
) : AgentMessage
data class PlanGeneratedMessage(
    val taskState: TaskState,
    val aiPrompt: String? = null,
    val aiResponse: String? = null,
    val plannerHistory: List<LlmChatMessage>? = null
) : AgentMessage

/**
 * 执行循环相关
 */
data class StepStartedMessage(val stepIndex: Int, val taskState: TaskState) : AgentMessage

/**
 * 观察相关
 */
data class ObservationGeneratedMessage(
    val taskState: TaskState,
    val screenshot: Bitmap
) : AgentMessage

/**
 * 执行相关
 */
data class ExecutionRequestMessage(
    val taskState: TaskState,
    val screenshot: Bitmap
) : AgentMessage

data class ExecutionGeneratedMessage(
    val description: String,
    val actionJson: String,
    val screenshotPath: String? = null,
    val aiPrompt: String? = null,
    val aiResponse: String? = null
) : AgentMessage

data class StateUpdatedMessage(
    val taskState: TaskState,
    val observation: String? = null
) : AgentMessage

data class CurrentAppUpdateMessage(
    val packageName: String
) : AgentMessage

/**
 * 动作执行相关
 */
data class ActionRequestMessage(val actionJson: String) : AgentMessage
data class ActionExecutedMessage(
    val success: Boolean,
    val message: String?,
    val actionType: String,
    val resultData: String? = null
) : AgentMessage
