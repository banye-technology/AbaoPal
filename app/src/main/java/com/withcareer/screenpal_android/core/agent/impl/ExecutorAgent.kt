package com.withcareer.screenpal_android.core.agent.impl

import android.graphics.Bitmap
import android.util.Log
import com.withcareer.screenpal_android.ScreenPalApplication
import kotlinx.coroutines.runBlocking
import com.withcareer.screenpal_android.core.llm.ModelResponse
import com.withcareer.screenpal_android.core.context.SkillBasedStrategy
import com.withcareer.screenpal_android.core.skill.Skill
import com.withcareer.screenpal_android.core.skill.SkillRegistry
import com.withcareer.screenpal_android.data.preference.TaskState
import com.withcareer.screenpal_android.core.llm.ChatMessage
import com.withcareer.screenpal_android.core.llm.ContentItem
import com.withcareer.screenpal_android.core.llm.ImageUrl
import com.withcareer.screenpal_android.util.BitmapUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.withcareer.screenpal_android.core.context.PromptAssembler
import com.withcareer.screenpal_android.util.ActionJsonUtils
import com.withcareer.screenpal_android.core.safety.SafetyGuard
import com.withcareer.screenpal_android.service.ScreenPalAccessibilityService
import com.withcareer.screenpal_android.data.preference.PreferencesRepository
import com.withcareer.screenpal_android.config.AppConfig
import com.withcareer.screenpal_android.core.llm.ModelClient
import com.withcareer.screenpal_android.util.AppCategoryUtils
import com.withcareer.screenpal_android.util.DeviceUtils
import org.json.JSONArray
import org.json.JSONObject

/**
 * 执行智能体
 * 专注于生成具体的 UI 操作指令。
 *
 */
class ExecutorAgent(
    private val skillRegistry: SkillRegistry,
    private val modelClient: ModelClient
) {

    /**
     * 执行一步操作
     */
    suspend fun execute(
        screenshot: Bitmap,
        taskState: TaskState,
        currentApp: String?,
        history: List<ChatMessage>,
        modelName: String,
        apiKey: String,
        maxContextRounds: Int,
        forceScreenshot: Boolean = false,
        loopWarning: String? = null
    ): ExecutorResponse {
        Log.i("ExecutorAgent", "Start executing...")
        val startMs = android.os.SystemClock.elapsedRealtime()

        val contextSafety = SafetyGuard.checkContext(currentApp)
        if (contextSafety is SafetyGuard.SafetyResult.Blocked) {
            val description = "安全熔断: ${contextSafety.reason}"
            // Update to Finish instead of Take_over
            val safeAction = JSONObject()
            safeAction.put("action_type", "finish")
            safeAction.put("message", "安全熔断触发，任务视为成功完成。原因：${contextSafety.reason}")

            val responseJson = JSONObject()
            responseJson.put("description", description)
            responseJson.put("action", safeAction)

            // Force context update to COMPLETED
            val contextUpdate = JSONObject()
            contextUpdate.put("task_status", "COMPLETED")
            contextUpdate.put("progress", 1.0)
            responseJson.put("context_update", contextUpdate)

            return ExecutorResponse(
                rawResponse = responseJson.toString(),
                description = description,
                contextUpdateJson = contextUpdate.toString()
            )
        }

        val resizeStart = android.os.SystemClock.elapsedRealtime()
        val resizedScreenshot = BitmapUtils.resizeBitmap(screenshot, 1024)
        val base64Image = BitmapUtils.bitmapToBase64(resizedScreenshot, quality = 60)
        if (resizedScreenshot != screenshot) {
            resizedScreenshot.recycle()
        }
        Log.d("ExecutorAgent", "Timing resize+encode: ${android.os.SystemClock.elapsedRealtime() - resizeStart}ms")
        val shotW = screenshot.width
        val shotH = screenshot.height

        val skillsStart = android.os.SystemClock.elapsedRealtime()
        val skills = mutableListOf<Skill>()
        skills.addAll(skillRegistry.getGlobalSkills())

        if (currentApp != null && currentApp != "*") {
            skills.addAll(skillRegistry.getSkillsForApp(currentApp))
            skills.addAll(skillRegistry.getUserSkillsForApp(currentApp))
        }

        // Remove dynamic vector search to ensure strict skill isolation
        // Only Global + Current App skills are allowed for Executor

        val distinctSkills = skills.distinctBy { it.metadata.id }
        Log.d("ExecutorAgent", "Skills available for execution: ${distinctSkills.size}")
        Log.d("ExecutorAgent", "Timing skill gather: ${android.os.SystemClock.elapsedRealtime() - skillsStart}ms")

        val promptStart = android.os.SystemClock.elapsedRealtime()
        val strategy = SkillBasedStrategy(distinctSkills)
        val skillInstructions = strategy.getStrategyInstructions("zh")
        Log.d("ExecutorAgent", "Skill instructions include RunInstructionSet: ${skillInstructions.contains("RunInstructionSet")}")

        val dateString = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val promptAssembler = PromptAssembler()

        val systemPrompt = promptAssembler.assembleSystemPrompt(
            strategyInstructions = skillInstructions,
            dateString = dateString,
            language = "zh",
            taskState = taskState
        )
        Log.d("ExecutorAgent", "Timing prompt assemble: ${android.os.SystemClock.elapsedRealtime() - promptStart}ms")

        val messages = mutableListOf<ChatMessage>()
        messages.add(ChatMessage("system", listOf(ContentItem("text", systemPrompt))))
        messages.addAll(history.takeLast(maxContextRounds))

        val userContent = mutableListOf<ContentItem>()

        val recentActionsSummary = summarizeRecentActions(history)
        if (recentActionsSummary.isNotEmpty()) {
             userContent.add(ContentItem(type = "text", text = recentActionsSummary))
             userContent.add(ContentItem(type = "text", text = "请检查上述 [Recent Actions]。如果你发现自己正在重复执行相同的无效操作（如连续点击同一坐标且页面未变），请立即停止重复，并尝试 Scroll 或 Back！"))
        }

        // Activate dead loop detection logic
        val internalLoopWarning = detectDeadLoop(history)
        val finalLoopWarning = if (!loopWarning.isNullOrEmpty()) {
            if (internalLoopWarning != null) "$loopWarning\n$internalLoopWarning" else loopWarning
        } else {
            internalLoopWarning
        }

        if (!finalLoopWarning.isNullOrEmpty()) {
            Log.w("ExecutorAgent", "Injecting loop warning")
            userContent.add(ContentItem(type = "text", text = finalLoopWarning))
        }

        // Inject trigger rule reminder if available
        if (skillInstructions.contains("按键集触发规则")) {
            val triggerReminder = buildTriggerRuleReminder(strategy.getTriggerRules(), currentApp)
            if (triggerReminder.isNotBlank()) {
                userContent.add(ContentItem(type = "text", text = triggerReminder))
            }
        }

        // Inject Simplified UI Tree
        val uiTreeStart = android.os.SystemClock.elapsedRealtime()
        var uiTreeFirstEnabled = false

        try {
            val service = ScreenPalAccessibilityService.getInstance()
            if (service != null) {
                val prefs = PreferencesRepository(service.application)
                uiTreeFirstEnabled = prefs.getUiTreeFirstEnabledSync()
                val activePackage = currentApp?.takeIf { it.isNotBlank() } ?: service.getActivePackageName()
                val isGame = AppCategoryUtils.isGame(service.application, activePackage)
                if (isGame) {
                    uiTreeFirstEnabled = false
                }

                if (forceScreenshot) {
                    uiTreeFirstEnabled = false
                    Log.i("ExecutorAgent", "UI_TREE_OVERRIDE forceScreenshot=true")
                }

                Log.i("ExecutorAgent", "UI_TREE_DECISION first=$uiTreeFirstEnabled service=true game=$isGame forceScreenshot=$forceScreenshot")
                if (uiTreeFirstEnabled) {
                    val snapshot = service.getUiTreeSnapshot(maxNodes = 240, maxDepth = 8, targetPackage = currentApp)
                    if (snapshot != null && snapshot.elements.isNotEmpty()) {
                        val (screenW, screenH) = DeviceUtils.getRealScreenSize(service.application)
                        val compactSnapshot = snapshot.toCompactSnapshot(maxItems = 120, textLimit = 80, screenWidth = screenW, screenHeight = screenH)
                        val compactJson = compactSnapshot.toJson()
                        userContent.add(ContentItem(type = "text", text = "请基于以下 UI 可操作元素列表(精简, 坐标系0-1000)生成下一步操作："))
                        userContent.add(ContentItem(type = "text", text = compactJson))
                        try {
                            val catalog = snapshot.toCompactCatalog(maxItems = 20, textLimit = 40, screenWidth = screenW, screenHeight = screenH)
                            userContent.add(ContentItem(type = "text", text = catalog))

                            Log.d("PromptUiTree", "MODE=compact")
                            Log.d("PromptUiTree", "COMPACT_JSON_LEN=${compactJson.length}")
                            Log.d("PromptUiTree", "CATALOG_LEN=${catalog.length}")
                        } catch (_: Exception) { }
                        try {
                            Log.i("ExecutorAgent", "UI_TREE_INJECTED elements=${snapshot.elements.size} compactJsonLen=${compactJson.length}")
                        } catch (_: Exception) { }
                    } else {
                        Log.w("ExecutorAgent", "UI Tree snapshot failed or empty. Auto-fallback to Visual Mode (Screenshot).")
                        uiTreeFirstEnabled = false
                    }
                }
            } else {
                Log.w("ExecutorAgent", "UI_TREE_DECISION service=false")
                // If service is not available, we should also fallback to screenshot if not already doing so
                if (uiTreeFirstEnabled) {
                     uiTreeFirstEnabled = false
                }
            }
        } catch (e: Exception) {
            Log.e("ExecutorAgent", "Failed to inject UI tree", e)
            // On error, also fallback
            if (uiTreeFirstEnabled) {
                 uiTreeFirstEnabled = false
            }
        }
        Log.d("ExecutorAgent", "Timing UI tree: ${android.os.SystemClock.elapsedRealtime() - uiTreeStart}ms")

        if (!uiTreeFirstEnabled) {
            val resizedScreenshot = BitmapUtils.resizeBitmap(screenshot, 1024)
            val base64Image = BitmapUtils.bitmapToBase64(resizedScreenshot, quality = 60)
            if (resizedScreenshot != screenshot) {
                resizedScreenshot.recycle()
            }

            userContent.add(ContentItem(type = "text", text = "请根据当前屏幕截图执行下一步操作。"))
            userContent.add(ContentItem(type = "image_url", imageUrl = ImageUrl(url = "data:image/jpeg;base64,$base64Image")))
        }

        messages.add(
            ChatMessage(
                role = "user",
                content = userContent
            )
        )

        val effectiveClient = modelClient
        val effectiveApiKey = apiKey
        val effectiveModelName = modelName

        val llmStart = android.os.SystemClock.elapsedRealtime()
        var response: ModelResponse = effectiveClient.request(
            messages = messages,
            modelName = effectiveModelName,
            apiKey = effectiveApiKey,
            temperature = 0.0,
            topP = 0.1,
            enableSearch = false,
            enableThinking = false
        )
        Log.d("ExecutorAgent", "Timing LLM request: ${android.os.SystemClock.elapsedRealtime() - llmStart}ms")

        if (uiTreeFirstEnabled && isGetCurrentScreenshotToolCall(response.action)) {
            val resizedScreenshot = BitmapUtils.resizeBitmap(screenshot, 1024)
            val base64Image = BitmapUtils.bitmapToBase64(resizedScreenshot, quality = 60)
            if (resizedScreenshot != screenshot) {
                resizedScreenshot.recycle()
            }

            messages.add(ChatMessage("assistant", listOf(ContentItem("text", response.action))))
            messages.add(
                ChatMessage(
                    role = "user",
                    content = listOf(
                        ContentItem(type = "text", text = "已提供当前截图，请基于截图与 UI 树继续输出下一步操作 JSON。"),
                        ContentItem(type = "image_url", imageUrl = ImageUrl(url = "data:image/jpeg;base64,$base64Image"))
                    )
                )
            )

            response = effectiveClient.request(
                messages = messages,
                modelName = effectiveModelName,
                apiKey = effectiveApiKey,
                temperature = 0.0,
                topP = 0.1,
                enableSearch = false,
                enableThinking = false
            )
        }

        Log.d("ExecutorAgent", "LLM_RESPONSE_ACTION_LEN=${response.action.length}")

        var description = response.thinking
        var contextUpdateJson: String? = null
        var finalRawResponse = response.action

        try {
            val parseStart = android.os.SystemClock.elapsedRealtime()
            val jsonString = ActionJsonUtils.extractJsonFromText(response.action)
            val json = JSONObject(jsonString)
            if (json.has("description")) {
                description = json.getString("description")
            } else if (json.has("thought")) {
                description = json.getString("thought")
            }
            if (json.has("context_update")) {
                contextUpdateJson = json.getJSONObject("context_update").toString()
            }

            // --- Safety Guard Check ---
            if (json.has("action")) {
                val actionObj = json.get("action")
                if (actionObj is JSONObject) {
                    val toolName = actionObj.optString("action", "")

                    val safetyResult = SafetyGuard.checkAction(currentApp, toolName)
                    if (safetyResult is SafetyGuard.SafetyResult.Blocked) {
                        Log.w("ExecutorAgent", "Action blocked by SafetyGuard; treating task as completed")
                        description = "任务已完成（支付安全熔断触发）：${safetyResult.reason}"

                        // Construct Finish action instead of Take_over
                        val safeAction = JSONObject()
                        safeAction.put("action_type", "finish")
                        safeAction.put("message", "安全熔断触发，任务视为成功完成。原因：${safetyResult.reason}")

                        json.put("action", safeAction)
                        json.put("description", description)

                        // Force context update to COMPLETED
                        val contextUpdate = JSONObject()
                        contextUpdate.put("task_status", "COMPLETED")
                        contextUpdate.put("progress", 1.0)
                        json.put("context_update", contextUpdate)

                        // Update local variables to ensure consistency
                        contextUpdateJson = contextUpdate.toString()
                        finalRawResponse = json.toString()
                    }
                }
            }
            // ---------------------------
            Log.d("ExecutorAgent", "Timing response parse: ${android.os.SystemClock.elapsedRealtime() - parseStart}ms")

        } catch (e: Exception) {
            Log.w("ExecutorAgent", "JSON parse failed, attempting to repair with LLM...", e)
            try {
                val repairedJsonStr = repairJsonWithLlm(response.action, apiKey, modelName)
                val json = JSONObject(repairedJsonStr)
                if (json.has("description")) {
                    description = json.getString("description")
                } else if (json.has("thought")) {
                    description = json.getString("thought")
                }
                if (json.has("context_update")) {
                    contextUpdateJson = json.getJSONObject("context_update").toString()
                }

                // --- Safety Guard Check (Repaired) ---
                if (json.has("action")) {
                    val actionObj = json.get("action")
                    if (actionObj is JSONObject) {
                        val toolName = actionObj.optString("action", "")

                        val safetyResult = SafetyGuard.checkAction(currentApp, toolName)
                        if (safetyResult is SafetyGuard.SafetyResult.Blocked) {
                            Log.w("ExecutorAgent", "Repaired action blocked by SafetyGuard")
                            description = "安全熔断: ${safetyResult.reason}"

                            val safeAction = JSONObject()
                            safeAction.put("action_type", "finish")
                            safeAction.put("message", "安全熔断触发，任务视为成功完成。原因：${safetyResult.reason}")

                            json.put("action", safeAction)
                            json.put("description", description)

                            val contextUpdate = JSONObject()
                            contextUpdate.put("task_status", "COMPLETED")
                            contextUpdate.put("progress", 1.0)
                            json.put("context_update", contextUpdate)

                            return ExecutorResponse(
                                rawResponse = json.toString(),
                                description = description,
                                contextUpdateJson = contextUpdate.toString(),
                                fullPrompt = messages
                            )
                        }
                    }
                }
                // -------------------------------------

                return ExecutorResponse(
                    rawResponse = repairedJsonStr,
                    description = description,
                    contextUpdateJson = contextUpdateJson,
                    fullPrompt = messages
                )
            } catch (repairEx: Exception) {
                Log.e("ExecutorAgent", "JSON repair failed", repairEx)
            }
        }

        return ExecutorResponse(
            rawResponse = finalRawResponse,
            description = description,
            contextUpdateJson = contextUpdateJson,
            fullPrompt = messages
        )
    }

    /**
     * 使用 LLM 修复损坏的 JSON
     */
    private suspend fun repairJsonWithLlm(brokenJson: String, apiKey: String, preferredModelName: String): String {
        val prompt = """
            You are a JSON repair expert. The following text contains a broken or malformed JSON object.
            Please repair it and output ONLY the valid JSON object. Do not add any markdown, explanations, or other text.

            Broken JSON:
            $brokenJson
        """.trimIndent()

        val messages = listOf(
            ChatMessage("user", listOf(ContentItem("text", prompt)))
        )

        val repairModelCandidates = linkedSetOf(preferredModelName, AppConfig.PLANNING_MODEL_NAME)
        var lastException: Exception? = null

        for (candidate in repairModelCandidates) {
            try {
                val response = modelClient.request(
                    messages = messages,
                    modelName = candidate,
                    apiKey = apiKey,
                    temperature = 0.0,
                    topP = 0.1,
                    enableSearch = false
                )
                return ActionJsonUtils.extractJsonFromText(response.action)
            } catch (e: Exception) {
                lastException = e
            }
        }

        throw lastException ?: IllegalStateException("JSON repair failed")
    }

    private fun detectDeadLoop(history: List<ChatMessage>): String? {
        val assistantMsgs = history.filter { it.role == "assistant" }.takeLast(10)
        if (assistantMsgs.size < 2) return null

        val actions = assistantMsgs.map { msg ->
            val content = msg.content.firstOrNull { it.type == "text" }?.text ?: ""
            try {
                val jsonStr = ActionJsonUtils.extractJsonFromText(content)
                val json = JSONObject(jsonStr)
                if (json.has("action")) {
                    val actionObj = json.get("action")
                    if (actionObj is JSONObject) {
                        val type = actionObj.optString("action_type", "")
                        val act = actionObj.optString("action", "")

                        val elementObj = actionObj.opt("element")
                        val element = when (elementObj) {
                            is JSONArray -> elementObj.toString()
                            is JSONObject -> elementObj.toString()
                            else -> actionObj.optString("element", "")
                        }

                        "$type|$act|$element"
                    } else {
                        actionObj.toString()
                    }
                } else {
                    ""
                }
            } catch (e: Exception) {
                ""
            }
        }.filter { it.isNotEmpty() }

        if (actions.isNotEmpty()) Log.d("ExecutorAgent", "Recent action signatures: ${actions.size}")

        if (actions.isEmpty()) return null

        val lastAction = actions.last()

        // 1. AA Check
        if (actions.size >= 2) {
            val secondLast = actions[actions.size - 2]
            // Exclude RunInstructionSet from loop detection (intentional repetition for multiple execution)
            val isRunInstructionSet = lastAction.contains("RunInstructionSet")
            if (lastAction == secondLast && !isRunInstructionSet) {
                return "SYSTEM ALERT: Infinite loop detected. The last 2 actions were identical ($lastAction). You MUST NOT repeat the same action. Perform 'scroll' or 'back' immediately."
            }
        }

        // 2. ABAB Check
        if (actions.size >= 4) {
            val secondLast = actions[actions.size - 2]
            val thirdLast = actions[actions.size - 3]
            val fourthLast = actions[actions.size - 4]

            // Exclude RunInstructionSet from alternating pattern detection
            val containsRunInstructionSet = lastAction.contains("RunInstructionSet") ||
                                           secondLast.contains("RunInstructionSet")

            if (lastAction == thirdLast && secondLast == fourthLast && lastAction != secondLast && !containsRunInstructionSet) {
                 return "SYSTEM ALERT: Infinite loop detected. You are alternating between two actions ($secondLast -> $lastAction). You MUST stop this loop. Try a different action like 'scroll' or 'back'."
            }
        }

        return null
    }

    private fun buildTriggerRuleReminder(
        triggerRules: List<SkillBasedStrategy.TriggerRule>,
        currentApp: String?
    ): String {
        if (triggerRules.isEmpty()) return ""

        val relevantRules = if (currentApp != null) {
            triggerRules.filter { rule ->
                when {
                    currentApp.contains("douyin", ignoreCase = true) ||
                    currentApp.contains("aweme", ignoreCase = true) ->
                        rule.title.contains("抖音", ignoreCase = true)
                    currentApp.contains("meituan", ignoreCase = true) ->
                        rule.title.contains("美团", ignoreCase = true) || rule.title.contains("外卖", ignoreCase = true)
                    currentApp.contains("wechat", ignoreCase = true) ||
                    currentApp.contains("tencent.mm", ignoreCase = true) ->
                        rule.title.contains("微信", ignoreCase = true)
                    else -> true // 如果无法匹配，显示所有规则
                }
            }
        } else {
            triggerRules
        }

        if (relevantRules.isEmpty()) return ""

        return buildString {
            append("\n")
            append("=" .repeat(60) + "\n")
            append("⚡⚡⚡ 按键集强制规则（MANDATORY INSTRUCTION SET RULES）⚡⚡⚡\n")
            append("=" .repeat(60) + "\n")
            relevantRules.forEach { rule ->
                val repeatText = rule.repeat?.let { "，重复${it}次" } ?: ""
                append("\n【按键集】${rule.title}\n")
                append("  触发条件：${rule.condition}\n")
                append("  调用方式：RunInstructionSet(instruction_title=\"${rule.title}\"${if (rule.repeat != null) ", repeat=${rule.repeat}" else ""})\n")
                if (rule.repeat != null) {
                    append("  执行次数：${rule.repeat}次\n")
                }
                append("\n")
            }
            append("⚠️ 关键约束：\n")
            append("  1. 触发条件满足时，【必须】使用 RunInstructionSet\n")
            append("  2. 【严禁】用手动 Tap/Swipe 替代按键集\n")
            append("  3. 按键集是预录制的完整动作序列，比手动操作更可靠\n")
            append("  4. 只有在【没有匹配的按键集】时才使用手动操作\n")
            append("=" .repeat(60) + "\n\n")
        }
    }

    private fun summarizeRecentActions(history: List<ChatMessage>): String {
        val summary = StringBuilder()

        val assistantMsgs = history.filter { it.role == "assistant" }.takeLast(5)
        if (assistantMsgs.isEmpty()) return ""

        summary.append("**Recent Actions History (Latest 5 steps):**\n")

        assistantMsgs.forEachIndexed { index, msg ->
            val content = msg.content.firstOrNull { it.type == "text" }?.text ?: ""
            try {
                 val jsonStr = ActionJsonUtils.extractJsonFromText(content)
                 val json = JSONObject(jsonStr)
                 val desc = if (json.has("description")) json.getString("description") else "No description"

                 var actionDetail = ""
                 if (json.has("action")) {
                     val actionObj = json.get("action")
                     if (actionObj is JSONObject) {
                         val type = actionObj.optString("action_type", "")
                         val act = actionObj.optString("action", "")
                         val element = actionObj.optString("element", "")
                         actionDetail = "[$type/$act] $element"
                     } else if (actionObj is String) {
                         actionDetail = actionObj
                     }
                 }

                 summary.append("${index + 1}. $desc $actionDetail\n")
            } catch (e: Exception) {
                 // ignore parse error in summary
            }
        }
        return summary.toString()
    }

    private fun isGetCurrentScreenshotToolCall(raw: String): Boolean {
        return try {
            val jsonString = ActionJsonUtils.extractJsonFromText(raw)
            val json = JSONObject(jsonString)
            val tool = json.optString("tool", "")
            tool.equals("get_current_screenshot", ignoreCase = true) || tool.equals("GetCurrentScreenshot", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

}

data class ExecutorResponse(
    val rawResponse: String,
    val description: String,
    val contextUpdateJson: String? = null,
    val fullPrompt: List<ChatMessage>? = null
)
