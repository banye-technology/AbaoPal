package com.withcareer.screenpal_android.util

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.withcareer.screenpal_android.data.preference.TaskState
import com.withcareer.screenpal_android.data.preference.TaskStatus
/**
 * 工具类：处理 Action JSON 的解析、清理和任务状态更新
 *
 */
object ActionJsonUtils {

    private const val MAX_MEMORY_ENTRIES = 30
    private const val MAX_MEMORY_VALUE_LENGTH = 200
    private const val MAX_SUMMARY_LENGTH = 800

    /**
     * 处理任务状态更新
     * 从 LLM 返回的 context_update 字段更新 TaskState
     */
    fun processTaskStateUpdate(updateJson: JsonObject, taskState: TaskState) {
        // 1. Update Memory (Variables)
        if (updateJson.has("variables")) {
            val vars = updateJson.getAsJsonObject("variables")
            vars.keySet().forEach { key ->
                val value = vars.get(key).asString.take(MAX_MEMORY_VALUE_LENGTH)
                taskState.memory[key] = value
            }
            trimMemory(taskState)
        }

        // 2. Update Summary
        if (updateJson.has("summary")) {
            taskState.summary = updateJson.get("summary").asString.take(MAX_SUMMARY_LENGTH)
        }

        // 3. Update Overall Status
        if (updateJson.has("status")) {
            try {
                var statusStr = updateJson.get("status").asString.uppercase()
                // Map common LLM completions to COMPLETED
                if (statusStr == "FINISHED" || statusStr == "DONE" || statusStr == "END") {
                    statusStr = "COMPLETED"
                }
                taskState.status = TaskStatus.valueOf(statusStr)
            } catch (e: Exception) {
                // Ignore invalid status
            }
        }

        // 4. Update Subtasks
        if (updateJson.has("subtasks")) {
            try {
                val subtasksArray = updateJson.getAsJsonArray("subtasks")
                subtasksArray.forEach { element ->
                    if (element.isJsonObject) {
                        val subObj = element.asJsonObject
                        val id = subObj.get("id")?.asString
                        val statusStr = subObj.get("status")?.asString

                        if (id != null && statusStr != null) {
                            var subtask = taskState.subtasks.find { it.id == id }

                            // Fallback: Try matching by description if ID lookup fails
                            if (subtask == null) {
                                subtask = taskState.subtasks.find {
                                    it.description == id || // Maybe model used description as ID
                                    it.description.contains(id, ignoreCase = true) || // Partial match
                                    (subObj.has("description") && it.description == subObj.get("description").asString) // Explicit description match
                                }
                            }

                            if (subtask != null) {
                                try {
                                    var normalizedStatus = statusStr.uppercase()
                                    if (normalizedStatus == "FINISHED" || normalizedStatus == "DONE" || normalizedStatus == "END") {
                                        normalizedStatus = "COMPLETED"
                                    }
                                    subtask.status = TaskStatus.valueOf(normalizedStatus)
                                } catch (e: Exception) {
                                    // Ignore invalid status
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore malformed subtasks
            }
        }
    }

    private fun trimMemory(taskState: TaskState) {
        if (taskState.memory.size <= MAX_MEMORY_ENTRIES) return

        val keysToRemove = taskState.memory.keys.take(taskState.memory.size - MAX_MEMORY_ENTRIES)
        keysToRemove.forEach { taskState.memory.remove(it) }
    }

    /**
     * 从文本中提取 JSON 字符串 (处理 Markdown 代码块和基础语法修复)
     */
    fun extractJsonFromText(text: String): String {
        // 1. 尝试去除 Markdown 代码块
        var cleanJson = text.trim()
        if (cleanJson.startsWith("```")) {
             val firstNewLine = cleanJson.indexOf('\n')
             cleanJson = if (firstNewLine != -1) {
                 cleanJson.substring(firstNewLine + 1)
             } else {
                 cleanJson.replace(Regex("^```[a-zA-Z]*"), "")
             }
             if (cleanJson.endsWith("```")) {
                 cleanJson = cleanJson.substring(0, cleanJson.length - 3)
             }
             cleanJson = cleanJson.trim()
        }

        // 2. 预处理：提取第一个完整的 JSON 对象
        val extracted = extractFirstJsonObject(cleanJson)
        if (!extracted.isNullOrBlank()) {
            cleanJson = extracted
        } else {
            val firstOpen = cleanJson.indexOf('{')
            val lastClose = cleanJson.lastIndexOf('}')
            if (firstOpen != -1 && lastClose > firstOpen) {
                cleanJson = cleanJson.substring(firstOpen, lastClose + 1)
            }
        }

        // 3. 增强的正则修复
        cleanJson = fixJsonSyntax(cleanJson)

        // 4. 尝试补全缺失的右大括号
        val openCount = cleanJson.count { it == '{' }
        val closeCount = cleanJson.count { it == '}' }
        if (openCount > closeCount) {
             val missing = openCount - closeCount
             cleanJson += "}".repeat(missing)
        }

        return cleanJson
    }

    private fun extractFirstJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start == -1) return null

        var depth = 0
        var inString = false
        var escape = false

        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                if (escape) {
                    escape = false
                    continue
                }
                when (c) {
                    '\\' -> escape = true
                    '"' -> inString = false
                }
                continue
            }

            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }

        return null
    }

    /**
     * 修复 JSON 语法
     */
    private fun fixJsonSyntax(json: String): String {
        var cleanJson = json
        // 1. 给 key 加引号
        cleanJson = cleanJson.replace(Regex("([{,])\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*:"), "$1\"$2\":")
        // 2. 去除尾部逗号
        cleanJson = cleanJson.replace(Regex(",\\s*([}\\]])"), "$1")
        return cleanJson
    }

    private const val MAX_DISPLAY_TEXT_LEN = 20

    /**
     * 将 action JSON 转为用户可读的简短描述，用于对话框等 UI 展示。
     * 解析失败或非标准格式时返回简短占位文案，避免直接展示原始 JSON。
     */
    fun formatActionForDisplay(rawJson: String): String {
        if (rawJson.isBlank()) return "执行操作"
        return try {
            val jsonStr = extractJsonFromText(rawJson)
            if (jsonStr.isBlank()) return fallbackDisplay(rawJson)
            val root = JsonParser.parseString(jsonStr)
            if (!root.isJsonObject) return fallbackDisplay(rawJson)
            val rootObj = root.asJsonObject
            val actionObj = if (rootObj.has("action") && rootObj.get("action").isJsonObject) {
                rootObj.getAsJsonObject("action")
            } else {
                rootObj
            }
            val actionType = actionObj.get("action_type")?.asString?.lowercase() ?: ""
            when (actionType) {
                "finish" -> {
                    val msg = actionObj.get("message")?.asString?.trim()
                    if (!msg.isNullOrBlank()) "✅ 任务完成：$msg" else "✅ 任务完成"
                }
                "do" -> formatDoAction(actionObj)
                "take_over", "takeover" -> {
                    val msg = actionObj.get("message")?.asString?.trim()
                    if (!msg.isNullOrBlank()) "⚠️ $msg" else "⚠️ 安全拦截"
                }
                else -> formatDoAction(actionObj).ifBlank { fallbackDisplay(rawJson) }
            }
        } catch (e: Exception) {
            fallbackDisplay(rawJson)
        }
    }

    private fun formatDoAction(obj: JsonObject): String {
        val act = when (val a = obj.get("action")) {
            null -> ""
            else -> if (a.isJsonPrimitive) a.asString else ""
        }.lowercase()
        val element = obj.get("element")
        val coords = element?.takeIf { it.isJsonArray }?.asJsonArray?.let { arr ->
            if (arr.size() >= 2) {
                val x = arr[0].asFloat
                val y = arr[1].asFloat
                if (x in 0.01..0.99 || y in 0.01..0.99) "" else " (${x.toInt()}, ${y.toInt()})"
            } else ""
        } ?: ""
        val text = obj.get("text")?.asString?.trim()
        val direction = obj.get("direction")?.asString?.trim()?.lowercase()
        val app = obj.get("app")?.asString?.trim()
        return when (act) {
            "tap", "click" -> "👆 点击$coords"
            "longpress", "long press" -> "长按$coords"
            "scroll" -> when (direction) {
                "up", "向上" -> "📜 向上滚动"
                "down", "向下" -> "📜 向下滚动"
                "left", "向左" -> "📜 向左滚动"
                "right", "向右" -> "📜 向右滚动"
                else -> "📜 滚动"
            }
            "swipe" -> "👆 滑动"
            "type", "input" -> if (!text.isNullOrBlank()) {
                val display = if (text.length > MAX_DISPLAY_TEXT_LEN) text.take(MAX_DISPLAY_TEXT_LEN) + "…" else text
                "⌨️ 输入：$display"
            } else "⌨️ 输入"
            "wait" -> "⏳ 等待"
            "home" -> "🏠 主屏"
            "back" -> "⬅️ 返回"
            "enter" -> "↵ 确认"
            "launch" -> if (!app.isNullOrBlank()) "🚀 启动：$app" else "🚀 启动应用"
            "runinstructionset", "run_instruction_set" -> "📋 执行指令集"
            else -> if (act.isNotBlank()) "执行：$act" else "执行操作"
        }
    }

    private fun fallbackDisplay(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{") && trimmed.length > 60) return "执行操作"
        return trimmed.take(50).let { if (trimmed.length > 50) "$it…" else it }
    }
}
