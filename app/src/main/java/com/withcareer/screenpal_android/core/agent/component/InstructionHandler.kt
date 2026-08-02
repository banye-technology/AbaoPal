package com.withcareer.screenpal_android.core.agent.component

import android.content.Context
import android.util.Log
import com.withcareer.screenpal_android.config.AppConfig
import com.withcareer.screenpal_android.config.LlmModel
import com.withcareer.screenpal_android.core.llm.ModelClient
import com.withcareer.screenpal_android.core.tts.TtsManager
import com.withcareer.screenpal_android.core.voice.VoiceController
import com.withcareer.screenpal_android.data.repository.InstructionRepository
import com.withcareer.screenpal_android.data.preference.PreferencesRepository
import com.withcareer.screenpal_android.data.room.InstructionSetEntity
import com.withcareer.screenpal_android.core.llm.ChatMessage
import com.withcareer.screenpal_android.core.llm.ContentItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles instruction set search, proposal, and confirmation intent recognition.
 */
class InstructionHandler(
    private val context: Context,
    private val instructionRepository: InstructionRepository,
    private val preferencesRepository: PreferencesRepository,
    private val ttsManager: TtsManager,
    private val scope: CoroutineScope
) {
    // Pair<OriginalInput, InstructionId>
    var pendingConfirmation: Pair<String, String>? = null
        private set

    /**
     * Searches for matching instructions and sets up pending confirmation if found.
     * Returns the matching instruction set if found, null otherwise.
     */
    suspend fun searchAndPropose(userInput: String): InstructionSetEntity? {
        val apiKey = preferencesRepository.getAsrApiKeySync().orEmpty()
        if (apiKey.isBlank()) return null

        val infraClient = ModelClient(AppConfig.BAILIAN_BASE_URL)

        try {
            instructionRepository.ensureEmbeddings(infraClient, apiKey)
        } catch (e: Exception) {
            Log.w("InstructionHandler", "Failed to sync embeddings", e)
        }

        val matches = instructionRepository.searchInstructions(userInput, infraClient, apiKey, threshold = 0.92)
        if (matches.isNotEmpty()) {
            val topMatch = matches.first()
            val instruction = instructionRepository.getInstructionSet(topMatch.id)
            if (instruction != null) {
                return instruction
            }
        }
        return null
    }

    /**
     * Initiates voice confirmation interaction.
     */
    fun startVoiceConfirmation(title: String) {
        ttsManager.speak("发现相似指令集：${title}。是否直接执行？") {
            scope.launch(Dispatchers.Main) {
                VoiceController.getInstance(context).startListening()
            }
        }
    }

    /**
     * Stops any active voice confirmation interaction.
     */
    fun stopVoiceConfirmation() {
        ttsManager.stop()
        VoiceController.getInstance(context).stopListening()
    }

    /**
     * Clears the pending confirmation state.
     */
    fun clearPending() {
        pendingConfirmation = null
    }

    /**
     * Uses LLM to determine if the user confirmed execution.
     */
    suspend fun recognizeConfirmationIntent(userResponse: String): Boolean {
        val apiKey = preferencesRepository.getAsrApiKeySync().orEmpty()
        if (apiKey.isBlank()) return false

        val client = ModelClient(AppConfig.BAILIAN_BASE_URL)
        val prompt = """
            User is asked: "Found a similar instruction set, do you want to execute it?".
            User replied: "$userResponse".

            Analyze the user's intent.
            If the user agrees, accepts, or says yes, return "YES".
            If the user refuses, rejects, or says no, return "NO".

            Return ONLY "YES" or "NO".
        """.trimIndent()

        try {
            val response = client.chatCompletionSimple(
                listOf(
                    ChatMessage(
                        role = "user",
                        content = listOf(ContentItem(type = "text", text = prompt))
                    )
                ),
                LlmModel.QWEN_FLASH.value,
                apiKey
            )
            return response.trim().uppercase().contains("YES")
        } catch (e: Exception) {
            Log.e("InstructionHandler", "Intent recognition failed", e)
            return false
        }
    }
}
