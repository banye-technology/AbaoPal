package com.withcareer.screenpal_android.core.agent.impl

import com.google.gson.Gson
import com.withcareer.screenpal_android.core.context.PlanningPromptResourceProvider
import com.withcareer.screenpal_android.core.skill.Skill
import com.withcareer.screenpal_android.core.skill.SkillType
import com.withcareer.screenpal_android.core.llm.ChatMessage
import com.withcareer.screenpal_android.core.llm.ContentItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds prompts for the Planner Agent.
 */
class PlannerPromptBuilder {

    fun buildSystemPrompt(
        appList: List<String>,
        skills: List<Skill>,
        expandedSkillIds: Set<String> = emptySet()
    ): String {
        val dateString = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val resources = PlanningPromptResourceProvider.getResources()
        val gson = Gson()

        return buildString {
            append("今天的日期是: $dateString\n")
            append(resources.roleDefinition)
            append("\n\n")

            // 1. Tool Definitions (MCP)
            append(resources.toolDefinitions)
            append("\n\n")

            // 2. Task Guidelines
            append(resources.taskAnalysisGuidelines)
            append("\n\n")

            if (appList.isNotEmpty()) {
                append("**Installed Apps:**\n[${appList.joinToString(", ")}]\n\n")
            }

            // 检测是否有用户自定义 Skills 和 App Skills
            val userSkills = skills.filter { it.metadata.id.startsWith("user.") }
            val appSkills = skills.filter { it.metadata.type == SkillType.APP }

            // 如果同时存在用户 Skills 和 App Skills，添加优先级提示
            if (userSkills.isNotEmpty() && appSkills.isNotEmpty()) {
                append("**重要提示：**\n")
                append("检测到用户自定义 Skills（ID 以 'user.' 开头）和应用操作 Skills（ID 以 'app.' 开头）。\n")
                append("- **用户自定义 Skills** 包含个性化信息（如联系人的具体昵称、特定配置等）。\n")

                // 只有当有未展开的用户技能时，才提示必须读取
                val hasUnexpandedUserSkills = userSkills.any { !expandedSkillIds.contains(it.metadata.id) }
                if (hasUnexpandedUserSkills) {
                     append("- **这些信息仅存在于详细内容中**。你必须使用 `read_skill` 工具读取那些未展示详情的用户自定义 Skills。\n")
                } else {
                     append("- **相关用户技能的详情已在下方直接展示**，请直接参考其指令进行规划。\n")
                     append("- **注意：相关的基础应用技能（App Skills）也已展开。**\n")
                     append("- **严禁**调用 `read_skill` 除非你确定缺少某个关键技能的详情。\n")
                }

                append("- **应用 Skills** 提供详细的应用操作指南和 UI 导航信息。\n")
                append("- **强制执行顺序**：\n")
                append("  1. 优先使用用户自定义 Skills 的信息（如联系人昵称）\n")
                append("  2. 结合应用 Skills 了解操作步骤\n")
                append("  3. 制定完整计划\n\n")
            }

            // 按键集触发规则说明
            append("**按键集（Instruction Set）说明：**\n")
            append("- 用户 Skill 的 instruction 中可能包含 `@按键集名称` 引用\n")
            append("- 这表示用户已录制好的完整动作序列，无需拆分成具体步骤\n")
            append("- **规划原则**：看到 `@按键集` 引用时，规划时用简洁描述即可，不要拆成具体操作步骤\n")
            append("- 执行阶段会自动识别并调用按键集\n\n")

            // 3. Available Skills
            append("**Available Skills Index:**\n")
            if (skills.isNotEmpty()) {
                // 优先显示用户 Skills
                userSkills.forEach { skill ->
                    appendSkillInfo(this, skill, expandedSkillIds.contains(skill.metadata.id), gson, "[USER]")
                }

                // 然后显示其他 Task Skills
                skills.filter { it.metadata.type == SkillType.TASK && !it.metadata.id.startsWith("user.") }.forEach { skill ->
                    appendSkillInfo(this, skill, expandedSkillIds.contains(skill.metadata.id), gson, "")
                }

                // 最后显示 App Skills
                appSkills.forEach { skill ->
                    appendSkillInfo(this, skill, expandedSkillIds.contains(skill.metadata.id), gson, "[APP]")
                }
            }
            append("\n")

            // 4. Tool Use Protocol & Output Format
            append(resources.toolUseGuidelines)
            append("\n\n")
            append(resources.outputFormat)
            append("\n\n")

            append(resources.examples)
        }
    }

    private fun appendSkillInfo(
        sb: StringBuilder,
        skill: Skill,
        isExpanded: Boolean,
        gson: Gson,
        prefix: String
    ) {
        val appsInfo = if (skill.metadata.targetApps.isNotEmpty()) " | Apps: ${skill.metadata.targetApps.joinToString(",")}" else ""

        if (isExpanded) {
            sb.append("--- $prefix Skill Details: ${skill.metadata.name} (${skill.metadata.id}) ---\n")
            sb.append("Desc: ${skill.metadata.description}$appsInfo\n")

            if (skill.instruction.isNotBlank()) {
                sb.append("[Instruction]\n${skill.instruction}\n")
            }
            // Removed UI Navigation Tree to reduce prompt size. The Planner rarely needs this level of detail.
            // If strictly needed, the agent can use 'read_skill' tool to fetch it.
            /*
            if (!skill.userPathTree.isNullOrEmpty()) {
                sb.append("[UI Navigation Tree (JSON)]\n")
                try {
                    val treeJson = gson.toJson(skill.userPathTree)
                    sb.append(treeJson)
                } catch (e: Exception) {
                    sb.append("[Error serializing tree]")
                }
                sb.append("\n")
            }
            */
            sb.append("------------------------------------------------\n")
        } else {
            val warning = if (prefix == "[USER]") " | ⚠️ 必读（真实信息在 instruction 中，请用 read_skill）" else ""
            sb.append("- $prefix ID: ${skill.metadata.id} | Name: ${skill.metadata.name} | Desc: ${skill.metadata.description}$appsInfo$warning\n")
        }
    }

    fun buildInitialUserPrompt(
        taskPrompt: String,
        notes: String,
        history: List<String>,
        lastError: String?
    ): String {
        return buildString {
            append(taskPrompt)
            if (notes.isNotEmpty()) {
                append("\n\n**Memory / Notes (Important Context):**\n$notes")
            }
            if (history.isNotEmpty()) {
                append("\n\n**Execution History:**\n")
                history.forEach { append("- $it\n") }
            }
            if (lastError != null) {
                append("\n\n**Last Error / Replan Reason:**\n$lastError")
            }
        }
    }

    fun buildToolOutputMessage(skillContent: String): ChatMessage {
        return ChatMessage(
            role = "user",
            content = listOf(ContentItem(type = "text", text = skillContent))
        )
    }
}
