package com.withcareer.screenpal_android.core.llm

import com.google.gson.annotations.SerializedName

/**
 * LLM 请求/响应数据结构
 * 与 OpenAI 兼容协议对齐（支持百炼、火山引擎、硅基流动等第三方服务）
 */
data class ChatMessage(
    val role: String,
    val content: List<ContentItem>
)

data class ContentItem(
    val type: String,
    val text: String? = null,
    @SerializedName("image_url")
    val imageUrl: ImageUrl? = null
)

data class ImageUrl(
    val url: String
)

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @SerializedName("max_tokens")
    val maxTokens: Int = 3000,
    val temperature: Double = 0.0,
    @SerializedName("top_p")
    val topP: Double = 0.85,
    @SerializedName("frequency_penalty")
    val frequencyPenalty: Double = 0.2,
    val stream: Boolean = false,
    @SerializedName("extra_body")
    val extraBody: Map<String, Any>? = null,
    // Add tools parameter for function calling / web search
    val tools: List<Tool>? = null,
    @SerializedName("tool_choice")
    val toolChoice: Any? = null,
    @SerializedName("enable_search")
    val enableSearch: Boolean? = null,
    @SerializedName("enable_thinking")
    val enableThinking: Boolean? = null,
    val thinking: ThinkingConfig? = null
)

data class ThinkingConfig(
    val type: String
)

data class Tool(
    val type: String = "function",
    val function: ToolFunction
)

data class ToolFunction(
    val name: String,
    val description: String,
    val parameters: Any? = null
)

data class ChatResponse(
    val id: String,
    val choices: List<Choice>,
    val usage: Usage?
)

data class Choice(
    val index: Int,
    val message: ResponseMessage,
    @SerializedName("finish_reason")
    val finishReason: String?
)

data class ResponseMessage(
    val role: String,
    val content: String
)

data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,
    @SerializedName("completion_tokens")
    val completionTokens: Int,
    @SerializedName("total_tokens")
    val totalTokens: Int
)

/**
 * Embedding 请求
 */
data class EmbeddingRequest(
    val model: String,
    val input: List<String>
)

/**
 * Embedding 响应
 */
data class EmbeddingResponse(
    val data: List<EmbeddingData>
)

data class EmbeddingData(
    val embedding: List<Double>,
    val index: Int,
    val `object`: String = "embedding"
)

/**
 * 模型列表响应（OpenAI 兼容协议 GET /models）
 */
data class ModelsResponse(
    val data: List<ModelInfo> = emptyList()
)

data class ModelInfo(
    val id: String,
    @SerializedName("owned_by")
    val ownedBy: String? = null
)
