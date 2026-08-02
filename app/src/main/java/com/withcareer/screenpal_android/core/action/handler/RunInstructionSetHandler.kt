package com.withcareer.screenpal_android.core.action.handler

import android.util.Log
import kotlinx.coroutines.flow.first
import com.google.gson.JsonObject
import com.withcareer.screenpal_android.ScreenPalApplication
import com.withcareer.screenpal_android.core.action.ExecuteResult
import com.withcareer.screenpal_android.core.skill.Skill
import com.withcareer.screenpal_android.data.model.SkillInstructionSet
import com.withcareer.screenpal_android.service.ScreenPalAccessibilityService

/**
 * 执行本地按键集（指令集）
 */
class RunInstructionSetHandler : ActionHandler {
    override fun getActionType(): String = "RunInstructionSet"

    override suspend fun execute(
        service: ScreenPalAccessibilityService,
        params: JsonObject,
        screenWidth: Int,
        screenHeight: Int
    ): ExecuteResult {
        Log.d("RunInstructionSet", "Instruction-set request received")
        val instructionId = params.get("instruction_id")?.asString
            ?: params.get("instructionId")?.asString
            ?: params.get("id")?.asString

        val instructionTitle = params.get("instruction_title")?.asString
            ?: params.get("instructionTitle")?.asString
            ?: params.get("title")?.asString
            ?: params.get("name")?.asString
        val repeatCount = params.get("repeat")?.asInt
            ?: params.get("times")?.asInt
            ?: 1

        if (instructionId.isNullOrBlank() && instructionTitle.isNullOrBlank()) {
            Log.w("RunInstructionSet", "Missing instruction_id/instruction_title")
            return ExecuteResult(
                success = false,
                message = "缺少 instruction_id 或 instruction_title"
            )
        }

        val app = service.applicationContext as? ScreenPalApplication
            ?: return ExecuteResult(success = false, message = "无法获取应用上下文")

        // 确保 Skills 加载完成，防止漏掉 Skill 内嵌的指令集
        app.skillRegistry.waitForUserSkillsLoad()

        val repository = app.instructionRepository
        val instructionSet = if (!instructionId.isNullOrBlank()) {
            Log.d("RunInstructionSet", "Resolving instruction set by ID")
            repository.getInstructionSet(instructionId)
        } else {
            val normalizedTitle = normalizeTitle(instructionTitle!!)
            Log.d("RunInstructionSet", "Resolving instruction set by title")
            repository.resolveInstructionSet(normalizedTitle)
        }

        // 如果在InstructionSetEntity中未找到，尝试从技能中的SkillInstructionSet查找
        if (instructionSet == null) {
            Log.d("RunInstructionSet", "Not found in InstructionSetEntity, searching in Skill instruction sets")
            val skillInstructionSet = findSkillInstructionSet(app, instructionId, instructionTitle)
            if (skillInstructionSet != null) {
                Log.i("RunInstructionSet", "Found matching skill instruction set")
                // 使用 AgentRuntime 的统一执行方法，复用 InstructionRunner 逻辑
                val success = app.agentRuntime.runSkillInstructionSetSync(skillInstructionSet, repeatCount)
                return if (success) {
                    ExecuteResult(
                        success = true,
                        message = "按键集 ${skillInstructionSet.name} 执行成功 (${repeatCount}次)"
                    )
                } else {
                    ExecuteResult(
                        success = false,
                        message = "按键集 ${skillInstructionSet.name} 执行失败"
                    )
                }
            }
        }

        if (instructionSet == null) {
            Log.w("RunInstructionSet", "Instruction set not found")
            // Try to list available instruction sets for debugging
            try {
                val sets = repository.allInstructionSets.first()
                Log.d("RunInstructionSet", "Available instruction set count: ${sets.size}")
            } catch (e: Exception) {
                Log.e("RunInstructionSet", "Failed to list instruction sets", e)
            }
            return ExecuteResult(
                success = false,
                message = "未找到指令集: ${instructionId ?: instructionTitle}。请确认按键集名称正确。"
            )
        }

        val repeat = repeatCount.coerceAtLeast(1)
        Log.i("RunInstructionSet", "Running instruction set: repeat=$repeat")

        // Synchronously execute instruction set to prevent ExecutorAgent from continuing
        val success = app.agentRuntime.runInstructionSetSync(instructionSet.id, repeat)

        return if (success) {
            ExecuteResult(
                success = true,
                message = "指令集 ${instructionSet.title} 执行成功 (${repeat}次)"
            )
        } else {
            ExecuteResult(
                success = false,
                message = "指令集 ${instructionSet.title} 执行失败"
            )
        }
    }

    private fun normalizeTitle(raw: String): String {
        var normalized = raw.trim()
            .removePrefix("《").removeSuffix("》")
            .removePrefix("「").removeSuffix("」")
            .removePrefix(""").removeSuffix(""")
            .removePrefix("\"").removeSuffix("\"")
            .removePrefix("'").removeSuffix("'")
            .removePrefix("@")

        // Remove common prefixes
        listOf("按键集", "指令集", "录制").forEach { prefix ->
            if (normalized.startsWith(prefix)) {
                normalized = normalized.removePrefix(prefix).trim().removePrefix(":").removePrefix("：").trim()
            }
        }

        return normalized
    }

    /**
     * 从当前活跃技能中查找SkillInstructionSet
     */
    private fun findSkillInstructionSet(app: ScreenPalApplication, id: String?, title: String?): SkillInstructionSet? {
        val normalizedTitle = if (title != null) normalizeTitle(title) else null
        val currentApp = app.agentRuntime.getCurrentApp()

        // 获取全局技能 + 当前应用的技能（合并为扁平列表便于遍历）
        val skills = mutableListOf<Skill>()
        skills.addAll(app.skillRegistry.getGlobalSkills())
        if (currentApp != null && currentApp != "*") {
            skills.addAll(app.skillRegistry.getSkillsForApp(currentApp))
            skills.addAll(app.skillRegistry.getUserSkillsForApp(currentApp))
        }

        for (skill in skills) {
            skill.instructionSets?.forEach { set ->
                // Check ID match
                if (!id.isNullOrBlank() && set.id == id) {
                    return set
                }

                // Check Title match
                if (!normalizedTitle.isNullOrBlank()) {
                    val setNormalizedName = normalizeTitle(set.name)
                    if (setNormalizedName.equals(normalizedTitle, ignoreCase = true) ||
                        set.name.equals(title, ignoreCase = true)) {
                        return set
                    }
                }
            }
        }

        return null
    }
}
