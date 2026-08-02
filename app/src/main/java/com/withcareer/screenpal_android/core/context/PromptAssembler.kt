package com.withcareer.screenpal_android.core.context

import com.withcareer.screenpal_android.data.preference.TaskState

/**
 * 提示词组装器
 * 负责动态组装 Prompt，包含防死循环、结果校验等通用逻辑
 *
 */
class PromptAssembler {

    /**
     * 组装系统提示词
     *
     * @param strategyInstructions 策略特定的指令
     * @param dateString 当前日期字符串
     * @param language 语言代码 (zh/en)
     * @param appList 已安装应用列表
     * @param taskState 任务状态（可选）
     * @return 完整的系统提示词
     */
    fun assembleSystemPrompt(
        strategyInstructions: String,
        dateString: String,
        language: String = "zh",
        appList: List<String> = emptyList(),
        taskState: TaskState? = null
    ): String {
        val resources = PromptResourceProvider.getResources()
        return buildString {
            append(resources.getRoleDefinition(dateString))
            append("\n\n")

            if (taskState != null) {
                append(formatTaskState(taskState, language))
                append("\n\n")
            }

            // 将按键集触发规则提前到核心原则之后
            if (strategyInstructions.contains("按键集触发规则")) {
                val triggerRuleSection = strategyInstructions.substringAfter("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", "")
                    .substringBefore("**Current App Skills:**", "")
                if (triggerRuleSection.isNotBlank()) {
                    append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    append(triggerRuleSection.substringBefore("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"))
                    append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n")
                }
            }

            append(resources.corePrinciples)
            append("\n\n")

            append("**Strategy & App Discovery:**\n")
            append(strategyInstructions)

            if (!strategyInstructions.contains("Launch", ignoreCase = true)) {
                 append("\n\n")
                 append(resources.launchStrategy)
            } else {
                 val keyword = "Launch 指令失败"
                 if (!strategyInstructions.contains(keyword, ignoreCase = true)) {
                     append("\n\n")
                     append(resources.launchStrategy)
                 }
            }
            append("\n\n")

            if (appList.isNotEmpty()) {
                append("**Installed Apps (Available for Launch):**\n")
                append("Here is the list of apps installed on the device. You can use 'Launch' action with these exact names:\n")
                append("[")
                append(appList.joinToString(", "))
                append("]")
                append("\n\n")
            }

            append(resources.nonTaskHandling)
            append("\n\n")

            append(resources.outputFormatRequirements)
            append("\n\n")

            append("\n\n")

            append(resources.exceptionHandling)
            append("\n\n")

            append(resources.generalRules)
            append("\n")
            append("2. 务必使用${if (language == "en") "英文" else "中文"}进行思考和回复。")
        }
    }


    private fun formatTaskState(taskState: TaskState, language: String): String {
        val header = "**Task Context (任务上下文):**"

        return buildString {
            append("$header\n")
            append("<task_context>\n")

            append("  <goal>${escapeXml(taskState.goal)}</goal>\n")
            append("  <status>${escapeXml(taskState.status.name)}</status>\n")

            if (taskState.summary.isNotEmpty()) {
                append("  <summary>${escapeXml(taskState.summary)}</summary>\n")
            }

            if (taskState.note.isNotEmpty()) {
                append("  <note>${escapeXml(taskState.note)}</note>\n")
            }

            if (taskState.memory.isNotEmpty()) {
                append("  <memory>\n")
                taskState.memory.forEach { (k, v) ->
                    append("    <item key=\"${escapeXml(k)}\">${escapeXml(v)}</item>\n")
                }
                append("  </memory>\n")
            }

            if (taskState.subtasks.isNotEmpty()) {
                append("  <subtasks>\n")
                val firstPending = taskState.subtasks.find { it.status.name == "PENDING" }
                if (firstPending != null) {
                    append("    <!-- ⚡ CURRENT SUBTASK: Execute this one only -->\n")
                    append("    <subtask id=\"${escapeXml(firstPending.id)}\" status=\"PENDING\" current=\"true\">${escapeXml(firstPending.description)}</subtask>\n")
                    append("    <!-- Other subtasks (DO NOT execute these yet) -->\n")
                    taskState.subtasks.filter { it.id != firstPending.id }.forEach { sub ->
                        append("    <subtask id=\"${escapeXml(sub.id)}\" status=\"${escapeXml(sub.status.name)}\">${escapeXml(sub.description)}</subtask>\n")
                    }
                } else {
                    taskState.subtasks.forEach { sub ->
                        append("    <subtask id=\"${escapeXml(sub.id)}\" status=\"${escapeXml(sub.status.name)}\">${escapeXml(sub.description)}</subtask>\n")
                    }
                }
                append("  </subtasks>\n")
            }

            append("</task_context>\n")
        }
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
