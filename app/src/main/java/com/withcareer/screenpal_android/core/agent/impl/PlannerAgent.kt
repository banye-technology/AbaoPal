package com.withcareer.screenpal_android.core.agent.impl

import android.app.Application
import android.util.Log
import com.google.gson.Gson
import com.withcareer.screenpal_android.core.llm.ModelClient
import com.withcareer.screenpal_android.core.skill.Skill
import com.withcareer.screenpal_android.core.skill.SkillRegistry
import com.withcareer.screenpal_android.data.preference.TaskState
import com.withcareer.screenpal_android.data.preference.Subtask
import com.withcareer.screenpal_android.core.llm.ChatMessage
import com.withcareer.screenpal_android.core.llm.ContentItem
import com.withcareer.screenpal_android.util.DeviceUtils

import com.withcareer.screenpal_android.ScreenPalApplication
import com.withcareer.screenpal_android.data.preference.TaskRepository

/**
 * 规划智能体
 * 负责解析用户意图，拆解任务步骤。
 * Refactored to use PlannerPromptBuilder and PlannerParser.
 *
 */
class PlannerAgent(
    private val application: Application,
    private val modelClient: ModelClient
) {

    private val taskRepository: TaskRepository by lazy {
        (application as ScreenPalApplication).taskRepository
    }

    private val skillRegistry: SkillRegistry by lazy {
        (application as ScreenPalApplication).skillRegistry
    }

    private val promptBuilder = PlannerPromptBuilder()
    private val parser = PlannerParser()

    data class PlanResult(
        val taskState: TaskState,
        val aiPrompt: String?,
        val aiResponse: String?,
        val chatHistory: List<ChatMessage>
    )

    /**
     * 执行任务规划
     */
    suspend fun plan(
        taskPrompt: String,
        modelName: String,
        apiKey: String,
        taskId: String? = null,
        history: List<String> = emptyList(),
        lastError: String? = null,
        allowedSkillIds: List<String>? = null
    ): PlanResult? {
        Log.i("PlannerAgent", "Start planning")

        val startMs = android.os.SystemClock.elapsedRealtime()

        // 1. Prepare Context
        var effectiveHistory = history
        var notes = ""

        if (taskId != null) {
            val task = taskRepository.tasks.value.find { it.id == taskId }
            if (task != null) {
                 if (task.steps.isNotEmpty() && effectiveHistory.isEmpty()) {
                     effectiveHistory = task.steps.map { step ->
                         "Thought: ${step.thought} | Action: ${step.action}"
                     }
                 }
                 notes = task.state?.note ?: ""
            }
        }

        val appListStart = android.os.SystemClock.elapsedRealtime()
        val appList = DeviceUtils.getInstalledAppNames(application)
        Log.d("PlannerAgent", "Timing app list: ${android.os.SystemClock.elapsedRealtime() - appListStart}ms")

        val skillStart = android.os.SystemClock.elapsedRealtime()
        val skills = if (allowedSkillIds != null) {
            skillRegistry.getSkillsByIds(allowedSkillIds)
        } else {
            skillRegistry.getAllSkills()
        }
        Log.d("PlannerAgent", "Timing skill load: ${android.os.SystemClock.elapsedRealtime() - skillStart}ms")
        val allowedSkillIdSet = allowedSkillIds?.toSet()
        skills.filter { it.metadata.id.startsWith("user.") }.forEach { skill ->
            Log.d(
                "PlannerAgent",
                "User skill available: instructionLength=${skill.instruction.length}"
            )
        }

        // Determine which skills to pre-expand in the prompt
        val expandedSkillIds = mutableSetOf<String>()
        var skillsForPrompt = skills
        if (allowedSkillIds != null) {
            expandedSkillIds.addAll(allowedSkillIds.filter { it.startsWith("user.") })
        }

        try {
            // 2. Build Initial Prompt
            val promptStart = android.os.SystemClock.elapsedRealtime()
            val systemPromptStr = promptBuilder.buildSystemPrompt(appList, skillsForPrompt, expandedSkillIds)
            val userPromptStr = promptBuilder.buildInitialUserPrompt(taskPrompt, notes, effectiveHistory, lastError)
            Log.d("PlannerAgent", "Timing prompt build: ${android.os.SystemClock.elapsedRealtime() - promptStart}ms")
            Log.d("PlannerAgent", "Prompt size: System=${systemPromptStr.length}, User=${userPromptStr.length}, Total=${systemPromptStr.length + userPromptStr.length} chars")

            val chatHistory = mutableListOf(
                ChatMessage(role = "system", content = listOf(ContentItem(type = "text", text = systemPromptStr))),
                ChatMessage(role = "user", content = listOf(ContentItem(type = "text", text = userPromptStr)))
            )

            // 3. ReAct Loop (Max 3 Rounds)
            var round = 0
            val maxRounds = 3

            while (round < maxRounds) {
                round++
                Log.d("PlannerAgent", "Planning Round: $round")

                // Force disable thinking for planner to speed up response time
                val enableThinking = false

                // 4. Call LLM
                val llmStart = android.os.SystemClock.elapsedRealtime()
                val planResponse = modelClient.request(
                    messages = chatHistory,
                    modelName = modelName,
                    apiKey = apiKey,
                    temperature = 0.0,
                    topP = 0.1,
                    enableSearch = false,
                    enableThinking = enableThinking,
                    maxTokens = 2048
                )
                Log.d("PlannerAgent", "Timing LLM request: ${android.os.SystemClock.elapsedRealtime() - llmStart}ms")

                chatHistory.add(ChatMessage(role = "assistant", content = listOf(ContentItem(type = "text", text = planResponse.action))))

                // 5. Parse Response
                val parseStart = android.os.SystemClock.elapsedRealtime()
                val parseResult = parser.parse(
                    jsonString = planResponse.action,
                    taskId = taskId ?: System.currentTimeMillis().toString(),
                    taskPrompt = taskPrompt,
                    notes = notes
                )
                Log.d("PlannerAgent", "Timing plan parse: ${android.os.SystemClock.elapsedRealtime() - parseStart}ms")

                // 6. Handle Parse Result
                when (parseResult) {
                    is PlannerParser.ParseResult.ToolCall -> {
                        // Execute Tool (Only read_skill supported currently)
                        if (parseResult.toolName == "read_skill") {
                            val skillId = parseResult.parameters.optString("skill_id")
                            if (!skillId.isNullOrEmpty()) {
                                val includeUiTree = parseResult.parameters.optBoolean("include_ui_tree", false)
                                val skillContent = handleReadSkill(
                                    skillId = skillId,
                                    allowedSkillIdSet = allowedSkillIdSet,
                                    availableSkills = skills,
                                    includeUiTree = includeUiTree
                                )
                                val toolMsg = promptBuilder.buildToolOutputMessage(skillContent)
                                chatHistory.add(toolMsg)
                                continue // Next round
                            }
                        }
                        // If tool not found or invalid params, maybe break or continue?
                        // For now, let's treat as error/end of loop if no valid skillId
                         break
                    }
                    is PlannerParser.ParseResult.Plan -> {
                        val totalMs = android.os.SystemClock.elapsedRealtime() - startMs
                        Log.d("PlannerAgent", "Timing plan total: ${totalMs}ms")
                        return PlanResult(
                            taskState = parseResult.taskState,
                            aiPrompt = formatChatHistoryForDebug(chatHistory),
                            aiResponse = planResponse.action,
                            chatHistory = chatHistory.toList()
                        )
                    }
                    is PlannerParser.ParseResult.Error -> {
                         // Parse failed, stop loop
                         break
                    }
                }
            }

            Log.d("PlannerAgent", "Timing plan total: ${android.os.SystemClock.elapsedRealtime() - startMs}ms")
            return null // Max rounds reached without valid plan

        } catch (e: Exception) {
            Log.e("PlannerAgent", "Planning failed", e)
            return null
        }
    }

    private fun handleReadSkill(
        skillId: String,
        allowedSkillIdSet: Set<String>?,
        availableSkills: List<Skill>,
        includeUiTree: Boolean
    ): String {
        if (allowedSkillIdSet != null && !allowedSkillIdSet.contains(skillId)) {
            return "**Tool Output (read_skill):**\nError: Skill ID '$skillId' is not allowed."
        }

        val skill = availableSkills.find { it.metadata.id == skillId }

        return if (skill != null) {
             val sb = StringBuilder()
             sb.append("**Tool Output (read_skill):**\n")
             sb.append("--- Skill Details: ${skill.metadata.name} (${skill.metadata.id}) ---\n")
             if (skill.metadata.targetApps.isNotEmpty()) {
                 sb.append("[Target Apps]\n${skill.metadata.targetApps.joinToString(", ")}\n")
             }
             if (skill.instruction.isNotBlank()) {
                 sb.append("[Instruction]\n${skill.instruction}\n")
             }
             if (includeUiTree && !skill.userPathTree.isNullOrEmpty()) {
                 sb.append("[UI Navigation Tree (JSON)]\n")
                 try {
                     val treeJson = Gson().toJson(skill.userPathTree)
                     sb.append(treeJson)
                 } catch (e: Exception) {
                     sb.append("[Error serializing tree]")
                 }
                 sb.append("\n")
             }
             sb.toString()
        } else {
             "**Tool Output (read_skill):**\nError: Skill ID '$skillId' not found."
        }
    }

    private fun formatChatHistoryForDebug(history: List<ChatMessage>): String {
        val sb = StringBuilder()
        history.forEach { msg ->
            sb.append("--- [${msg.role}] ---\n")
            msg.content.forEach { item ->
                if (item.type == "text") {
                    sb.append(item.text).append("\n")
                }
            }
            sb.append("\n")
        }
        return sb.toString()
    }

}
