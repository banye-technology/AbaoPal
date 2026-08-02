package com.withcareer.screenpal_android.core.agent.impl

import android.app.Application
import android.util.Log
import com.withcareer.screenpal_android.R
import com.withcareer.screenpal_android.core.agent.framework.*
import com.withcareer.screenpal_android.core.llm.ModelClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * 规划智能体 (Event-Driven)
 */
class PlannerAgentWrapper(
    private val application: Application,
    modelClient: ModelClient,
    private val apiKey: String,
    private val modelName: String,
    bus: AgentBus,
    scope: CoroutineScope,
    private val skipPlanning: Boolean = false,
    private val allowedSkillIds: List<String>? = null
) : BaseAgent(bus, scope) {

    private val innerAgent = PlannerAgent(application, modelClient)

    override suspend fun handleMessage(message: AgentMessage) {
        withContext(Dispatchers.IO) {
            when (message) {
                is PlanRequestMessage -> {
                    handlePlanning(message.goal, message.taskId, message.lastError)
                }
                else -> {}
            }
        }
    }

    private suspend fun handlePlanning(goal: String, taskId: String?, lastError: String?) {
        Log.d("PlannerAgent", "Planning started")
        Log.d("PlannerAgentWrapper", "Calling planner with allowedSkillCount=${allowedSkillIds?.size ?: 0}")
        try {
            val result = innerAgent.plan(
                taskPrompt = goal,
                modelName = modelName,
                apiKey = apiKey,
                taskId = taskId,
                lastError = lastError,
                allowedSkillIds = allowedSkillIds
            )
            if (result != null) {
                Log.d("PlannerAgent", "Plan generated: subtasks=${result.taskState.subtasks.size}")

                bus.emit(PlanGeneratedMessage(
                    taskState = result.taskState,
                    aiPrompt = result.aiPrompt,
                    aiResponse = result.aiResponse,
                    plannerHistory = result.chatHistory
                ))
            } else {
                bus.emit(TaskFailedMessage(application.getString(R.string.plan_generate_failed)))
            }
        } catch (e: Exception) {
            Log.e("PlannerAgent", "Error planning", e)
            val errorMessage = application.getString(
                R.string.plan_generate_error,
                e.message ?: ""
            ).trimEnd()
            bus.emit(TaskFailedMessage(errorMessage))
        }
    }
}
