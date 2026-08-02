package com.withcareer.screenpal_android.core.llm

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * LLM API 接口定义
 * 用于与大模型后端进行通信
 */
interface LlmApi {
    @POST("chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: ChatRequest
    ): Response<ChatResponse>

    @POST("embeddings")
    suspend fun createEmbeddings(
        @Header("Authorization") authorization: String,
        @Body request: EmbeddingRequest
    ): Response<EmbeddingResponse>

    /**
     * 拉取可用模型列表（OpenAI 兼容协议）
     */
    @GET("models")
    suspend fun listModels(
        @Header("Authorization") authorization: String
    ): Response<ModelsResponse>
}
