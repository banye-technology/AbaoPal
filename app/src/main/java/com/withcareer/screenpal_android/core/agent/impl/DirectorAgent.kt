package com.withcareer.screenpal_android.core.agent.impl

import android.graphics.Bitmap
import android.util.Log
import com.withcareer.screenpal_android.core.agent.framework.*
import com.withcareer.screenpal_android.data.preference.TaskState
import com.withcareer.screenpal_android.data.preference.TaskStatus
import com.withcareer.screenpal_android.service.ScreenPalAccessibilityService
import com.withcareer.screenpal_android.util.BitmapUtils
import com.withcareer.screenpal_android.util.DeviceUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 导演智能体 (Director / Orchestrator)
 * 负责协调任务流程：Plan -> Loop (Observe -> Execute -> Action)
 */
class DirectorAgent(
    private val accessibilityService: ScreenPalAccessibilityService,
    bus: AgentBus,
    scope: CoroutineScope,
    private val maxSteps: Int = 100
) : BaseAgent(bus, scope) {

    private var currentTaskState: TaskState? = null
    private var stepCount = 0

    private var isTaskEnded = false
    private var isReplanning = false
    private var pendingScreenshot: Bitmap? = null
    private var planTimeoutJob: Job? = null
    private var isPreLaunching = false

    override fun stop() {
        clearPendingScreenshot()
        planTimeoutJob?.cancel()
        planTimeoutJob = null
        super.stop()
    }

    override suspend fun handleMessage(message: AgentMessage) {
        if (isTaskEnded) return

        when (message) {
            is UserRequestMessage -> {
                Log.i("Director", "User feedback received")
                // 视为用户反馈，触发重新规划
                if (currentTaskState != null && !isTaskEnded) {
                    bus.emit(PlanRequestMessage(
                        goal = currentTaskState?.goal ?: "",
                        taskId = currentTaskState?.taskId,
                        lastError = "User Feedback: ${message.content}"
                    ))
                } else {
                    Log.w("Director", "Received UserRequestMessage but no active task.")
                }
            }
            is SystemErrorMessage -> {
                Log.e("Director", "System error received")
                handleError(message.error)
            }
            is TaskStartedMessage -> {
                Log.i("Director", "Task started")
                stepCount = 0
                isTaskEnded = false

                clearPendingScreenshot()
                planTimeoutJob?.cancel()

                val initialState = TaskState(
                    taskId = message.taskId ?: System.currentTimeMillis().toString(),
                    goal = message.goal,
                    subtasks = mutableListOf(),
                    summary = "Initializing..."
                )
                currentTaskState = initialState

                Log.i("Director", "Emitting PlanRequestMessage")
                bus.emit(PlanRequestMessage(message.goal, message.taskId))

                Log.i("Director", "Task started. Requesting Plan and starting Observation in parallel.")
                stepCount = 1
                bus.emit(StepStartedMessage(stepCount, initialState))
                triggerObservation(initialState)
            }
            is StartExecutionMessage -> {
                Log.i("Director", "Execution-only mode started")
                stepCount = 0
                isTaskEnded = false

                clearPendingScreenshot()
                planTimeoutJob?.cancel()

                currentTaskState = message.taskState

                Log.i("Director", "Plan provided. Starting Observation and Execution loop.")
                stepCount = 1
                bus.emit(StepStartedMessage(stepCount, message.taskState))
                triggerObservation(message.taskState)
            }
            is PlanGeneratedMessage -> {
                Log.i("Director", "Plan updated asynchronously")
                currentTaskState?.let { current ->
                    val plan = message.taskState
                    if (plan.subtasks.isNotEmpty()) {
                        currentTaskState = current.copy(
                            subtasks = plan.subtasks,
                            summary = plan.summary
                        )
                        bus.emit(StateUpdatedMessage(currentTaskState!!))

                        val screenshot = pendingScreenshot
                        pendingScreenshot = null
                        if (screenshot != null) {
                            val shouldPreLaunch = shouldPreLaunchFirstSubtask(currentTaskState!!)
                            if (shouldPreLaunch) {
                                val openAppName = extractAppNameFromOpenSubtask(currentTaskState!!)
                                if (!openAppName.isNullOrBlank()) {
                                    if (!screenshot.isRecycled) {
                                        screenshot.recycle()
                                    }
                                    val currentApp = accessibilityService.getActivePackageName()
                                    Log.i("Director", "Pre-launching target app")
                                    isPreLaunching = true
                                    bus.emit(ActionRequestMessage("""{"action_type":"do","action":"launch","app":"$openAppName"}"""))
                                } else {
                                    Log.w("Director", "Pre-launch skipped: failed to extract app name from subtask.")
                                    Log.i("Director", "Plan ready and Observation ready. Triggering Execution.")
                                    bus.emit(ExecutionRequestMessage(currentTaskState!!, screenshot))
                                }
                            } else {
                                Log.i("Director", "Plan ready and Observation ready. Triggering Execution.")
                                bus.emit(ExecutionRequestMessage(currentTaskState!!, screenshot))
                            }
                        } else {
                            Log.i("Director", "Plan ready. Waiting for Observation.")
                        }
                    } else {
                         Log.w("Director", "Plan returned empty.")
                    }
                }
            }
            is ObservationGeneratedMessage -> {
                Log.i("Director", "Observation received.")
                if (currentTaskState != null) {
                    if (currentTaskState!!.subtasks.isNotEmpty()) {
                         Log.i("Director", "Plan is ready. Triggering Execution.")
                         bus.emit(ExecutionRequestMessage(currentTaskState!!, message.screenshot))
                    } else {
                        Log.i("Director", "Plan not ready. Holding Observation.")
                        pendingScreenshot?.let {
                            if (it != message.screenshot && !it.isRecycled) {
                                it.recycle()
                            }
                        }
                        pendingScreenshot = message.screenshot
                    }
                } else {
                    handleError("Internal State Error: Missing task state")
                }
            }
            is StateUpdatedMessage -> {
                 Log.i("Director", "State updated asynchronously.")
                 currentTaskState = message.taskState

                 if (currentTaskState?.status == TaskStatus.COMPLETED) {
                     Log.i("Director", "State marked as COMPLETED. Waiting for action execution to finish task.")
                 }
            }
            is ExecutionGeneratedMessage -> {
                Log.i("Director", "Execution action generated")
                bus.emit(ActionRequestMessage(message.actionJson))
            }
            is ActionExecutedMessage -> {
                Log.i("Director", "Action executed. Success: ${message.success}")
                stepCount++

                if (isPreLaunching) {
                    isPreLaunching = false
                }

                if (stepCount >= maxSteps) {
                    handleError("Task exceeded maximum steps ($maxSteps). Terminating.")
                    return
                }

                if (message.success) {
                    var shouldFinish = false
                    var finishSummary = "Completed"

                    if (!message.resultData.isNullOrEmpty()) {
                        try {
                            val resultJson = org.json.JSONObject(message.resultData)

                            val completedSubtaskId = resultJson.optString("completed_subtask_id", "")
                            if (completedSubtaskId.isNotEmpty()) {
                                // 验证是否为批量标记（包含逗号）
                                if (completedSubtaskId.contains(",")) {
                                    Log.w("Director", "⚠️ Executor attempted batch completion")
                                    Log.w("Director", "⚠️ This is not allowed. Only single subtask can be completed at a time.")
                                    Log.w("Director", "⚠️ Subtask state NOT updated. System will continue with current PENDING subtask.")
                                } else {
                                    Log.i("Director", "Subtask completion reported by Executor")
                                    currentTaskState?.let { currentState ->
                                        val matchedSubtask = currentState.subtasks.find { it.id == completedSubtaskId }
                                        if (matchedSubtask != null) {
                                            Log.i("Director", "Subtask matched")
                                            val updatedSubtasks = currentState.subtasks.map { sub ->
                                                if (sub.id == completedSubtaskId) {
                                                    sub.copy(status = TaskStatus.COMPLETED)
                                                } else {
                                                    sub
                                                }
                                            }.toMutableList()
                                            val allDone = updatedSubtasks.isNotEmpty() && updatedSubtasks.all { sub ->
                                                sub.status == TaskStatus.COMPLETED || sub.status == TaskStatus.SKIPPED
                                            }

                                            currentTaskState = if (allDone) {
                                                currentState.copy(
                                                    subtasks = updatedSubtasks,
                                                    status = TaskStatus.COMPLETED,
                                                    summary = currentState.summary.ifBlank { "Completed" }
                                                )
                                            } else {
                                                currentState.copy(subtasks = updatedSubtasks)
                                            }
                                            bus.emit(StateUpdatedMessage(currentTaskState!!))

                                            if (allDone) {
                                                finishTask(currentTaskState?.summary ?: "Completed")
                                                return
                                            }
                                        } else {
                                            Log.w("Director", "✗ Reported subtask was not found")
                                            Log.w("Director", "Available subtask count: ${currentState.subtasks.size}")
                                        }
                                    }
                                }
                            }

                        } catch (e: Exception) {
                            Log.w("Director", "Failed to parse resultData for context update", e)
                        }
                    }

                    if (message.actionType.equals("finish", ignoreCase = true)) {
                         shouldFinish = true
                         finishSummary = message.message ?: "Finished"
                         Log.i("Director", "Task finish signal received")
                    } else if (message.actionType.equals("replan", ignoreCase = true)) {
                        Log.i("Director", "Replan requested")
                        isReplanning = true
                        bus.emit(PlanRequestMessage(
                            goal = currentTaskState?.goal ?: "",
                            taskId = currentTaskState?.taskId,
                            lastError = message.message
                        ))
                        return
                    }

                    if (shouldFinish) {
                        finishTask(finishSummary)
                        return
                    }

                } else {
                    Log.w("Director", "Action failed; continuing loop to allow self-correction")
                }

                // Dynamic delay based on action type
                val delayMs = when {
                    message.actionType.equals("swipe", ignoreCase = true) -> 2000L  // Swipe needs more time for content to load
                    message.actionType.equals("runinstructionset", ignoreCase = true) -> 500L  // InstructionSet already has internal delays
                    else -> 1000L  // Default delay
                }
                Log.d("Director", "Waiting ${delayMs}ms after action")
                delay(delayMs)

                Log.d("Director", "Checking status after delay: ${currentTaskState?.status}")
                currentTaskState?.let { state ->
                    val pendingSubtasks = state.subtasks.filter { it.status == TaskStatus.PENDING }
                    val completedSubtasks = state.subtasks.filter { it.status == TaskStatus.COMPLETED }
                    Log.d("Director", "Subtask progress: ${completedSubtasks.size}/${state.subtasks.size} completed, ${pendingSubtasks.size} pending")

                    if (pendingSubtasks.isEmpty()) {
                        Log.i("Director", "All subtasks completed. Finishing task.")
                        finishTask(state.summary.ifEmpty { "所有子任务已完成" })
                    } else {
                        Log.d("Director", "Continuing with next pending subtask")
                        triggerObservation(state)
                    }
                } ?: run {
                    Log.w("Director", "Task state is null after action. Cannot continue.")
                }
            }
            else -> {}
        }
    }

    private fun triggerObservation(taskState: TaskState) {
        Log.d("Director", "Triggering observation (internal)...")
        Log.d("Director", "scope.isActive: ${scope.coroutineContext[kotlinx.coroutines.Job]?.isActive}")
        scope.launch {
            Log.d("Director", "scope.launch started, calling captureScreenshot")
            try {
                captureScreenshot(taskState)
            } catch (e: Exception) {
                Log.e("Director", "captureScreenshot failed", e)
                handleError("Screenshot capture failed: ${e.message}")
            }
        }
        Log.d("Director", "scope.launch called")
    }

    private suspend fun captureScreenshot(taskState: TaskState) {
        val startMs = android.os.SystemClock.elapsedRealtime()
        Log.d("Director", "captureScreenshot: starting")

        var screenshot = accessibilityService.takeScreenshotSuspend()
        Log.d("Director", "captureScreenshot: completed, available=${screenshot != null}")

        var blackScreenRetryCount = 0
        val maxBlackScreenRetries = 5

        while (screenshot != null && BitmapUtils.isBitmapBlack(screenshot) && !DeviceUtils.isEmulator() && blackScreenRetryCount < maxBlackScreenRetries) {
            Log.d("Director", "Detected black screen, waiting... ($blackScreenRetryCount/$maxBlackScreenRetries)")
            if (!screenshot.isRecycled) {
                screenshot.recycle()
            }
            delay(1500)
            screenshot = accessibilityService.takeScreenshotSuspend()
            blackScreenRetryCount++
        }

        if (screenshot != null) {
            val resizeStart = android.os.SystemClock.elapsedRealtime()
            val resizedScreenshot = BitmapUtils.resizeBitmap(screenshot, 1920)
            if (resizedScreenshot != screenshot && !screenshot.isRecycled) {
                screenshot.recycle()
            }
            Log.d("Director", "Timing screenshot resize: ${android.os.SystemClock.elapsedRealtime() - resizeStart}ms")
            Log.d("Director", "captureScreenshot: emitting ObservationGeneratedMessage")
            bus.emit(ObservationGeneratedMessage(taskState, resizedScreenshot))
            Log.d("Director", "Timing captureScreenshot total: ${android.os.SystemClock.elapsedRealtime() - startMs}ms")
        } else {
            Log.e("Director", "captureScreenshot: Failed to capture screenshot")
            handleError("Failed to capture screenshot")
        }
    }

    private suspend fun finishTask(summary: String) {
        if (isTaskEnded) return
        isTaskEnded = true
        clearPendingScreenshot()
        bus.emit(TaskCompletedMessage(summary))
    }

    private suspend fun handleError(reason: String) {
        if (isTaskEnded) return
        isTaskEnded = true
        Log.e("Director", "Task failed")
        clearPendingScreenshot()
        bus.emit(TaskFailedMessage(reason))
    }

    private fun clearPendingScreenshot() {
        pendingScreenshot?.let {
            if (!it.isRecycled) {
                it.recycle()
            }
        }
        pendingScreenshot = null
    }

    private fun shouldPreLaunchFirstSubtask(state: TaskState): Boolean {
        val currentApp = accessibilityService.getActivePackageName()
        if (currentApp.isNullOrBlank()) return false
        if (currentApp != accessibilityService.packageName) return false
        val firstPending = state.subtasks.firstOrNull { it.status == TaskStatus.PENDING } ?: return false
        return firstPending.description.trim().startsWith("打开")
    }

    private fun extractAppNameFromOpenSubtask(state: TaskState): String? {
        val firstPending = state.subtasks.firstOrNull { it.status == TaskStatus.PENDING } ?: return null
        var s = firstPending.description.trim()
        if (!s.startsWith("打开")) return null
        s = s.removePrefix("打开").trim()
        s = s.removeSuffix("应用").trim()
        s = s.removeSuffix("APP").trim()
        s = s.removeSuffix("App").trim()
        s = s.removeSuffix("app").trim()
        if (s.isBlank()) return null
        val stopTokens = listOf("并", "然后", "再", "之后", "。", "，", ",", ".", "!", "！")
        val cutIndex = stopTokens.map { token -> s.indexOf(token).takeIf { it >= 0 } ?: Int.MAX_VALUE }.minOrNull() ?: Int.MAX_VALUE
        val name = if (cutIndex != Int.MAX_VALUE) s.substring(0, cutIndex).trim() else s
        return name.ifBlank { null }
    }
}
