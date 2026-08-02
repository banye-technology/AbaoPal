package com.withcareer.screenpal_android.core.agent

import android.app.Application
import android.util.Log
import com.withcareer.screenpal_android.R
import com.withcareer.screenpal_android.ScreenPalApplication
import com.withcareer.screenpal_android.config.AppConfig
import com.withcareer.screenpal_android.config.AiProvider
import com.withcareer.screenpal_android.config.LlmModel
import com.withcareer.screenpal_android.core.action.ActionExecutor
import com.withcareer.screenpal_android.core.agent.component.*
import com.withcareer.screenpal_android.core.agent.framework.*
import com.withcareer.screenpal_android.core.agent.runner.InstructionRunner
import com.withcareer.screenpal_android.core.agent.runner.RunnerState
import com.withcareer.screenpal_android.util.ActionUtils
import com.google.gson.JsonParser
import com.withcareer.screenpal_android.core.llm.ModelClient
import com.withcareer.screenpal_android.core.llm.ModelConfigResolver
import com.withcareer.screenpal_android.core.skill.SkillRegistry
import com.withcareer.screenpal_android.data.preference.PreferencesRepository
import com.withcareer.screenpal_android.data.preference.Subtask
import com.withcareer.screenpal_android.data.preference.TaskState
import com.withcareer.screenpal_android.data.preference.TaskStep
import com.withcareer.screenpal_android.data.preference.TaskStatus
import com.withcareer.screenpal_android.data.model.SkillInstructionSet
import com.withcareer.screenpal_android.data.room.InstructionStepEntity
import com.withcareer.screenpal_android.core.mapper.TaskToInstructionMapper
import com.withcareer.screenpal_android.service.ScreenPalAccessibilityService
import com.withcareer.screenpal_android.core.tts.TtsManager
import com.withcareer.screenpal_android.core.model.ChatUiState
import com.withcareer.screenpal_android.core.model.InteractiveAction
import com.withcareer.screenpal_android.core.model.ChatMessage as UiChatMessage
import com.withcareer.screenpal_android.core.model.MessageRole
import com.withcareer.screenpal_android.util.ActionJsonUtils
import com.withcareer.screenpal_android.util.DeviceUtils
import com.withcareer.screenpal_android.util.ToastUtils
import android.widget.Toast
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull

/**
 * AgentRuntime 负责管理智能体的核心执行循环、状态管理和资源协调。
 * 采用事件驱动架构 (Event-Driven Architecture)。
 * 重构后将职责拆分到组件：
 * - TaskManager
 * - AgentOrchestrator
 * - InstructionHandler
 *
 */
class AgentRuntime(private val application: Application) {

    private val preferencesRepository: PreferencesRepository = PreferencesRepository(application)
    private val taskRepository = (application as ScreenPalApplication).taskRepository
    private val instructionRepository = (application as ScreenPalApplication).instructionRepository
    private val skillRegistry: SkillRegistry = (application as ScreenPalApplication).skillRegistry
    private val ttsManager = TtsManager.getInstance(application)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var actionExecutor: ActionExecutor? = null
    private var taskJob: Job? = null
    private var currentAppJob: Job? = null
    private var busCollectionJob: Job? = null
    private var instructionRunner: InstructionRunner? = null

    // Components
    private val taskManager = TaskManager(taskRepository)
    private val agentOrchestrator = AgentOrchestrator(application, preferencesRepository, skillRegistry, scope)
    private val instructionHandler = InstructionHandler(application, instructionRepository, preferencesRepository, ttsManager, scope)

    init {
        // skillRegistry 已在 ScreenPalApplication 中初始化，无需重复
    }

    /**
     * 绑定无障碍服务。
     * @return 是否成功绑定，false 表示配置获取失败
     */
    suspend fun attachAccessibilityService(service: ScreenPalAccessibilityService): Boolean {
        // 解析当前生效的模型配置（自定义配置优先，其次默认配置）
        val config = ModelConfigResolver.resolveExecutor(application)
        if (config == null) {
            Log.e("AgentRuntime", "No API key available, cannot bind accessibility service")
            _uiState.value = _uiState.value.copy(
                error = application.getString(R.string.configure_api_key_first)
            )
            return false
        }
        val modelClient = ModelClient(config.baseUrl)
        val apiKey = config.apiKey
        val executorModel = config.modelName
        Log.d("AgentRuntime", "Using configured executor model")

        this.actionExecutor = ActionExecutor(
            service,
            modelClient,
            { apiKey },
            executorModel
        )

        // Check errors
        val accessibilityError = application.getString(R.string.accessibility_service_not_enabled)
        val accessibilityNotReadyError = application.getString(R.string.accessibility_service_not_ready)
        if (_uiState.value.error == accessibilityError || _uiState.value.error == accessibilityNotReadyError) {
            _uiState.value = _uiState.value.copy(error = null)
        }

        currentAppJob?.cancel()
        currentAppJob = scope.launch {
            service.currentApp.collectLatest { app ->
                _uiState.value = _uiState.value.copy(currentApp = app)
                agentOrchestrator.agentBus?.tryEmit(CurrentAppUpdateMessage(app ?: ""))
            }
        }

        return true
    }

    fun detachAccessibilityService() {
        currentAppJob?.cancel()
        currentAppJob = null
        actionExecutor = null
        _uiState.value = _uiState.value.copy(currentApp = null)
        stopTask()
    }

    /** 当前前台应用包名，供 RunInstructionSet 等按应用解析技能用 */
    fun getCurrentApp(): String? = _uiState.value.currentApp

    /** 供 RunInstructionSetHandler 等同步执行指令步骤用 */
    fun getActionExecutor(): ActionExecutor? = actionExecutor

    /** 获取当前执行的任务ID */
    fun getCurrentTaskId(): String? = taskManager.currentTaskId

    /**
     * 重新执行一个现有的任务，不创建新记录，重新规划并执行。
     */
    suspend fun restartTask(taskId: String) {
        val task = taskManager.getTask(taskId) ?: return
        val userInput = task.prompt

        if (_uiState.value.isLoading) {
            handleUserFeedback(userInput)
            return
        }

        val accessibilityService = ScreenPalAccessibilityService.getInstance()
        if (accessibilityService == null) {
            Log.e("AgentRuntime", "Accessibility service not available")
            return
        }
        if (actionExecutor == null) {
            if (!attachAccessibilityService(accessibilityService)) {
                return
            }
        }

        // 确保清理之前的任务
        taskJob?.cancel()
        taskJob = null
        busCollectionJob?.cancel()
        busCollectionJob = null
        agentOrchestrator.cleanupAgents()

        // 确保悬浮助手服务运行（用于显示执行悬浮框）
        try {
            if (android.provider.Settings.canDrawOverlays(application)) {
                com.withcareer.screenpal_android.overlay.FloatingAssistantService.start(application)
            }
        } catch (e: Exception) {
            Log.w("AgentRuntime", "Failed to start FloatingAssistantService", e)
        }

        // 复用 ID 并清空步骤
        taskManager.setCurrentTask(taskId)

        // 准备 UI：重置消息列表，只保留初始的用户提示词
        _uiState.value = _uiState.value.copy(
            messages = listOf(UiChatMessage(
                id = "${task.createdAt}",
                role = MessageRole.USER,
                content = userInput,
                timestamp = task.createdAt
            )),
            taskCompletedMessage = null,
            lastCompletedTaskId = null,
            error = null,
            isLoading = true,
            executionSteps = emptyList(),
            thinkingContent = "重新规划并执行任务..."
        )

        Log.d("AgentRuntime", "restartTask: Calling startTaskInternal to re-plan and execute")
        // 重新规划并执行（不复用原有规划）
        startTaskInternal(userInput, skipPlanning = false, initialTaskState = null)
    }

    /**
     * 开始一个新的任务 (Event-Driven Mode)。
     */
    suspend fun startTask(userInput: String, skipPlanning: Boolean = false, initialTaskState: TaskState? = null) {
        if (userInput.isBlank()) {
            Log.w("AgentRuntime", "startTask: input is blank")
            return
        }

        if (_uiState.value.isLoading) {
            handleUserFeedback(userInput)
            return
        }

        val accessibilityService = ScreenPalAccessibilityService.getInstance()
        if (accessibilityService == null) {
            Log.e("AgentRuntime", "Accessibility service not available")
            val errorMsg = application.getString(R.string.accessibility_service_not_enabled)
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + UiChatMessage(
                    id = System.currentTimeMillis().toString(),
                    role = MessageRole.USER,
                    content = userInput
                ),
                error = errorMsg,
                isLoading = false,
                thinkingContent = ""
            )
            ToastUtils.show(application, errorMsg, Toast.LENGTH_LONG)
            return
        }
        if (actionExecutor == null) {
            if (!attachAccessibilityService(accessibilityService)) {
                return
            }
        }

        // 确保清理之前的任务
        taskJob?.cancel()
        taskJob = null
        busCollectionJob?.cancel()
        busCollectionJob = null
        agentOrchestrator.cleanupAgents()

        // 强制重置状态，防止卡住
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            error = null,
            taskCompletedMessage = null,
            lastCompletedTaskId = null,
            thinkingContent = ""
        )

        // 1. Check pending instruction confirmation
        val pending = instructionHandler.pendingConfirmation
        Log.d("AgentRuntime", "startTask: pending instruction present=${pending != null}")
        if (pending != null) {
            val (originalInput, instructionId) = pending
            instructionHandler.stopVoiceConfirmation()
            instructionHandler.clearPending()

            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + UiChatMessage(
                    id = System.currentTimeMillis().toString(),
                    role = MessageRole.USER,
                    content = userInput
                ),
                thinkingContent = "正在确认您的意图..."
            )

            val isConfirmed = instructionHandler.recognizeConfirmationIntent(userInput)
            if (isConfirmed) {
                _uiState.value = _uiState.value.copy(thinkingContent = "确认执行指令集")
                runInstruction(instructionId)
                return
            } else {
                _uiState.value = _uiState.value.copy(thinkingContent = "取消指令集，继续规划...")
                // Fallback to original input
                startTaskInternal(originalInput, skipPlanning, initialTaskState, skipInstructionCheck = true)
                return
            }
        }

        val effectiveUserInput = userInput

        // Standard Start Flow
        Log.d("AgentRuntime", "startTask: Starting standard flow, updating UI state...")
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + UiChatMessage(
                id = System.currentTimeMillis().toString(),
                role = MessageRole.USER,
                content = userInput
            ),
            taskCompletedMessage = null,
            lastCompletedTaskId = null,
            error = null,
            isLoading = true,
            executionSteps = emptyList(),
            thinkingContent = if (skipPlanning) "准备执行..." else "正在分析指令..."
        )

        taskManager.createTask(effectiveUserInput)

        startTaskInternal(effectiveUserInput, skipPlanning, initialTaskState)
    }

    private suspend fun startTaskInternal(userInput: String, skipPlanning: Boolean, initialTaskState: TaskState?, skipInstructionCheck: Boolean = false) {
        _uiState.value = _uiState.value.copy(isLoading = true)

        // 确保悬浮助手服务运行（用于显示执行悬浮框）
        try {
            if (android.provider.Settings.canDrawOverlays(application)) {
                com.withcareer.screenpal_android.overlay.FloatingAssistantService.start(application)
            }
        } catch (e: Exception) {
            Log.w("AgentRuntime", "Failed to start FloatingAssistantService", e)
        }

        val job = scope.launch(Dispatchers.Default) {
            try {
                val startMs = android.os.SystemClock.elapsedRealtime()

                // 添加超时保护（5分钟）
                val timeoutJob = launch {
                    delay(300000) // 5分钟超时
                    if (_uiState.value.isLoading) {
                        Log.w("AgentRuntime", "Task timeout after 5 minutes, forcing stop")
                        withContext(Dispatchers.Main.immediate) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = "任务执行超时（5分钟），已自动停止",
                                thinkingContent = ""
                            )
                        }
                        taskJob?.cancel()
                        busCollectionJob?.cancel()
                        agentOrchestrator.cleanupAgents()
                    }
                }

                try {
                if (initialTaskState == null && !skipPlanning) {
                    // 标准任务流程：直接进入本地指令检索 / 智能体规划
                }

                // 3. Local Instruction Match
                if (initialTaskState == null && !skipInstructionCheck) {
                    _uiState.value = _uiState.value.copy(thinkingContent = "正在检索本地指令...")
                    val instructionStart = android.os.SystemClock.elapsedRealtime()
                    val instruction = instructionHandler.searchAndPropose(userInput)
                    Log.d("AgentRuntime", "Timing instruction search: ${android.os.SystemClock.elapsedRealtime() - instructionStart}ms")

                    if (instruction != null) {
                        instructionHandler.clearPending()
                        _uiState.value = _uiState.value.copy(thinkingContent = "匹配到本地指令，开始执行…")
                        runInstruction(instruction.id)
                        return@launch
                    } else {
                        Log.i("AgentRuntime", "No instructions found matching threshold")
                    }
                }

                // 4. Agent Planning
                _uiState.value = _uiState.value.copy(thinkingContent = "启动智能体规划...")

                val orchestratorStart = android.os.SystemClock.elapsedRealtime()
                agentOrchestrator.initializeAgents(userInput, skipPlanning)
                Log.d("AgentRuntime", "Timing orchestrator init: ${android.os.SystemClock.elapsedRealtime() - orchestratorStart}ms")

                // Subscribe to Bus
                val bus = agentOrchestrator.agentBus
                if (bus != null) {
                    val busStart = android.os.SystemClock.elapsedRealtime()
                    withContext(Dispatchers.Main.immediate) {
                        busCollectionJob?.cancel()
                        busCollectionJob = scope.launch(Dispatchers.Default) {
                            try {
                                bus.events.collect { message ->
                                    try {
                                        withContext(Dispatchers.Main.immediate) {
                                            handleUiUpdates(message)
                                        }
                                    } catch (e: Exception) {
                                        Log.e("AgentRuntime", "Error handling UI update for message type=${message::class.simpleName}")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("AgentRuntime", "Bus collection failed", e)
                            }
                        }
                    }

                    if (initialTaskState != null) {
                        bus.emit(StartExecutionMessage(initialTaskState))
                    } else {
                        bus.emit(TaskStartedMessage(userInput, taskManager.currentTaskId))
                    }
                    Log.d("AgentRuntime", "Timing bus setup+emit: ${android.os.SystemClock.elapsedRealtime() - busStart}ms")
                }
                    Log.d("AgentRuntime", "Timing startTaskInternal total: ${android.os.SystemClock.elapsedRealtime() - startMs}ms")

                } finally {
                    // 取消超时Job
                    timeoutJob.cancel()
                }

            } catch (_: CancellationException) {
                Log.i("AgentRuntime", "Task cancelled")
                withContext(Dispatchers.Main.immediate) {
                    if (_uiState.value.isLoading) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            thinkingContent = "",
                            error = null
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("AgentRuntime", "Error starting task", e)
                withContext(Dispatchers.Main.immediate) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message,
                        thinkingContent = ""
                    )
                }
            }
        }
        taskJob = job
        job.join()
    }

    private fun appendAssistantMessage(content: String, thinking: String? = null) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + UiChatMessage(
                id = System.currentTimeMillis().toString(),
                role = MessageRole.ASSISTANT,
                content = content,
                thinking = thinking
            )
        )
    }

    private suspend fun speakIfEnabled(text: String) {
        val enabled = preferencesRepository.ttsEnabled.firstOrNull() ?: false
        if (enabled) {
            ttsManager.speak(text)
        }
    }

    fun handleUserInteraction(actionId: String, payload: String?) {
        when (actionId) {
            "CONFIRM_INSTRUCTION" -> {
                instructionHandler.stopVoiceConfirmation()

                if (payload != null) {
                    instructionHandler.clearPending()
                    _uiState.value = _uiState.value.copy(thinkingContent = "确认执行指令集")
                    runInstruction(payload)
                }
            }
            "CANCEL_INSTRUCTION" -> {
                instructionHandler.stopVoiceConfirmation()

                val pending = instructionHandler.pendingConfirmation
                if (pending != null) {
                    val (originalInput, _) = pending
                    instructionHandler.clearPending()

                    _uiState.value = _uiState.value.copy(thinkingContent = "取消指令集，继续规划...")

                    scope.launch(Dispatchers.Default) {
                        delay(50)
                        startTaskInternal(originalInput, skipPlanning = false, initialTaskState = null, skipInstructionCheck = true)
                    }
                }
            }
            "ADD_TO_QUICK_COMMAND" -> {
                val taskId = payload?.takeIf { it.isNotBlank() } ?: taskManager.currentTaskId
                if (taskId.isNullOrBlank()) return

                scope.launch(Dispatchers.IO) {
                    val task = taskManager.getTask(taskId) ?: return@launch
                    val app = application as ScreenPalApplication
                    val (set, steps) = TaskToInstructionMapper.map(task)
                    val apiKey = preferencesRepository.getAsrApiKeySync().orEmpty()
                    val client = com.withcareer.screenpal_android.core.llm.ModelClient(com.withcareer.screenpal_android.config.AppConfig.BAILIAN_BASE_URL)
                    app.instructionRepository.saveInstructionSet(set, steps, client, apiKey)
                    withContext(Dispatchers.Main.immediate) {
                        _uiState.value = _uiState.value.copy(lastCompletedTaskId = null)
                        appendAssistantMessage("已添加到快捷指令")
                    }
                }
            }
        }
    }

    private fun handleUserFeedback(feedback: String) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + UiChatMessage(
                id = System.currentTimeMillis().toString(),
                role = MessageRole.USER,
                content = feedback
            )
        )

        val bus = agentOrchestrator.agentBus
        if (bus != null) {
            Log.i("AgentRuntime", "Emitting user feedback")
            val emitted = bus.tryEmit(UserRequestMessage(feedback))
            if (!emitted) {
                Log.w("AgentRuntime", "Emit user feedback failed due to backpressure")
            }
        } else {
            Log.w("AgentRuntime", "Cannot send feedback, agent bus is not active.")
        }
    }

    private suspend fun handleUiUpdates(message: AgentMessage) {
        when (message) {
            is ActionExecutedMessage -> {}
            is StartExecutionMessage -> {
                // 重新执行任务时，在 UI 中展示已有规划
                val taskState = message.taskState
                if (taskState.subtasks.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(thinkingContent = "复用规划执行: ${taskState.summary}")
                    taskManager.updateTaskState(taskState)
                    Log.d("AgentRuntime", "StartExecution with existing plan: subtasks=${taskState.subtasks.size}")

                    // Create a UI message for the plan
                    val planContent = buildString {
                        append("规划如下：\n")
                        taskState.subtasks.forEachIndexed { index, subtask ->
                            append("${index + 1}. ${subtask.description}\n")
                        }
                    }

                    val planMessage = UiChatMessage(
                        id = java.util.UUID.randomUUID().toString(),
                        role = MessageRole.ASSISTANT,
                        content = planContent,
                        thinking = taskState.summary,
                        action = "Generate Plan"
                    )

                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages + planMessage
                    )
                }
            }
            is PlanGeneratedMessage -> {
                _uiState.value = _uiState.value.copy(thinkingContent = "Plan Generated: ${message.taskState.summary}")
                taskManager.updateTaskState(message.taskState)
                Log.d("AgentRuntime", "Plan received: subtasks=${message.taskState.subtasks.size}")

                // Always add planning step to task history
                val planningStep = TaskStep(
                    thought = "Planning Phase (Interactive)",
                    action = "Generate Plan",
                    timestamp = System.currentTimeMillis(),
                    aiPrompt = message.aiPrompt,
                    aiResponse = message.aiResponse
                )
                taskManager.addStepToBeginning(planningStep)

                // Create a UI message for the plan
                val planContent = buildString {
                    append("规划如下：\n")
                    message.taskState.subtasks.forEachIndexed { index, subtask ->
                        append("${index + 1}. ${subtask.description}\n")
                    }
                }

                val planMessage = UiChatMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    role = MessageRole.ASSISTANT,
                    content = planContent,
                    thinking = message.taskState.summary,
                    action = "Generate Plan"
                )

                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + planMessage
                )
            }
            is ExecutionGeneratedMessage -> {
                val thought = message.description
                val action = message.actionJson

                val step = TaskStep(
                    thought = thought,
                    action = action,
                    timestamp = System.currentTimeMillis(),
                    screenshotPath = message.screenshotPath,
                    aiPrompt = message.aiPrompt,
                    aiResponse = message.aiResponse
                )
                taskManager.addStep(step)

                val displayContent = buildString {
                    if (thought.isNotBlank()) {
                        append("💭 思考：$thought\n\n")
                    }
                    if (action.isNotBlank()) {
                        append("🎯 动作：${ActionJsonUtils.formatActionForDisplay(action)}")
                    }
                }
                val assistantMessage = UiChatMessage(
                    id = System.currentTimeMillis().toString(),
                    role = MessageRole.ASSISTANT,
                    content = displayContent,
                    thinking = thought,
                    action = action
                )

                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + assistantMessage,
                    thinkingContent = thought
                )
            }
            is TaskCompletedMessage -> {
                val currentTaskId = taskManager.currentTaskId
                Log.i("AgentRuntime", "Task completed")

                // Construct completion message similar to loadHistoryTask
                val statusContent = buildString {
                    append("📋 任务状态：COMPLETED\n")
                    if (message.summary.isNotBlank()) {
                        append("\n✅ 总结：${message.summary}")
                    }
                    // Try to get note from current task state
                    val currentTask = taskManager.currentTaskId?.let { taskManager.getTask(it) }
                    val note = currentTask?.state?.note
                    if (!note.isNullOrBlank()) {
                        append("\n\n📝 备注：\n$note")
                    }
                }

                val completionMessage = UiChatMessage(
                    id = System.currentTimeMillis().toString(),
                    role = MessageRole.ASSISTANT,
                    content = statusContent
                )

                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + completionMessage,
                    isLoading = false,
                    taskCompletedMessage = message.summary,
                    lastCompletedTaskId = currentTaskId,
                    thinkingContent = "",
                    error = null
                )
                taskManager.completeTask(message.summary)

                agentOrchestrator.cleanupAgents()

                // 确保任务Job被清理
                taskJob?.cancel()
                taskJob = null
                busCollectionJob?.cancel()
                busCollectionJob = null
            }
            is TaskFailedMessage -> {
                Log.e("AgentRuntime", "Task failed")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    thinkingContent = "",
                    error = message.reason
                )
                taskManager.failTask(message.reason)

                agentOrchestrator.cleanupAgents()

                // 确保任务Job被清理
                taskJob?.cancel()
                taskJob = null
                busCollectionJob?.cancel()
                busCollectionJob = null
            }
            is StateUpdatedMessage -> {
                Log.i("Director", "State updated. Deciding next step.")

                if (message.observation != null) {
                    taskManager.updateLastStep { it.copy(observation = message.observation) }
                }

                taskManager.updateTaskState(message.taskState)
            }
            else -> {}
        }
    }


    fun stopTask() {
        taskJob?.cancel()
        taskJob = null
        busCollectionJob?.cancel()
        busCollectionJob = null
        agentOrchestrator.cleanupAgents()
        instructionRunner = null
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            thinkingContent = "",
            lastCompletedTaskId = null,
            error = null // Clear any error to prevent stale voice broadcast
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun startNewTopic() {
        stopTask()
        taskManager.clear()
        _uiState.value = ChatUiState(
            currentApp = _uiState.value.currentApp
        )
    }

    /**
     * 加载历史任务记录（只显示，不执行）
     */
    fun loadHistoryTask(task: com.withcareer.screenpal_android.data.preference.Task) {
        android.util.Log.d("AgentRuntime", "loadHistoryTask: loading stored task")

        // 停止当前任务
        stopTask()

        // 设置当前任务上下文，以便后续重新执行时复用
        taskManager.setCurrentTask(task.id)

        // 创建用户消息
        val userMessage = UiChatMessage(
            id = "${task.createdAt}",
            role = MessageRole.USER,
            content = task.prompt,
            timestamp = task.createdAt
        )

        // 将 TaskStep 转换为 ChatMessage（动作用可读文案展示，避免原始 JSON）
        val stepMessages = task.steps.mapIndexed { index, step ->
            val content = if (step.action == "Generate Plan" && !step.aiResponse.isNullOrBlank()) {
                try {
                    val jsonStr = com.withcareer.screenpal_android.util.ActionJsonUtils.extractJsonFromText(step.aiResponse)
                    val planJson = org.json.JSONObject(jsonStr)
                    val planArray = planJson.optJSONArray("plan")
                    if (planArray != null && planArray.length() > 0) {
                        buildString {
                            append("规划如下：\n")
                            for (i in 0 until planArray.length()) {
                                val item = planArray.getJSONObject(i)
                                val desc = item.optString("description")
                                append("${i + 1}. $desc\n")
                            }
                        }
                    } else {
                        step.thought
                    }
                } catch (e: Exception) {
                    step.thought
                }
            } else {
                buildString {
                    if (step.thought.isNotBlank()) {
                        append("💭 思考：${step.thought}\n\n")
                    }
                    if (step.action.isNotBlank()) {
                        append("🎯 动作：${ActionJsonUtils.formatActionForDisplay(step.action)}")
                    }
                    if (step.observation?.isNotBlank() == true) {
                        append("\n\n📊 观察：${step.observation}")
                    }
                }
            }

            UiChatMessage(
                id = "${task.createdAt}_step_${index}",
                role = MessageRole.ASSISTANT,
                content = content,
                thinking = step.thought.takeIf { it.isNotBlank() },
                action = step.action.takeIf { it.isNotBlank() },
                timestamp = step.timestamp
            )
        }

        // 如果有任务状态信息，添加最终状态消息
        val allMessages = mutableListOf<UiChatMessage>()
        allMessages.add(userMessage)
        allMessages.addAll(stepMessages)

        task.state?.let { state ->
            if (state.summary.isNotBlank() || state.note.isNotBlank()) {
                val statusContent = buildString {
                    append("📋 任务状态：${state.status}\n")
                    if (state.summary.isNotBlank()) {
                        append("\n✅ 总结：${state.summary}")
                    }
                    if (state.note.isNotBlank()) {
                        append("\n\n📝 备注：\n${state.note}")
                    }
                }

                allMessages.add(
                    UiChatMessage(
                        id = "${task.createdAt}_status",
                        role = MessageRole.ASSISTANT,
                        content = statusContent,
                        timestamp = task.createdAt + task.steps.size
                    )
                )
            }
        }

        // 更新 UI 状态
        _uiState.value = ChatUiState(
            messages = allMessages,
            taskSteps = task.steps,
            currentApp = _uiState.value.currentApp,
            isLoading = false,
            error = null
        )

        android.util.Log.d("AgentRuntime", "loadHistoryTask: Loaded ${allMessages.size - 1} messages")
    }

    fun runInstruction(
        instructionSetId: String,
        isPreview: Boolean = false,
        ignoreTargetPackage: Boolean = false
    ) {
        taskJob?.cancel()
        agentOrchestrator.cleanupAgents()
        instructionRunner = null

        scope.launch(Dispatchers.Default) {
            try {
                val accessibilityService = ScreenPalAccessibilityService.getInstance()
                if (accessibilityService == null) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(error = application.getString(R.string.accessibility_service_not_enabled))
                    }
                    return@launch
                }

                if (actionExecutor == null) {
                    attachAccessibilityService(accessibilityService)
                }

                val executor = actionExecutor
                if (executor == null) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(error = application.getString(R.string.accessibility_service_not_enabled))
                    }
                    return@launch
                }

                val app = application as ScreenPalApplication
                val steps = app.instructionRepository.getSteps(instructionSetId)

                val isPlanBased = steps.isNotEmpty() && steps.all { it.actionType == "LLM_PLAN" }

                if (isPreview && isPlanBased) {
                    startInstructionInternal(instructionSetId, steps)
                } else {
                    taskJob = coroutineContext.job

                    val runner = InstructionRunner(
                        app.instructionRepository,
                        executor,
                        ignoreTargetPackage = ignoreTargetPackage
                    )
                    instructionRunner = runner

                    val (width, height) = DeviceUtils.getRealScreenSize(application)
                    val rotation = DeviceUtils.getDisplayRotation(application)
                    val instructionSet = app.instructionRepository.getInstructionSet(instructionSetId)
                    val recordedSize = instructionSet?.deviceInfo
                        ?.split("x")
                        ?.let { parts ->
                            if (parts.size == 2) {
                                parts[0].toIntOrNull() to parts[1].toIntOrNull()
                            } else {
                                null to null
                            }
                        }
                    val recordedWidth = recordedSize?.first ?: width
                    val recordedHeight = recordedSize?.second ?: height

                    runWithStepsInternal(runner, steps, width, height) {
                        runner.run(instructionSetId, width, height, recordedWidth, recordedHeight, rotation)
                    }
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                Log.e("AgentRuntime", "Error running instruction", e)
                withContext(Dispatchers.Main.immediate) {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
            } finally {
                instructionRunner = null
            }
        }
    }

    /**
     * 执行 SkillInstructionSet（技能内嵌的按键集）
     */
    fun runSkillInstructionSet(
        skillInstructionSet: SkillInstructionSet,
        recordedWidth: Int,
        recordedHeight: Int,
        rotationDegrees: Int = 0,
        ignoreTargetPackage: Boolean = true
    ) {
        taskJob?.cancel()
        agentOrchestrator.cleanupAgents()
        instructionRunner = null

        scope.launch(Dispatchers.Default) {
            try {
                val accessibilityService = ScreenPalAccessibilityService.getInstance()
                if (accessibilityService == null) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(error = application.getString(R.string.accessibility_service_not_enabled))
                    }
                    return@launch
                }

                if (actionExecutor == null) {
                    attachAccessibilityService(accessibilityService)
                }

                val executor = actionExecutor
                if (executor == null) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(error = application.getString(R.string.accessibility_service_not_enabled))
                    }
                    return@launch
                }

                val app = application as ScreenPalApplication
                val (width, height) = DeviceUtils.getRealScreenSize(application)

                // 将 SkillInstructionSet.InstructionStep 转换为 InstructionStepEntity
                val steps = skillInstructionSet.steps.map { step ->
                    InstructionStepEntity(
                        setId = skillInstructionSet.id,
                        orderIndex = step.orderIndex,
                        actionType = step.actionType,
                        actionParams = step.actionParams,
                        description = step.description,
                        delayBefore = step.delayBefore
                    )
                }

                taskJob = coroutineContext.job

                val runner = InstructionRunner(
                    app.instructionRepository,
                    executor,
                    ignoreTargetPackage = ignoreTargetPackage
                )
                instructionRunner = runner

                runWithStepsInternal(runner, steps, width, height) {
                    runner.runWithSteps(
                        allSteps = steps,
                        screenWidth = width,
                        screenHeight = height,
                        recordedWidth = recordedWidth,
                        recordedHeight = recordedHeight,
                        rotationDegrees = rotationDegrees
                    )
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                Log.e("AgentRuntime", "Error running skill instruction", e)
                withContext(Dispatchers.Main.immediate) {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
            } finally {
                instructionRunner = null
            }
        }
    }

    private suspend fun runWithStepsInternal(
        runner: InstructionRunner,
        allSteps: List<InstructionStepEntity>,
        screenWidth: Int,
        screenHeight: Int,
        executionBlock: suspend () -> Unit
    ) {
        withContext(Dispatchers.Main) {
            _uiState.value = _uiState.value.copy(
                messages = emptyList(),
                taskCompletedMessage = null,
                error = null,
                isLoading = true,
                executionSteps = emptyList(),
                thinkingContent = "正在执行指令集..."
            )
        }

        val executableSteps = allSteps.filter { it.actionType != "LLM_PLAN" }
        val displayDescriptions = executableSteps.map { step ->
            resolvePlaybackDescription(step, screenWidth, screenHeight)
        }

        coroutineScope {
            val progressJob = launch {
                runner.currentStepIndex.collect { index ->
                    if (index >= 0 && index < executableSteps.size) {
                        val display = displayDescriptions[index]
                        withContext(Dispatchers.Main.immediate) {
                            _uiState.value = _uiState.value.copy(
                                thinkingContent = "执行步骤 ${index + 1}: ${display}"
                            )
                        }
                    }
                }
            }

            try {
                executionBlock()

                // 等待执行完成或状态变化
                runner.state.collect { state ->
                    if (state == RunnerState.COMPLETED || state == RunnerState.FAILED || state == RunnerState.STOPPED) {
                        withContext(Dispatchers.Main.immediate) {
                            if (runner.state.value == RunnerState.COMPLETED) {
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    taskCompletedMessage = "指令集执行完成"
                                )
                            } else if (runner.state.value == RunnerState.FAILED) {
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    error = "指令集执行失败"
                                )
                            } else if (runner.state.value == RunnerState.STOPPED) {
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    taskCompletedMessage = "指令集执行已停止",
                                    error = null
                                )
                            }
                        }
                        // 停止收集状态
                        throw CancellationException("Execution finished")
                    }
                }
            } catch (_: CancellationException) {
            } finally {
                progressJob.cancel()
            }
        }
    }

    fun runInstructionInline(instructionSetId: String, ignoreTargetPackage: Boolean = false) {
        scope.launch(Dispatchers.Default) {
            try {
                val accessibilityService = ScreenPalAccessibilityService.getInstance()
                if (accessibilityService == null) {
                    Log.w("AgentRuntime", "Inline instruction failed: accessibility service not ready")
                    return@launch
                }

                if (actionExecutor == null) {
                    attachAccessibilityService(accessibilityService)
                }

                val executor = actionExecutor
                if (executor == null) {
                    Log.w("AgentRuntime", "Inline instruction failed: action executor not ready")
                    return@launch
                }

                val app = application as ScreenPalApplication
                val (width, height) = DeviceUtils.getRealScreenSize(application)
                val rotation = DeviceUtils.getDisplayRotation(application)
                val instructionSet = app.instructionRepository.getInstructionSet(instructionSetId)
                val recordedSize = instructionSet?.deviceInfo
                    ?.split("x")
                    ?.let { parts ->
                        if (parts.size == 2) {
                            parts[0].toIntOrNull() to parts[1].toIntOrNull()
                        } else {
                            null to null
                        }
                    }
                val recordedWidth = recordedSize?.first ?: width
                val recordedHeight = recordedSize?.second ?: height

                val runner = InstructionRunner(
                    app.instructionRepository,
                    executor,
                    ignoreTargetPackage = ignoreTargetPackage
                )
                Log.i("AgentRuntime", "Inline running instruction set")
                runner.run(instructionSetId, width, height, recordedWidth, recordedHeight, rotation)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("AgentRuntime", "Inline instruction error", e)
            }
        }
    }

    /**
     * 同步执行指令集（阻塞直到完成）
     */
    suspend fun runInstructionSetSync(instructionSetId: String, repeatCount: Int = 1): Boolean {
        try {
            // 确保 Skills 加载完成，防止通过 ID 查找 Skill 指令集时失败
            skillRegistry.waitForUserSkillsLoad()

            val accessibilityService = ScreenPalAccessibilityService.getInstance()
            if (accessibilityService == null) {
                Log.w("AgentRuntime", "Sync instruction failed: accessibility service not ready")
                return false
            }

            if (actionExecutor == null) {
                attachAccessibilityService(accessibilityService)
            }

            val executor = actionExecutor
            if (executor == null) {
                Log.w("AgentRuntime", "Sync instruction failed: action executor not ready")
                return false
            }

            val app = application as ScreenPalApplication
            val (width, height) = DeviceUtils.getRealScreenSize(application)
            val rotation = DeviceUtils.getDisplayRotation(application)
            val instructionSet = app.instructionRepository.getInstructionSet(instructionSetId)
            val recordedSize = instructionSet?.deviceInfo
                ?.split("x")
                ?.let { parts ->
                    if (parts.size == 2) {
                        parts[0].toIntOrNull() to parts[1].toIntOrNull()
                    } else {
                        null to null
                    }
                }
            val recordedWidth = recordedSize?.first ?: width
            val recordedHeight = recordedSize?.second ?: height

            val runner = InstructionRunner(
                app.instructionRepository,
                executor,
                ignoreTargetPackage = false
            )

            val repeat = repeatCount.coerceAtLeast(1)
            repeat(repeat) { index ->
                Log.i("AgentRuntime", "Sync running instruction set (${index + 1}/$repeat)")
                runner.run(instructionSetId, width, height, recordedWidth, recordedHeight, rotation)
                val state = runner.state.value
                if (state != com.withcareer.screenpal_android.core.agent.runner.RunnerState.COMPLETED) {
                    Log.w("AgentRuntime", "Sync instruction ended with state=$state")
                    return false
                }
                if (index < repeat - 1) {
                    delay(300)
                }
            }

            return true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("AgentRuntime", "Sync instruction error", e)
            return false
        }
    }

    /**
     * 同步执行 Skill 内嵌指令集（阻塞直到完成）
     */
    suspend fun runSkillInstructionSetSync(
        skillInstructionSet: SkillInstructionSet,
        repeatCount: Int = 1
    ): Boolean {
        try {
            val accessibilityService = ScreenPalAccessibilityService.getInstance()
            if (accessibilityService == null) {
                Log.w("AgentRuntime", "Sync skill instruction failed: accessibility service not ready")
                return false
            }

            if (actionExecutor == null) {
                attachAccessibilityService(accessibilityService)
            }

            val executor = actionExecutor
            if (executor == null) {
                Log.w("AgentRuntime", "Sync skill instruction failed: action executor not ready")
                return false
            }

            val app = application as ScreenPalApplication
            val (width, height) = DeviceUtils.getRealScreenSize(application)

            // 转换步骤
            val steps = skillInstructionSet.steps.map { step ->
                InstructionStepEntity(
                    setId = skillInstructionSet.id,
                    orderIndex = step.orderIndex,
                    actionType = step.actionType,
                    actionParams = step.actionParams,
                    description = step.description,
                    delayBefore = step.delayBefore
                )
            }

            val runner = InstructionRunner(
                app.instructionRepository,
                executor,
                ignoreTargetPackage = true // Skill 指令集通常假设上下文已就绪
            )

            val repeat = repeatCount.coerceAtLeast(1)
            repeat(repeat) { index ->
                Log.i("AgentRuntime", "Sync running skill instruction set (${index + 1}/$repeat)")

                // 使用 runWithSteps 直接执行步骤列表
                // 注意：这里我们假设录制尺寸与当前屏幕一致，或者由 runner 内部处理适配
                runner.runWithSteps(
                    allSteps = steps,
                    screenWidth = width,
                    screenHeight = height,
                    recordedWidth = width,
                    recordedHeight = height,
                    rotationDegrees = 0
                )

                val state = runner.state.value
                if (state != com.withcareer.screenpal_android.core.agent.runner.RunnerState.COMPLETED) {
                    Log.w("AgentRuntime", "Sync skill instruction ended with state=$state")
                    return false
                }
                if (index < repeat - 1) {
                    delay(300)
                }
            }

            return true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("AgentRuntime", "Sync skill instruction error", e)
            return false
        }
    }

    fun pauseInstructionPlayback(): Boolean {
        val runner = instructionRunner ?: return false
        runner.pause()
        _uiState.value = _uiState.value.copy(thinkingContent = "已暂停播放")
        return true
    }

    fun resumeInstructionPlayback(): Boolean {
        val runner = instructionRunner ?: return false
        runner.resume()
        _uiState.value = _uiState.value.copy(thinkingContent = "继续执行指令集...")
        return true
    }

    fun stopInstructionPlayback(): Boolean {
        val runner = instructionRunner ?: return false
        runner.stop()
        taskJob?.cancel()
        taskJob = null
        instructionRunner = null
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            thinkingContent = "",
            taskCompletedMessage = "指令集执行已停止",
            error = null
        )
        return true
    }

    private suspend fun startInstructionInternal(instructionId: String, steps: List<InstructionStepEntity>) {
        val app = application as ScreenPalApplication
        val repo = app.instructionRepository
        val instructionSet = repo.getInstructionSet(instructionId)
        val title = instructionSet?.title ?: "自定义指令"

        val startInput = "执行指令集: $title"

        val subtasks = steps.mapIndexed { index, step ->
            Subtask(
                id = "step_${index}",
                description = step.description, // 高层级描述
                status = TaskStatus.PENDING
            )
        }.toMutableList()

        val taskState = TaskState(
            taskId = "temp_instruction_${System.currentTimeMillis()}", // Temporary
            goal = startInput,
            status = TaskStatus.IN_PROGRESS,
            subtasks = subtasks,
            summary = "加载指令集: $title"
        )

        // 1. 启动任务环境，传入 initialTaskState，直接进入执行模式
        startTask(startInput, skipPlanning = true, initialTaskState = taskState)

        // 更新 Repository (startTask 内部会创建 currentTaskId，这里需要确保一致性)
        taskManager.currentTaskId?.let { id ->
            // 更新 taskState 的 taskId
            val correctState = taskState.copy(taskId = id)
            taskManager.updateTaskState(correctState)
        }
    }

    /**
     * 重放任务
     * @param taskId 任务ID
     */
    fun replayTask(taskId: String) {
        val task = taskManager.getTask(taskId)
        if (task == null || task.steps.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = application.getString(R.string.task_not_found))
            return
        }

        // 停止当前任务
        taskJob?.cancel()
        agentOrchestrator.cleanupAgents()

        // 准备 UI
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            error = null,
            messages = _uiState.value.messages + UiChatMessage(
                id = System.currentTimeMillis().toString(),
                role = MessageRole.USER,
                content = "重放：${task.prompt}"
            ),
            thinkingContent = "正在准备重放..."
        )

        taskJob = scope.launch(Dispatchers.Default) {
            try {
                val accessibilityService = ScreenPalAccessibilityService.getInstance()
                if (accessibilityService == null) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = application.getString(R.string.accessibility_service_not_enabled)
                        )
                    }
                    return@launch
                }

                if (actionExecutor == null) {
                    attachAccessibilityService(accessibilityService)
                }

                val executor = actionExecutor
                if (executor == null) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = application.getString(R.string.accessibility_service_not_enabled)
                        )
                    }
                    return@launch
                }

                val metrics = application.resources.displayMetrics
                val steps = task.steps

                steps.forEachIndexed { index, step ->
                    // 检查是否取消
                    if (!isActive) return@forEachIndexed

                    val progressMsg = "正在重放步骤 ${index + 1}/${steps.size}"
                    _uiState.value = _uiState.value.copy(thinkingContent = progressMsg)

                    // 在 UI 上显示当前执行的步骤（动作用可读文案展示）
                    val stepDisplayContent = step.thought.ifBlank { ActionJsonUtils.formatActionForDisplay(step.action) }
                    val assistantMessage = UiChatMessage(
                        id = System.currentTimeMillis().toString(),
                        role = MessageRole.ASSISTANT,
                        content = stepDisplayContent,
                        thinking = step.thought,
                        action = step.action
                    )
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages + assistantMessage
                    )

                    // 执行动作
                    val result = executor.execute(step.action, metrics.widthPixels, metrics.heightPixels)

                    if (!result.success) {
                        throw Exception("Replay failed at step ${index + 1}: ${result.message}")
                    }

                    // 模拟操作间隔
                    delay(1500)
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    taskCompletedMessage = "重放完成",
                    thinkingContent = ""
                )

            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("AgentRuntime", "Replay error", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "重放失败：${e.message}"
                )
            }
        }
    }

    private fun resolvePlaybackDescription(
        step: InstructionStepEntity,
        screenWidth: Int,
        screenHeight: Int
    ): String {
        if (step.actionType != "swipe") return step.description
        return try {
            val json = JsonParser.parseString(step.actionParams).asJsonObject
            val path = json.getAsJsonArray("path")
            if (path != null && path.size() >= 2) {
                val first = path[0].asJsonArray
                val last = path[path.size() - 1].asJsonArray
                val (startX, startY) = ActionUtils.convertRelativeToAbsolute(
                    listOf(first[0].asFloat, first[1].asFloat),
                    screenWidth,
                    screenHeight
                )
                val (endX, endY) = ActionUtils.convertRelativeToAbsolute(
                    listOf(last[0].asFloat, last[1].asFloat),
                    screenWidth,
                    screenHeight
                )
                "滑动 (${startX.toInt()},${startY.toInt()}) → (${endX.toInt()},${endY.toInt()})"
            } else {
                step.description
            }
        } catch (_: Exception) {
            step.description
        }
    }
}
