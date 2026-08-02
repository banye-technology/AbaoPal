package com.withcareer.screenpal_android.core.agent.impl

import android.util.Log
import com.withcareer.screenpal_android.data.preference.Subtask
import com.withcareer.screenpal_android.data.preference.TaskState
import com.withcareer.screenpal_android.util.ActionJsonUtils
import org.json.JSONObject

/**
 * Parses the response from the LLM for the Planner Agent.
 */
class PlannerParser {

    sealed class ParseResult {
        data class ToolCall(val toolName: String, val parameters: JSONObject) : ParseResult()
        data class Plan(val taskState: TaskState) : ParseResult()
        object Error : ParseResult()
    }

    fun parse(
        jsonString: String,
        taskId: String,
        taskPrompt: String,
        notes: String
    ): ParseResult {
        try {
            val cleanJson = ActionJsonUtils.extractJsonFromText(jsonString)
            if (cleanJson.isBlank()) {
                return ParseResult.Error
            }
            val planJson = JSONObject(cleanJson)

            // 1. Check for MCP Tool Call
            val toolName = planJson.optString("tool")
            if (toolName.isNotEmpty()) {
                val params = planJson.optJSONObject("parameters") ?: JSONObject()
                return ParseResult.ToolCall(toolName, params)
            }

            // 2. Check for Legacy Plan Format (Tool Call inside plan array)
            val planArray = planJson.optJSONArray("plan")
            if (planArray != null && planArray.length() > 0) {
                val firstStep = planArray.getJSONObject(0)
                val action = firstStep.optString("action")

                if (action == "read_skill") {
                    val args = firstStep.optJSONObject("args") ?: JSONObject()
                    // Map legacy args to parameters
                    return ParseResult.ToolCall("read_skill", args)
                }
            }

            // 3. Process Final Plan
            val thought = planJson.optString("thought")
            val analysis = planJson.optString("analysis")

            if (planArray == null || planArray.length() == 0) {
                // Empty plan (Direct Answer or Info Query)
                val answer = if (analysis.isNotBlank()) analysis else thought

                val state = TaskState(
                    taskId = taskId,
                    goal = taskPrompt,
                    subtasks = mutableListOf(
                        Subtask(id = "direct_execution", description = "Process Request")
                    ),
                    summary = answer,
                    note = notes
                )
                return ParseResult.Plan(state)
            }

            val subtasks = mutableListOf<Subtask>()
            for (i in 0 until planArray.length()) {
                val step = planArray.getJSONObject(i)
                val id = step.optString("step_id")
                val description = step.optString("description")
                // 全局不展示包名
                val fullDesc = description
                subtasks.add(Subtask(id = id, description = fullDesc))
            }

            // Construct summary
            val summaryContent = StringBuilder()
            if (analysis.isNotBlank()) {
                summaryContent.append("分析: $analysis")
            }
            if (thought.isNotBlank()) {
                if (summaryContent.isNotEmpty()) summaryContent.append("\n")
                summaryContent.append("思考: $thought")
            }

            val finalState = TaskState(
                taskId = taskId,
                goal = taskPrompt,
                subtasks = subtasks,
                summary = summaryContent.toString(),
                note = notes
            )

            return ParseResult.Plan(finalState)

        } catch (e: Exception) {
            Log.e("PlannerParser", "Error parsing plan JSON", e)
            return ParseResult.Error
        }
    }
}
