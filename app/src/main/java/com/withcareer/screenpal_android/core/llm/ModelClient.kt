package com.withcareer.screenpal_android.core.llm

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import java.io.IOException
/**
 * 模型响应数据类
 */
data class ModelResponse(
    val thinking: String,
    val action: String
)

/**
 * 模型客户端，用于与大模型进行交互
 * 负责构建请求、发送请求以及解析响应
 */
class ModelClient(
    baseUrl: String
) {
    private val api: LlmApi

    companion object {
        private const val TAG = "ModelClient"
    }

    init {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            redactHeader("Authorization")
            level = HttpLoggingInterceptor.Level.NONE
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

        val finalBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        val retrofit = Retrofit.Builder()
            .baseUrl(finalBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(LlmApi::class.java)
    }

    /**
     * 请求模型（使用消息上下文）
     * @param messages 消息列表
     * @param modelName 模型名称
     * @param apiKey API Key
     * @param temperature 温度 (默认 0.0)
     * @param maxTokens 最大 token 数 (默认 3000)
     * @return 模型响应结果
     */
    suspend fun request(
        messages: List<ChatMessage>,
        modelName: String,
        apiKey: String,
        temperature: Double = 0.0,
        topP: Double = 0.1,
        maxTokens: Int = 2048,
        enableSearch: Boolean = false,
        enableThinking: Boolean? = null
    ): ModelResponse {
        val optimizedMessages = optimizeMessages(messages)

        // Doubao Model Logic:
        // If it's a Doubao model and enableThinking is explicitly false,
        // we set thinking = { type: "disabled" } to speed up execution.
        val isDoubao = modelName.contains("doubao", ignoreCase = true)
        val thinkingConfig = if (isDoubao && enableThinking == false) {
             ThinkingConfig("disabled")
        } else {
             null
        }

        // For Doubao, we prefer using the 'thinking' object over 'enable_thinking' boolean if possible,
        // or we can send both. But to be safe and clean, let's use the object config for Doubao.
        // However, we still pass enableThinking to the request if it's not handled by thinkingConfig,
        // or we can pass it anyway if the API supports it.
        // Given user instructions, the priority is the 'thinking' object.

        val request = ChatRequest(
            model = modelName,
            messages = optimizedMessages,
            maxTokens = maxTokens,
            temperature = temperature,
            topP = topP,
            frequencyPenalty = 0.2,
            stream = false,
            enableSearch = enableSearch,
            enableThinking = enableThinking,
            thinking = thinkingConfig
        )

        val authHeader = if (apiKey.isNotEmpty() && apiKey != "EMPTY") {
            "Bearer $apiKey"
        } else {
            "Bearer EMPTY"
        }

        var currentAttempt = 0
        val maxRetries = 3
        var lastException: Exception? = null

        while (currentAttempt <= maxRetries) {
            try {
                if (currentAttempt > 0) {
                    val delayMs = 1000L * (1 shl (currentAttempt - 1)) // 1s, 2s, 4s
                    Log.d(TAG, "Retry attempt $currentAttempt after ${delayMs}ms")
                    delay(delayMs)
                }

                val response = api.chatCompletion(authHeader, request)

                if (response.isSuccessful && response.body() != null) {
                    val responseBody = response.body()!!
                    val content = responseBody.choices.firstOrNull()?.message?.content ?: ""

                    // Log Token Usage
                    val usage = responseBody.usage
                    if (usage != null) {
                        Log.i(TAG, "[TokenUsage] Input: ${usage.promptTokens}, Output: ${usage.completionTokens}, Total: ${usage.totalTokens}")
                    } else {
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "Model response received, length=${content.length}")
                        }
                    }

                    return parseResponse(content)
                } else {
                    val code = response.code()
                    // 仅针对服务端错误或限流进行重试
                    if (code in 500..599 || code == 429) {
                        response.errorBody()?.close()
                        throw IOException("Server error: $code")
                    } else {
                        // 4xx 错误通常是客户端请求问题，不重试
                        response.errorBody()?.close()
                        Log.e(TAG, "API request failed with HTTP status $code")
                        throw Exception("API request failed: $code ${response.message()}")
                    }
                }
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Request failed (attempt $currentAttempt): ${e.javaClass.simpleName}")

                // 如果是网络 IO 异常、服务端错误或 JSON 解析错误，继续重试
                if (e is IOException || e is com.google.gson.JsonSyntaxException || e is IllegalStateException) {
                    Log.w(TAG, "Retrying due to recoverable error: ${e.javaClass.simpleName}")
                    currentAttempt++
                    continue
                } else {
                    // 其他异常（如解析错误、4xx）直接抛出
                    throw e
                }
            }
        }

        throw lastException ?: Exception("Max retries exceeded")
    }

    /**
     * 简单的对话请求，直接返回内容字符串
     */
    suspend fun chatCompletionSimple(
        messages: List<ChatMessage>,
        modelName: String,
        apiKey: String
    ): String {
        val request = ChatRequest(
            model = modelName,
            messages = messages,
            maxTokens = 512,
            temperature = 0.0,
            stream = false
        )

        val authHeader = if (apiKey.isNotEmpty() && apiKey != "EMPTY") {
            "Bearer $apiKey"
        } else {
            "Bearer EMPTY"
        }

        try {
            val response = api.chatCompletion(authHeader, request)
            if (response.isSuccessful && response.body() != null) {
                return response.body()!!.choices.firstOrNull()?.message?.content ?: ""
            } else {
                Log.e(TAG, "Simple chat failed with HTTP status ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Simple chat error: ${e.javaClass.simpleName}")
        }
        return ""
    }

    /**
     * 拉取可用模型列表（OpenAI 兼容协议 GET /models）
     * @param apiKey API Key
     * @return 模型 id 列表；请求失败或为空时返回空列表（调用方可降级为手动输入）
     */
    suspend fun listModels(apiKey: String): List<String> {
        val authHeader = if (apiKey.isNotEmpty() && apiKey != "EMPTY") {
            "Bearer $apiKey"
        } else {
            "Bearer EMPTY"
        }

        return try {
            val response = api.listModels(authHeader)
            if (response.isSuccessful && response.body() != null) {
                response.body()!!.data.map { it.id }.filter { it.isNotBlank() }
            } else {
                Log.e(TAG, "listModels failed with HTTP status ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "listModels error: ${e.javaClass.simpleName}")
            emptyList()
        }
    }

    /**
     * 创建 Embedding
     * @param text 输入文本
     * @param modelName Embedding 模型名称
     * @param apiKey API Key
     * @return 向量列表
     */
    suspend fun createEmbedding(
        text: String,
        modelName: String,
        apiKey: String
    ): List<Double> {
        val request = EmbeddingRequest(
            model = modelName,
            input = listOf(text)
        )

        val authHeader = if (apiKey.isNotEmpty() && apiKey != "EMPTY") {
            "Bearer $apiKey"
        } else {
            "Bearer EMPTY"
        }

        val response = api.createEmbeddings(authHeader, request)

        if (response.isSuccessful && response.body() != null) {
            val data = response.body()!!.data
            if (data.isNotEmpty()) {
                return data[0].embedding
            }
        } else {
            val errorBody = response.errorBody()?.string() ?: "Unknown error"
            Log.e("ModelClient", "Embedding request failed with HTTP status ${response.code()}")

            if (response.code() == 401 && (errorBody.contains("invalid_api_key") || errorBody.contains("Incorrect API key"))) {
                throw IOException("API Key 配置错误: Embedding 请求被拒绝，请检查 API Key。")
            }
        }
        return emptyList()
    }

    /**
     * 优化消息列表：
     * 1. 移除历史消息中的图片，只保留最后一条用户消息的图片
     * 2. 剪枝历史消息中的长文本（如 UI 树），防止 Token 爆炸
     */
    private fun optimizeMessages(messages: List<ChatMessage>): List<ChatMessage> {
        if (messages.isEmpty()) return messages

        val optimizedList = mutableListOf<ChatMessage>()
        val lastUserIndex = messages.indexOfLast { it.role == "user" }

        messages.forEachIndexed { index, message ->
            if (message.role == "user" && index != lastUserIndex) {
                // 如果是历史用户消息（非最新），进行深度剪枝
                optimizedList.add(pruneHistoryMessage(message))
            } else {
                // 其他消息（系统、助手、或最后一条用户消息）保持原样
                optimizedList.add(message)
            }
        }
        return optimizedList
    }

    /**
     * 深度剪枝历史消息：
     * - 移除图片
     * - 截断超长文本（> 800字符），通常是 UI 树 JSON
     */
    private fun pruneHistoryMessage(message: ChatMessage): ChatMessage {
        val newContent = message.content.mapNotNull { item ->
            if (item.type == "text") {
                val text = item.text ?: ""
                if (text.length > 800) {
                    // 检查是否看起来像 UI 树或长 JSON
                    if (text.trimStart().startsWith("[") || text.trimStart().startsWith("{")) {
                        ContentItem(type = "text", text = "[History UI Tree Data Omitted]")
                    } else {
                        // 普通长文本也截断，保留前 200 字符作为上下文线索
                        ContentItem(type = "text", text = text.take(200) + "... [Truncated]")
                    }
                } else {
                    item
                }
            } else {
                // 丢弃非文本内容（如图片）
                null
            }
        }

        // 确保内容不为空，如果全被过滤了，留一个占位符
        val finalContent = if (newContent.isEmpty()) {
            listOf(ContentItem(type = "text", text = "[Image/Data Omitted]"))
        } else {
            newContent
        }

        return ChatMessage(
            role = message.role,
            content = finalContent
        )
    }

    /**
     * 从消息中移除图片内容，只保留文本（节省 token）
     * @deprecated Use pruneHistoryMessage instead
     */
    private fun removeImagesFromMessage(message: ChatMessage): ChatMessage {
        val textOnlyContent = message.content.filter { it.type == "text" }
        return ChatMessage(
            role = message.role,
            content = textOnlyContent
        )
    }


    private fun parseResponse(content: String): ModelResponse {
        Log.d("ModelClient", "开始解析响应内容 (长度: ${content.length})...")

        var thinking = ""
        var action: String
        var thinkingEndIndex = 0
        var contentAfterThinking = content

        // 1. 尝试从 JSON 中提取 Thinking (新策略：thought 字段)
        // 先尝试把整个内容当做 JSON 解析
        var jsonObject: JSONObject? = null
        try {
            // 尝试提取 JSON 部分（如果包含 Markdown 代码块）
            val jsonStart = content.indexOf('{')
            val jsonEnd = content.lastIndexOf('}')
            if (jsonStart != -1 && jsonEnd > jsonStart) {
                val jsonString = content.substring(jsonStart, jsonEnd + 1)
                jsonObject = JSONObject(jsonString)
            }
        } catch (e: Exception) {
            // 解析失败，可能是格式不标准
        }

        if (jsonObject != null) {
            // 优先使用 JSON 中的 thought 字段
            if (jsonObject.has("thought")) {
                thinking = jsonObject.getString("thought")
            } else if (jsonObject.has("thinking")) {
                thinking = jsonObject.getString("thinking")
            }

            // 如果 JSON 解析成功，Action 就是整个 JSON（或者需要剔除 thought？）
            // 为了兼容旧逻辑，我们保留 thought 在 JSON 中，或者也可以选择移除它
            // 这里我们直接使用提取出来的 JSON 字符串作为 Action，但在后续处理中可能需要注意
            action = jsonObject.toString()
        } else {
            // 2. 如果 JSON 解析失败，回退到旧的 <think> 标签解析逻辑 (兼容旧 Prompt)
            // 优先尝试 DeepSeek 风格 <think>...</think>，支持大小写不敏感
            val thinkTagRegex = Regex("<think>(.*?)</think>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            val thinkMatch = thinkTagRegex.find(content)

            if (thinkMatch != null) {
                thinking = thinkMatch.groupValues[1].trim()
                thinkingEndIndex = thinkMatch.range.last + 1
            } else {
                // ... (保留之前的容错逻辑)
                // 容错处理：如果没找到闭合标签，但找到了开始标签
                val startTagRegex = Regex("<think>", RegexOption.IGNORE_CASE)
                val startMatch = startTagRegex.find(content)
                if (startMatch != null) {
                     val startIndex = startMatch.range.last + 1
                     // 尝试找 JSON 的开始 (```json 或 {) 作为 Thinking 的结束
                     val jsonStartRegex = Regex("```json|\\{", RegexOption.IGNORE_CASE)
                     val jsonMatch = jsonStartRegex.find(content, startIndex)

                     if (jsonMatch != null) {
                         thinking = content.substring(startIndex, jsonMatch.range.first).trim()
                         thinkingEndIndex = jsonMatch.range.first
                     } else {
                         // 后面没有 JSON，那剩余部分全是 Thinking
                         thinking = content.substring(startIndex).trim()
                         thinkingEndIndex = content.length
                     }
                     Log.w("ModelClient", "Warning: <think> tag not closed. Extracted partially.")
                }
            }

            // 提取 Action (旧逻辑)
            contentAfterThinking = content.substring(thinkingEndIndex)
            action = extractBalancedJson(contentAfterThinking)
        }

        // 3. 兜底策略：如果上面没提取到，尝试正则提取函数调用风格
        if (action.isEmpty()) {
             val finishRegex = Regex("finish\\s*\\(\\s*message\\s*=")
             val doRegex = Regex("do\\s*\\(\\s*action\\s*=")
             val match = finishRegex.find(contentAfterThinking) ?: doRegex.find(contentAfterThinking)
             if (match != null) {
                 action = contentAfterThinking.substring(match.range.first).trim()
                 Log.d("ModelClient", "通过正则提取函数调用风格 Action 成功")
             }
        }

        // 4. 最后的补救：如果 Thinking 为空，但 Action 提取到了，尝试把 Action 之前的内容当做 Thinking
        if (thinking.isEmpty() && action.isNotEmpty()) {
            val actionIndex = content.indexOf(action)
            if (actionIndex > 0) {
                var preActionContent = content.substring(0, actionIndex)
                // 清理可能的干扰标签
                preActionContent = preActionContent
                    .replace(Regex("</?think>"), "")
                    .replace(Regex("</?tool_call>"), "")
                    .replace(Regex("</?answer>"), "")
                    .replace("```json", "")
                    .replace("```", "")
                    .trim()

                if (preActionContent.isNotEmpty()) {
                    thinking = preActionContent
                    Log.d("ModelClient", "补救: 从 Action 前提取 Thinking 成功")
                }
            }
        }

        // 5. 极端情况：整个内容就是一个 JSON
        if (action.isEmpty() && thinking.isEmpty()) {
             val trimmed = content.trim()
             if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                 action = trimmed
             }
        }

        return ModelResponse(thinking, action)
    }

    /**
     * 提取第一个平衡的 JSON 对象字符串
     */
    private fun extractBalancedJson(text: String): String {
        var braceCount = 0
        var startIndex = -1

        for (i in text.indices) {
            if (text[i] == '{') {
                if (braceCount == 0) {
                    startIndex = i
                }
                braceCount++
            } else if (text[i] == '}') {
                if (braceCount > 0) {
                    braceCount--
                    if (braceCount == 0) {
                        // 找到一个完整的闭合块
                        val candidate = text.substring(startIndex, i + 1)
                        // 验证是否包含预期的 JSON 字段 (兼容执行模型和规划模型)
                        if (candidate.contains("\"action\"") ||
                            candidate.contains("\"context_update\"") ||
                            candidate.contains("\"plan\"") ||
                            candidate.contains("\"analysis\"")) {
                            return candidate
                        }
                        // 如果不包含关键字，继续寻找下一个块 (但通常第一个大的块就是)
                        // 稍微放宽条件：如果长度足够大，也可能是
                        if (candidate.length > 20) {
                             return candidate
                        }
                    }
                }
            }
        }
        // 如果没有找到平衡的块，回退到取首尾 (针对只有半个括号或者格式极其糟糕的情况)
        val first = text.indexOf('{')
        val last = text.lastIndexOf('}')
        if (first != -1 && last > first) {
            return text.substring(first, last + 1)
        }
        return ""
    }

}
