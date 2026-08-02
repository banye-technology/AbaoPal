package com.withcareer.screenpal_android.core.context

import com.withcareer.screenpal_android.core.skill.Skill
import com.withcareer.screenpal_android.core.mcp.ToolRegistry
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import android.util.Log

/**
 * 基于 Skill 配置文件的通用策略
 */
class SkillBasedStrategy(private val skills: List<Skill>) {

    private val mapper = ObjectMapper().apply {
        setSerializationInclusion(JsonInclude.Include.NON_NULL)
    }

    data class TriggerRule(
        val condition: String,
        val title: String,
        val repeat: Int? = null
    )

    fun getTriggerRules(): List<TriggerRule> {
        val rules = mutableListOf<TriggerRule>()
        skills.forEach { skill ->
            if (skill.instruction.isNotBlank()) {
                rules.addAll(extractTriggerRules(skill.instruction))
            }
        }
        return rules.distinctBy { "${it.title}|${it.condition}|${it.repeat ?: 1}" }
    }

    fun getStrategyInstructions(language: String): String {
        val sb = StringBuilder()
        val triggerRules = mutableListOf<TriggerRule>()

        // 1. 聚合 Skill Instructions
        if (skills.isNotEmpty()) {
            sb.append("Current App Skills:\n")
            skills.forEach { skill ->
                sb.append("--- ${skill.metadata.name} (${skill.metadata.id}) ---\n")

                // Append Instruction
                if (skill.instruction.isNotBlank()) {
                    sb.append("[Instruction]\n")
                    sb.append(skill.instruction).append("\n")
                    val rules = extractTriggerRules(skill.instruction)
                    if (rules.isNotEmpty()) {
                        Log.d("SkillBasedStrategy", "Found trigger rules: count=${rules.size}")
                    }
                    triggerRules.addAll(rules)
                }

                // Append User Path Tree as JSON
                if (!skill.userPathTree.isNullOrEmpty()) {
                    try {
                        val treeJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(skill.userPathTree)
                        sb.append("\n[UI Navigation Tree (JSON)]\n")
                        sb.append(treeJson).append("\n")
                    } catch (e: Exception) {
                        // Ignore serialization errors, just skip the tree
                    }
                }

                // Append Instruction Sets
                if (!skill.instructionSets.isNullOrEmpty()) {
                    sb.append("\n[可用按键集]\n")
                    skill.instructionSets.forEach { set ->
                        sb.append("  • 名称: ${set.name}\n")
                        sb.append("    描述: ${set.description}\n")
                        sb.append("    步骤数: ${set.steps.size}\n")
                        sb.append("    调用方式: RunInstructionSet {\"instruction_title\": \"${set.name}\"}\n")
                    }
                    sb.append("\n")
                    Log.d("SkillBasedStrategy", "Found instruction sets: count=${skill.instructionSets.size}")
                }

                sb.append("\n")
            }
        }

        val distinctRules = triggerRules.distinctBy { "${it.title}|${it.condition}|${it.repeat ?: 1}" }
        if (distinctRules.isNotEmpty()) {
            Log.d("SkillBasedStrategy", "Distinct trigger rule count: ${distinctRules.size}")
        }

        if (distinctRules.isNotEmpty()) {
            sb.append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
            sb.append("⚡ **按键集触发规则（最高优先级）**\n")
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n")

            distinctRules.forEach { rule ->
                val repeatText = rule.repeat?.let { "，重复 $it 次" } ?: ""
                sb.append("✓ 条件：${rule.condition}\n")
                sb.append("  → 调用：RunInstructionSet\n")
                sb.append("  → 参数：instruction_title=\"${rule.title}\"${if (rule.repeat != null) ", repeat=${rule.repeat}" else ""}\n")
                sb.append("\n")
            }

            sb.append("**重要提醒：**\n")
            sb.append("- 条件满足时，立即调用 RunInstructionSet，不要手动操作。\n")
            sb.append("- 按键集会自动完成所有操作，比手动执行更可靠。\n")
            sb.append("- 完成后在 context_update 中标记**单个子任务** ID。\n")
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n")
        }

        // 2. 聚合 Tools
        val allToolNames = skills.flatMap { it.allowedTools }.distinct().toMutableList()

        // 确保核心通用工具始终可用
        val universalTools = listOf("RecordNote", "Wait", "Finish", "Home", "Back", "Tap", "Type", "Scroll", "GetCurrentScreenshot")
        allToolNames.addAll(universalTools)

        if (distinctRules.isNotEmpty()) {
            allToolNames.add("RunInstructionSet")
            Log.d("SkillBasedStrategy", "RunInstructionSet enabled by trigger rules")
        }

        // 去重
        val finalToolNames = allToolNames.distinct()

        val tools = ToolRegistry.getTools(finalToolNames)
        val toolsPrompt = ToolRegistry.generateToolsPrompt(tools)

        sb.append(toolsPrompt)

        return sb.toString()
    }

    private fun extractTriggerRules(text: String): List<TriggerRule> {
        if (text.isBlank()) return emptyList()
        val rules = mutableListOf<TriggerRule>()
        val lines = text.lines()

        val atPattern = Regex("@([A-Za-z0-9_\\-\\u4e00-\\u9fa5]{1,40})")
        val repeatPattern = Regex("重复(?:调用)?\\s*(\\d+)")
        val cleanPattern = Regex("(调用|执行|触发)?\\s*@?[A-Za-z0-9_\\-\\u4e00-\\u9fa5]{1,40}|按键集|指令集|录制")

        lines.forEachIndexed { index, line ->
            val match = atPattern.find(line) ?: return@forEachIndexed
            val title = match.groupValues.getOrNull(1)?.trim()
            if (title.isNullOrBlank()) return@forEachIndexed

            val repeat = repeatPattern.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: lines.getOrNull(index + 1)?.let { next ->
                    repeatPattern.find(next)?.groupValues?.getOrNull(1)?.toIntOrNull()
                }

            val cleaned = line
                .replace(match.value, "")
                .replace(repeatPattern, "")
                .replace(cleanPattern, "")
                .replace("，", "")
                .replace(",", "")
                .trim()

            val condition = if (cleaned.isNotBlank()) {
                cleaned
            } else {
                val previous = (index - 1 downTo 0)
                    .map { lines[it].trim() }
                    .firstOrNull { candidate ->
                        candidate.isNotBlank() &&
                            !candidate.contains("@") &&
                            !repeatPattern.containsMatchIn(candidate)
                    }
                previous?.replace(cleanPattern, "")?.trim().orEmpty()
            }.ifBlank { "满足技能指令条件" }

            rules.add(TriggerRule(condition = condition, title = title, repeat = repeat))
        }

        return rules
    }
}
