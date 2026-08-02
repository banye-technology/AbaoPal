package com.withcareer.screenpal_android.ui_v2.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.withcareer.screenpal_android.ScreenPalApplication
import com.withcareer.screenpal_android.core.model.ChatUiState
import com.withcareer.screenpal_android.data.preference.PreferencesRepository
import com.withcareer.screenpal_android.data.preference.Task
import com.withcareer.screenpal_android.core.model.MessageRole
import com.withcareer.screenpal_android.core.model.ChatMessage
import com.withcareer.screenpal_android.core.voice.VoiceController
import com.withcareer.screenpal_android.util.OperationGuard
import com.withcareer.screenpal_android.data.room.InstructionSetEntity
import com.google.gson.JsonParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class V2ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isSending: Boolean = false,
    val inputText: String = "",
    val isListening: Boolean = false,
    val isProcessingVoice: Boolean = false,
    val voiceError: String? = null,
    val voiceRmsDb: Float = 0f,
    val isTaskRunning: Boolean = false,
    val thinkingContent: String = "",
    val error: String? = null,
    val tasks: List<Task> = emptyList(),
    val isTtsEnabled: Boolean = false,
    val quickCommands: List<InstructionSetEntity> = emptyList(),
    val viewingTask: Task? = null,
    val lastCompletedTaskId: String? = null
)

class V2ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ScreenPalApplication
    private val agentRuntime = app.agentRuntime
    private val taskRepository = app.taskRepository
    private val instructionRepository = app.instructionRepository
    private val preferencesRepository = PreferencesRepository(application)
    private val operationGuard = OperationGuard(application)
    private val voiceController = VoiceController.getInstance(application)

    private val inputText = MutableStateFlow("")
    private val isListening = MutableStateFlow(false)
    private val isProcessingVoice = MutableStateFlow(false)
    private val voiceError = MutableStateFlow<String?>(null)
    private val voiceRmsDb = MutableStateFlow(0f)
    private val viewingTask = MutableStateFlow<Task?>(null)

    private var autoSubmitJob: Job? = null
    private var voiceBaseText: String = ""
    private var isVoiceInterrupted: Boolean = false

    private data class BaseInputs(
        val chatState: ChatUiState,
        val tasks: List<Task>,
        val ttsEnabled: Boolean,
        val quickCommands: List<InstructionSetEntity>,
        val viewingTask: Task?
    )

    private data class VoiceInputs(
        val listening: Boolean,
        val processingVoice: Boolean,
        val voiceErr: String?,
        val rmsDb: Float
    )

    private val baseState = combine(
        agentRuntime.uiState,
        taskRepository.tasks,
        preferencesRepository.ttsEnabled,
        instructionRepository.allInstructionSets,
        viewingTask
    ) { chatState, tasks, ttsEnabled, quickCommands, viewingTask ->
        BaseInputs(
            chatState = chatState,
            tasks = tasks,
            ttsEnabled = ttsEnabled,
            quickCommands = quickCommands,
            viewingTask = viewingTask
        )
    }.combine(
        combine(isListening, isProcessingVoice, voiceError, voiceRmsDb) { listening, processingVoice, voiceErr, rmsDb ->
            VoiceInputs(
                listening = listening,
                processingVoice = processingVoice,
                voiceErr = voiceErr,
                rmsDb = rmsDb
            )
        }
    ) { base, voice ->
        val cleanThinking = extractThinking(base.chatState.messages, base.chatState.thinkingContent)

        V2ChatUiState(
            messages = base.chatState.messages,
            isSending = base.chatState.isLoading,
            isListening = voice.listening,
            isProcessingVoice = voice.processingVoice,
            voiceError = voice.voiceErr,
            voiceRmsDb = voice.rmsDb,
            isTaskRunning = base.chatState.isLoading,
            thinkingContent = cleanThinking,
            error = base.chatState.error,
            tasks = base.tasks,
            isTtsEnabled = base.ttsEnabled,
            quickCommands = base.quickCommands,
            viewingTask = base.viewingTask,
            lastCompletedTaskId = base.chatState.lastCompletedTaskId
        )
    }

    val uiState: StateFlow<V2ChatUiState> = baseState
        .combine(inputText) { state, input ->
            state.copy(inputText = input)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), V2ChatUiState())

    init {
        viewModelScope.launch {
            voiceController.uiState.collect { voiceState ->
                val wasBusy = isListening.value || isProcessingVoice.value

                if (voiceState.isListening || voiceState.isProcessing) {
                    autoSubmitJob?.cancel()
                }

                if (voiceState.finalText == null && inputText.value.isNotBlank()) {
                    autoSubmitJob?.cancel()
                    autoSubmitJob = null
                }

                isListening.value = voiceState.isListening
                isProcessingVoice.value = voiceState.isProcessing
                voiceError.value = voiceState.error
                voiceRmsDb.value = voiceState.rmsDb

                if (!voiceState.finalText.isNullOrEmpty()) {
                    val separator = if (voiceBaseText.isNotEmpty() && !voiceBaseText.endsWith(" ")) " " else ""
                    inputText.value = "$voiceBaseText$separator${voiceState.finalText}"
                }

                val isBusyNow = voiceState.isListening || voiceState.isProcessing
                if (wasBusy && !isBusyNow) {
                    val currentText = inputText.value
                    if (currentText.isNotBlank() && !isVoiceInterrupted) {
                        if (autoSubmitJob?.isActive != true) {
                            autoSubmitJob = launch {
                                delay(300)
                                val textToSubmit = inputText.value.trim()
                                if (textToSubmit.isNotBlank()) {
                                    if (operationGuard.check(requireApiKey = true)) {
                                        inputText.value = ""
                                        agentRuntime.startTask(textToSubmit)
                                    }
                                }
                            }
                        }
                    }
                    isVoiceInterrupted = false
                }
            }
        }
    }

    fun updateInputText(text: String) {
        autoSubmitJob?.cancel()
        inputText.value = text
    }

    fun newChat() {
        viewingTask.value = null
        agentRuntime.stopTask()
        agentRuntime.startNewTopic()
        inputText.value = ""
    }

    fun replayTask(task: Task) {
        viewModelScope.launch {
            try {
                viewingTask.value = task
                // 清空输入框
                inputText.value = ""

                // 加载历史任务记录（只显示，不执行）
                agentRuntime.loadHistoryTask(task)
            } catch (e: Exception) {
                android.util.Log.e("V2ChatViewModel", "Error loading history task", e)
            }
        }
    }

    fun deleteTasks(taskIds: Set<String>) {
        taskRepository.deleteTasks(taskIds)
    }

    fun updateTaskTitle(taskId: String, title: String) {
        taskRepository.updateTaskPrompt(taskId, title)
    }

    fun send() {
        viewModelScope.launch {
            if (!operationGuard.check(
                requireApiKey = true,
                requireAccessibility = true
            )) return@launch

            val text = inputText.value.trim()
            if (text.isBlank()) return@launch

            viewingTask.value = null

            // 强制清理之前可能卡住的状态
            val currentState = agentRuntime.uiState.value
            if (currentState.isLoading && currentState.messages.isNotEmpty()) {
                val lastMessage = currentState.messages.lastOrNull()
                // 如果最后一条消息已经超过30秒，强制停止任务
                if (lastMessage != null) {
                    try {
                        val messageTime = lastMessage.id.toLongOrNull() ?: 0
                        val now = System.currentTimeMillis()
                        if (now - messageTime > 30000) {
                            android.util.Log.w("V2ChatViewModel", "Force stopping stuck task before starting new one")
                            agentRuntime.stopTask()
                            delay(100) // 等待状态更新
                        }
                    } catch (e: Exception) {
                        // 忽略时间解析错误
                    }
                }
            }

            inputText.value = ""
            agentRuntime.startTask(text)
        }
    }

    fun startVoiceInput() {
        viewModelScope.launch {
            if (!operationGuard.check(requireApiKey = true, requireRecordAudio = true, requireAccessibility = true)) {
                voiceController.restartWakeup()
                return@launch
            }
            autoSubmitJob?.cancel()
            voiceBaseText = inputText.value
            isVoiceInterrupted = false
            voiceController.startListening()
        }
    }

    fun stopVoiceInput() {
        voiceController.stopListening()
    }

    fun interruptVoiceInput() {
        isVoiceInterrupted = true
        voiceController.stopListening()
    }

    fun stopTask() {
        agentRuntime.stopTask()
    }

    fun handleInteractiveAction(actionId: String, payload: String?) {
        agentRuntime.handleUserInteraction(actionId, payload)
    }

    fun runQuickCommand(instructionSetId: String) {
        agentRuntime.runInstruction(instructionSetId)
    }

    fun reExecuteTask(prompt: String): Boolean {
        val trimmed = prompt.trim()
        if (trimmed.isBlank()) return false
        viewModelScope.launch {
            if (!operationGuard.check(
                    requireApiKey = true,
                    requireAccessibility = true
                )
            ) return@launch

            if (uiState.value.isTaskRunning) {
                agentRuntime.stopTask()
                delay(120)
            }

            val currentViewingTaskId = viewingTask.value?.id ?: agentRuntime.getCurrentTaskId()
            val currentTaskPrompt = viewingTask.value?.prompt ?: uiState.value.messages.firstOrNull { it.role == MessageRole.USER }?.content

            if (currentViewingTaskId != null && currentTaskPrompt?.trim() == trimmed) {
                // 如果当前正在查看的任务（历史或当前活跃）提示词一致，则在原任务上重新执行
                agentRuntime.restartTask(currentViewingTaskId)
            } else {
                // 否则创建新任务
                inputText.value = ""
                agentRuntime.startTask(trimmed)
            }
        }
        return true
    }

    fun toggleTtsEnabled() {
        viewModelScope.launch {
            preferencesRepository.saveTtsEnabled(!uiState.value.isTtsEnabled)
        }
    }

    private fun extractThinking(messages: List<ChatMessage>, thinkingContent: String): String {
        val lastAssistant = messages.lastOrNull { it.role == MessageRole.ASSISTANT }
        val thinking = thinkingContent.ifBlank {
            lastAssistant?.thinking?.trim().orEmpty()
        }
        if (thinking.startsWith("{") && thinking.endsWith("}")) {
            return try {
                val json = JsonParser.parseString(thinking).asJsonObject
                json.get("description")?.asString
                    ?: json.get("thought")?.asString
                    ?: json.get("thinking")?.asString
                    ?: thinking
            } catch (_: Exception) {
                thinking
            }
        }
        return thinking
    }
}
