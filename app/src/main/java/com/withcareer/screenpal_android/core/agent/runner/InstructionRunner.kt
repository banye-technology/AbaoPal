package com.withcareer.screenpal_android.core.agent.runner

import android.util.Log
import com.google.gson.JsonParser
import com.withcareer.screenpal_android.core.action.ActionExecutor
import com.withcareer.screenpal_android.core.action.ExecuteResult
import com.withcareer.screenpal_android.core.action.ActionParsingException
import com.withcareer.screenpal_android.data.repository.InstructionRepository
import com.withcareer.screenpal_android.data.room.InstructionStepEntity
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RunnerState {
    IDLE, RUNNING, PAUSED, COMPLETED, FAILED, STOPPED
}

class InstructionRunner(
    private val repository: InstructionRepository,
    private val actionExecutor: ActionExecutor,
    private val ignoreTargetPackage: Boolean = false
) {
    private val _state = MutableStateFlow(RunnerState.IDLE)
    val state = _state.asStateFlow()

    private val _currentStepIndex = MutableStateFlow(-1)
    val currentStepIndex = _currentStepIndex.asStateFlow()

    private var isStopped = false
    @Volatile
    private var isPaused = false

    suspend fun run(
        instructionSetId: String,
        screenWidth: Int,
        screenHeight: Int,
        recordedWidth: Int,
        recordedHeight: Int,
        rotationDegrees: Int
    ) {
        val allSteps = repository.getSteps(instructionSetId)
        runWithSteps(allSteps, screenWidth, screenHeight, recordedWidth, recordedHeight, rotationDegrees)
    }

    suspend fun runWithSteps(
        allSteps: List<InstructionStepEntity>,
        screenWidth: Int,
        screenHeight: Int,
        recordedWidth: Int,
        recordedHeight: Int,
        rotationDegrees: Int
    ) {
        _state.value = RunnerState.RUNNING
        _currentStepIndex.value = 0
        isStopped = false

        try {
            // Filter out plan steps for execution
            val executableSteps = allSteps.filter { it.actionType != "LLM_PLAN" }

            if (executableSteps.isEmpty()) {
                Log.w("InstructionRunner", "No executable steps found")
                _state.value = RunnerState.COMPLETED
                return
            }

            var lastStepStartMs: Long? = null
            for ((index, step) in executableSteps.withIndex()) {
                if (isStopped) {
                    _state.value = RunnerState.STOPPED
                    return
                }
                awaitIfPaused()

                _currentStepIndex.value = index
                val delayMs = step.delayBefore.coerceAtLeast(0)
                if (delayMs > 0) {
                    Log.d("InstructionRunner", "Waiting ${delayMs}ms before step $index")
                    delayWithPause(delayMs)
                }
                val stepStartMs = SystemClock.elapsedRealtime()
                if (lastStepStartMs != null) {
                    val actualIntervalMs = stepStartMs - lastStepStartMs
                    val expectedIntervalMs = delayMs
                    val deltaMs = actualIntervalMs - expectedIntervalMs
                    Log.d(
                        "InstructionRunner",
                        "Step interval: index=$index expected(prev)=${expectedIntervalMs}ms actual=${actualIntervalMs}ms delta=${deltaMs}ms"
                    )
                } else {
                    Log.d("InstructionRunner", "Step interval: index=$index (first step)")
                }
                Log.d("InstructionRunner", "Executing step $index")

                val result = executeStep(
                    step,
                    screenWidth,
                    screenHeight,
                    recordedWidth,
                    recordedHeight,
                    rotationDegrees
                )
                val stepEndMs = SystemClock.elapsedRealtime()
                Log.d(
                    "InstructionRunner",
                    "Step duration: index=$index exec=${stepEndMs - stepStartMs}ms"
                )

                if (!result.success) {
                    Log.e("InstructionRunner", "Step $index failed")
                    _state.value = RunnerState.FAILED
                    return
                }
                lastStepStartMs = stepStartMs
            }

            _state.value = RunnerState.COMPLETED
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.i("InstructionRunner", "Execution cancelled")
            _state.value = RunnerState.STOPPED
        } catch (e: Exception) {
            Log.e("InstructionRunner", "Execution failed", e)
            _state.value = RunnerState.FAILED
        }
    }

    private suspend fun executeStep(
        step: InstructionStepEntity,
        screenWidth: Int,
        screenHeight: Int,
        recordedWidth: Int,
        recordedHeight: Int,
        rotationDegrees: Int
    ): ExecuteResult {
        if (step.actionType == "LLM_PLAN") {
             // Skip high-level plan steps during execution
             // These are for display purposes only in the saved instruction set
             Log.d("InstructionRunner", "Skipping LLM plan step")
             return ExecuteResult(true, "Skipped plan step")
        }

        // Standard atomic action
        val actionParams = if (ignoreTargetPackage) {
            removeTargetPackage(step.actionParams)
        } else {
            step.actionParams
        }
        val adjustedParams = rotateActionParamsIfNeeded(
            actionParams,
            recordedWidth,
            recordedHeight,
            screenWidth,
            screenHeight,
            rotationDegrees
        )
        return try {
            actionExecutor.execute(adjustedParams, screenWidth, screenHeight)
        } catch (e: ActionParsingException) {
            Log.w("InstructionRunner", "Skipping invalid non-JSON step")
            ExecuteResult(true, "Skipped invalid/non-JSON step")
        }
    }

    private fun rotateActionParamsIfNeeded(
        actionParams: String,
        recordedWidth: Int,
        recordedHeight: Int,
        currentWidth: Int,
        currentHeight: Int,
        rotationDegrees: Int
    ): String {
        val recordedLandscape = recordedWidth >= recordedHeight
        val currentLandscape = currentWidth >= currentHeight
        if (recordedLandscape == currentLandscape || rotationDegrees == 0) {
            return actionParams
        }
        return try {
            val jsonElement = JsonParser.parseString(actionParams)
            if (!jsonElement.isJsonObject) return actionParams
            val obj = jsonElement.asJsonObject
            val actionObj = if (obj.has("action") && obj.get("action").isJsonObject) {
                obj.getAsJsonObject("action")
            } else {
                obj
            }
            if (actionObj.has("element") && actionObj.get("element").isJsonArray) {
                val array = actionObj.getAsJsonArray("element")
                if (array.size() >= 2) {
                    val rotated = rotateRelativePoint(array[0].asFloat, array[1].asFloat, rotationDegrees)
                    array[0] = com.google.gson.JsonPrimitive(rotated.first)
                    array[1] = com.google.gson.JsonPrimitive(rotated.second)
                }
            }
            if (actionObj.has("points") && actionObj.get("points").isJsonArray) {
                val points = actionObj.getAsJsonArray("points")
                points.forEach { point ->
                    if (point.isJsonArray) {
                        val arr = point.asJsonArray
                        if (arr.size() >= 2) {
                            val rotated = rotateRelativePoint(arr[0].asFloat, arr[1].asFloat, rotationDegrees)
                            arr[0] = com.google.gson.JsonPrimitive(rotated.first)
                            arr[1] = com.google.gson.JsonPrimitive(rotated.second)
                        }
                    } else if (point.isJsonObject) {
                        val obj = point.asJsonObject
                        val x = obj.get("x")?.asFloat
                        val y = obj.get("y")?.asFloat
                        if (x != null && y != null && x in 0f..1.5f && y in 0f..1.5f) {
                            val rotated = rotateRelativePoint(x, y, rotationDegrees)
                            obj.addProperty("x", rotated.first)
                            obj.addProperty("y", rotated.second)
                        }
                    }
                }
            }
            if (actionObj.has("path") && actionObj.get("path").isJsonArray) {
                val path = actionObj.getAsJsonArray("path")
                path.forEach { point ->
                    if (point.isJsonArray) {
                        val arr = point.asJsonArray
                        if (arr.size() >= 2) {
                            val rotated = rotateRelativePoint(arr[0].asFloat, arr[1].asFloat, rotationDegrees)
                            arr[0] = com.google.gson.JsonPrimitive(rotated.first)
                            arr[1] = com.google.gson.JsonPrimitive(rotated.second)
                        }
                    }
                }
            }
            if (actionObj.has("start") && actionObj.get("start").isJsonArray) {
                val start = actionObj.getAsJsonArray("start")
                if (start.size() >= 2) {
                    val rotated = rotateRelativePoint(start[0].asFloat, start[1].asFloat, rotationDegrees)
                    start[0] = com.google.gson.JsonPrimitive(rotated.first)
                    start[1] = com.google.gson.JsonPrimitive(rotated.second)
                }
            }
            if (actionObj.has("end") && actionObj.get("end").isJsonArray) {
                val end = actionObj.getAsJsonArray("end")
                if (end.size() >= 2) {
                    val rotated = rotateRelativePoint(end[0].asFloat, end[1].asFloat, rotationDegrees)
                    end[0] = com.google.gson.JsonPrimitive(rotated.first)
                    end[1] = com.google.gson.JsonPrimitive(rotated.second)
                }
            }
            obj.toString()
        } catch (_: Exception) {
            actionParams
        }
    }

    private fun rotateRelativePoint(x: Float, y: Float, rotationDegrees: Int): Pair<Float, Float> {
        return when (rotationDegrees) {
            90 -> y to (1f - x)
            180 -> (1f - x) to (1f - y)
            270 -> (1f - y) to x
            else -> x to y
        }
    }

    fun stop() {
        isStopped = true
    }

    fun pause() {
        isPaused = true
        _state.value = RunnerState.PAUSED
    }

    fun resume() {
        isPaused = false
        if (_state.value == RunnerState.PAUSED) {
            _state.value = RunnerState.RUNNING
        }
    }

    private suspend fun awaitIfPaused() {
        while (isPaused && !isStopped) {
            if (_state.value != RunnerState.PAUSED) {
                _state.value = RunnerState.PAUSED
            }
            delay(120)
        }
        if (!isStopped && _state.value == RunnerState.PAUSED) {
            _state.value = RunnerState.RUNNING
        }
    }

    private suspend fun delayWithPause(delayMs: Long) {
        var remaining = delayMs
        while (remaining > 0 && !isStopped) {
            awaitIfPaused()
            val chunk = minOf(remaining, 200L)
            delay(chunk)
            remaining -= chunk
        }
    }

    private fun removeTargetPackage(actionParams: String): String {
        return try {
            val jsonElement = JsonParser.parseString(actionParams)
            if (!jsonElement.isJsonObject) return actionParams
            val obj = jsonElement.asJsonObject
            obj.remove("target_package")
            val actionElement = obj.get("action")
            if (actionElement != null && actionElement.isJsonObject) {
                actionElement.asJsonObject.remove("target_package")
            }
            obj.toString()
        } catch (_: Exception) {
            actionParams
        }
    }
}
