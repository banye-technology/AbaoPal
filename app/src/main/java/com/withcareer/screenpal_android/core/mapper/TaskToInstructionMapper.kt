package com.withcareer.screenpal_android.core.mapper

import com.withcareer.screenpal_android.data.preference.Task
import com.withcareer.screenpal_android.data.room.InstructionSetEntity
import com.withcareer.screenpal_android.data.room.InstructionStepEntity
import com.withcareer.screenpal_android.util.ActionJsonUtils
import com.google.gson.JsonParser
import org.json.JSONObject
import java.util.UUID

object TaskToInstructionMapper {

    fun map(task: Task, authorName: String = "Me"): Pair<InstructionSetEntity, List<InstructionStepEntity>> {
        val setId = UUID.randomUUID().toString()

        // 1. Extract plan steps from subtasks
        val planSteps = task.state?.subtasks?.map { subtask ->
             val params = JSONObject().apply {
                put("instruction", subtask.description)
                put("app", "")
            }
            InstructionStepEntity(
                setId = setId,
                orderIndex = 0, // Will be re-indexed
                actionType = "LLM_PLAN",
                actionParams = params.toString(),
                description = subtask.description
            )
        } ?: emptyList()

        // 2. Extract execution steps
        val validSteps = task.steps.filter { step ->
            if (step.action.isNullOrBlank()) return@filter false
            // Filter out non-JSON actions (e.g. "Generate Plan" placeholder)
            try {
                val jsonStr = ActionJsonUtils.extractJsonFromText(step.action)
                if (jsonStr.isBlank()) return@filter false
                val element = JsonParser.parseString(jsonStr)
                element.isJsonObject
            } catch (e: Exception) {
                false
            }
        }

        val executionSteps = validSteps.map { step ->
            InstructionStepEntity(
                setId = setId,
                orderIndex = 0,
                actionType = "ATOMIC", // Always use ATOMIC for replayed actions
                actionParams = step.action, // Store raw JSON
                description = step.thought.takeIf { !it.isNullOrBlank() } ?: "Execute Step",
                delayBefore = 1200L // Default delay for AI generated steps
            )
        }

        // 3. Combine
        val allSteps = mutableListOf<InstructionStepEntity>()
        allSteps.addAll(planSteps)
        allSteps.addAll(executionSteps)

        val instructionSteps = allSteps.mapIndexed { index, step ->
            step.copy(orderIndex = index)
        }

        val instructionSet = InstructionSetEntity(
            id = setId,
            title = task.prompt.take(50), // Truncate if too long
            description = task.prompt,
            authorId = "self",
            authorName = authorName,
            createdAt = System.currentTimeMillis(),
            executionCount = 0
        )

        return Pair(instructionSet, instructionSteps)
    }
}
