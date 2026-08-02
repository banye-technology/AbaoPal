package com.withcareer.screenpal_android.data.repository

import android.util.Log
import com.withcareer.screenpal_android.config.AppConfig
import com.withcareer.screenpal_android.core.llm.ModelClient
import com.withcareer.screenpal_android.data.room.InstructionDao
import com.withcareer.screenpal_android.data.room.InstructionSetEntity
import com.withcareer.screenpal_android.data.room.InstructionStepEntity
import com.withcareer.screenpal_android.data.sqllite.InstructionVectorStore
import com.withcareer.screenpal_android.data.sqllite.SearchResult
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID

class InstructionRepository(
    private val instructionDao: InstructionDao,
    private val vectorStore: InstructionVectorStore
) {

    val allInstructionSets: Flow<List<InstructionSetEntity>> = instructionDao.getAllSets()

    suspend fun getInstructionSet(id: String): InstructionSetEntity? {
        return instructionDao.getSetById(id)
    }

    suspend fun getInstructionSetByTitle(title: String): InstructionSetEntity? {
        return instructionDao.getSetByTitle(title) ?: instructionDao.findSetByTitleKeyword(title)
    }

    suspend fun resolveInstructionSet(identifier: String): InstructionSetEntity? {
        if (identifier.isBlank()) return null
        return getInstructionSet(identifier) ?: getInstructionSetByTitle(identifier)
    }

    suspend fun getSteps(setId: String): List<InstructionStepEntity> {
        return instructionDao.getStepsBySetId(setId)
    }

    suspend fun createInstructionSet(
        title: String,
        description: String,
        authorId: String,
        authorName: String,
        steps: List<InstructionStepEntity>,
        modelClient: ModelClient? = null,
        apiKey: String? = null
    ) {
        val newSet = InstructionSetEntity(
            title = title,
            description = description,
            authorId = authorId,
            authorName = authorName,
            isPublished = false
        )
        // Update set ID in steps to match the new set
        val linkedSteps = steps.mapIndexed { index, step ->
            step.copy(setId = newSet.id, orderIndex = index)
        }
        instructionDao.createInstructionSet(newSet, linkedSteps)

        // 如果提供了 Client，立即生成向量
        if (modelClient != null && !apiKey.isNullOrBlank()) {
            indexInstructionSet(newSet.id, modelClient, apiKey)
        }
    }

    suspend fun saveInstructionSet(
        set: InstructionSetEntity,
        steps: List<InstructionStepEntity>,
        modelClient: ModelClient? = null,
        apiKey: String? = null
    ) {
        instructionDao.createInstructionSet(set, steps)
        vectorStore.deleteEmbedding(set.id)
        if (modelClient != null && !apiKey.isNullOrBlank()) {
            indexInstructionSet(set.id, modelClient, apiKey)
        }
    }

    suspend fun deleteInstructionSet(id: String) {
        instructionDao.deleteSet(id)
        vectorStore.deleteEmbedding(id)
    }

    /**
     * 为指定指令集生成并存储向量
     */
    suspend fun indexInstructionSet(setId: String, modelClient: ModelClient, apiKey: String) {
        try {
            val set = instructionDao.getSetById(setId) ?: return
            val textToEmbed = "${set.title}\n${set.description}"
            Log.d("InstructionRepo", "Indexing instruction set")

            val embedding = modelClient.createEmbedding(
                textToEmbed,
                AppConfig.DEFAULT_EMBEDDING_MODEL,
                apiKey
            )

            if (embedding.isNotEmpty()) {
                Log.d("InstructionRepo", "Generated instruction embedding: size=${embedding.size}")
                vectorStore.saveEmbedding(setId, embedding, set.title)
            } else {
                Log.w("InstructionRepo", "Instruction embedding result was empty")
            }
        } catch (e: Exception) {
            Log.e("InstructionRepo", "Error indexing instruction set", e)
        }
    }

    /**
     * 确保所有指令集都有 Embedding
     */
    suspend fun ensureEmbeddings(modelClient: ModelClient, apiKey: String) {
        val sets = allInstructionSets.first()
        val storedIds = vectorStore.getAllIds().toSet()

        for (set in sets) {
            if (set.id !in storedIds) {
                try {
                    val textToEmbed = "${set.title}\n${set.description}"
                    Log.d("InstructionRepo", "Generating instruction embedding")

                    val embedding = modelClient.createEmbedding(
                        textToEmbed,
                        AppConfig.DEFAULT_EMBEDDING_MODEL,
                        apiKey
                    )

                    if (embedding.isNotEmpty()) {
                        Log.d("InstructionRepo", "Generated instruction embedding: size=${embedding.size}")
                        vectorStore.saveEmbedding(set.id, embedding, set.title)
                    } else {
                        Log.w("InstructionRepo", "Instruction embedding result was empty")
                    }
                } catch (e: Exception) {
                    Log.e("InstructionRepo", "Failed to generate instruction embedding", e)
                }
            }
        }
    }

    /**
     * 搜索相似指令集
     */
    suspend fun searchInstructions(
        query: String,
        modelClient: ModelClient,
        apiKey: String,
        limit: Int = 3,
        threshold: Double = 0.85
    ): List<SearchResult> {
        try {
            val embedding = modelClient.createEmbedding(
                query,
                AppConfig.DEFAULT_EMBEDDING_MODEL,
                apiKey
            ) ?: return emptyList()

            return vectorStore.search(embedding, limit, threshold)
        } catch (e: Exception) {
            Log.e("InstructionRepo", "Search failed", e)
            return emptyList()
        }
    }
}
