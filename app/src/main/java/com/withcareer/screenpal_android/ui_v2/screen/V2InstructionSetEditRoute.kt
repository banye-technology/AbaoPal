package com.withcareer.screenpal_android.ui_v2.screen

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.withcareer.screenpal_android.data.model.InstructionStep
import com.withcareer.screenpal_android.data.model.SkillInstructionSet
import com.withcareer.screenpal_android.recording.RecordedAction
import com.withcareer.screenpal_android.recording.RecordingResult
import com.withcareer.screenpal_android.ui.screen.RecordingEditScreen
import java.util.UUID

import com.withcareer.screenpal_android.recording.CoordinateAdapter

/**
 * V2 按键集编辑页面
 * 直接复用「技能库-按键录制」的编辑页面，并做必要适配
 */
@Composable
fun V2InstructionSetEditRoute(
    initialSteps: List<InstructionStep>,
    recordingResult: RecordingResult? = null,
    instructionSet: SkillInstructionSet? = null,
    onSave: (SkillInstructionSet) -> Unit,
    onBack: () -> Unit
) {
    val sourceSteps = remember(initialSteps, instructionSet) {
        if (initialSteps.isNotEmpty()) {
            initialSteps
        } else {
            instructionSet?.steps ?: emptyList()
        }
    }
    val context = LocalContext.current
    val fallbackScreenSize = remember(context) { getScreenSize(context) }
    val recordingId = remember(instructionSet?.id) {
        instructionSet?.id ?: UUID.randomUUID().toString()
    }
    val resolvedRecordingResult = remember(
        recordingId,
        sourceSteps,
        instructionSet?.name,
        recordingResult,
        fallbackScreenSize
    ) {
        val fallbackWidth = fallbackScreenSize.first
        val fallbackHeight = fallbackScreenSize.second
        val supplied = recordingResult
        if (supplied != null) {
            if (supplied.screenWidth > 0 && supplied.screenHeight > 0) {
                supplied
            } else {
                supplied.copy(
                    screenWidth = fallbackWidth,
                    screenHeight = fallbackHeight
                )
            }
        } else {
            stepsToRecordingResult(
                steps = sourceSteps,
                recordingId = recordingId,
                title = instructionSet?.name,
                screenWidth = fallbackWidth,
                screenHeight = fallbackHeight
            )
        }
    }

    var description by remember { mutableStateOf(instructionSet?.description ?: "") }
    var descriptionError by remember { mutableStateOf(false) }
    var showValidationErrorDialog by remember { mutableStateOf(false) }
    var validationErrorMessage by remember { mutableStateOf("") }

    if (showValidationErrorDialog) {
        AlertDialog(
            shape = RoundedCornerShape(16.dp),
            onDismissRequest = { showValidationErrorDialog = false },
            title = { Text("保存失败") },
            text = { Text(validationErrorMessage) },
            confirmButton = {
                TextButton(onClick = { showValidationErrorDialog = false }) {
                    Text("确定")
                }
            }
        )
    }

    RecordingEditScreen(
        recordingResult = resolvedRecordingResult,
        description = description,
        onDescriptionChange = {
            description = it
            descriptionError = false
        },
        descriptionError = descriptionError,
        onSave = { title, result ->
            if (description.isBlank()) {
                descriptionError = true
                validationErrorMessage = "请填写按键集描述"
                showValidationErrorDialog = true
                return@RecordingEditScreen
            }
            onSave(
                SkillInstructionSet(
                    id = instructionSet?.id ?: UUID.randomUUID().toString(),
                    name = title,
                    description = description,
                    steps = recordingResultToSteps(result),
                    createdAt = instructionSet?.createdAt ?: System.currentTimeMillis()
                )
            )
        },
        onBack = onBack
    )
}

private fun stepsToRecordingResult(
    steps: List<InstructionStep>,
    recordingId: String,
    title: String?,
    screenWidth: Int,
    screenHeight: Int
): RecordingResult {
    var timestamp = 0L
    var resolvedWidth = screenWidth
    var resolvedHeight = screenHeight
    val actions = steps.sortedBy { it.orderIndex }.map { step ->
        timestamp += step.delayBefore
        runCatching {
            val obj = com.google.gson.JsonParser.parseString(step.actionParams).asJsonObject
            val w = obj.get("screen_width")?.asInt ?: 0
            val h = obj.get("screen_height")?.asInt ?: 0
            if (w > 0 && h > 0) {
                resolvedWidth = w
                resolvedHeight = h
            }
        }
        RecordedAction(
            actionType = step.actionType,
            paramsJson = step.actionParams,
            timestamp = timestamp,
            delayBefore = step.delayBefore
        )
    }
    val totalDuration = if (actions.isNotEmpty()) timestamp + 1000L else 0L
    return RecordingResult(
        id = recordingId,
        title = title,
        screenshotPath = null,
        actions = actions,
        screenWidth = resolvedWidth,
        screenHeight = resolvedHeight,
        totalDuration = totalDuration
    )
}

fun recordingResultToSteps(result: RecordingResult): List<InstructionStep> {
    return result.actions.mapIndexed { index, action ->
        // 使用 CoordinateAdapter 转换为标准格式 (归一化)
        val standardParams = CoordinateAdapter.convertToStandardFormat(
            action,
            result.screenWidth,
            result.screenHeight
        )

        InstructionStep(
            orderIndex = index,
            actionType = action.actionType,
            actionParams = standardParams,
            description = formatRecordedActionDescription(action, result.screenWidth, result.screenHeight),
            delayBefore = action.delayBefore
        )
    }
}

fun formatRecordedActionDescription(
    action: RecordedAction,
    screenWidth: Int,
    screenHeight: Int
): String {
    return when (action.actionType) {
        "tap" -> {
            val x = (action.params["x"] as? Number)?.toFloat()
            val y = (action.params["y"] as? Number)?.toFloat()
            val (resolvedX, resolvedY) = if (x != null && y != null) {
                val isRelative = x in 0f..1.5f && y in 0f..1.5f
                if (isRelative && screenWidth > 0 && screenHeight > 0) {
                    (x * screenWidth).toInt() to (y * screenHeight).toInt()
                } else {
                    x.toInt() to y.toInt()
                }
            } else {
                val element = action.params["element"] as? List<*>
                val relX = (element?.getOrNull(0) as? Number)?.toFloat()
                val relY = (element?.getOrNull(1) as? Number)?.toFloat()
                if (relX != null && relY != null && screenWidth > 0 && screenHeight > 0) {
                    (relX * screenWidth).toInt() to (relY * screenHeight).toInt()
                } else {
                    0 to 0
                }
            }
            "点击 ($resolvedX, $resolvedY)"
        }
        "multiTap" -> {
            val points = action.params["points"] as? List<*>
            val count = points?.size ?: 0
            "多点点击 ($count 个点位)"
        }
        "swipe", "longPressSwipe" -> {
            val pathList = action.params["path"] as? List<*>
            if (!pathList.isNullOrEmpty()) {
                val first = pathList.firstOrNull() as? Map<*, *>
                val last = pathList.lastOrNull() as? Map<*, *>
                val (startX, startY) = resolvePathPoint(first, screenWidth, screenHeight)
                val (endX, endY) = resolvePathPoint(last, screenWidth, screenHeight)
                val duration = (action.params["duration"] as? Number)?.toLong()
                val desc = if (action.actionType == "longPressSwipe" && duration != null) {
                    "长按滑动 ($startX,$startY) → ($endX,$endY) ${duration}ms"
                } else {
                    "滑动 ($startX,$startY) → ($endX,$endY)"
                }
                return desc
            }
            val startX = (action.params["startX"] as? Number)?.toInt() ?: 0
            val startY = (action.params["startY"] as? Number)?.toInt() ?: 0
            val endX = (action.params["endX"] as? Number)?.toInt() ?: 0
            val endY = (action.params["endY"] as? Number)?.toInt() ?: 0
            val duration = (action.params["duration"] as? Number)?.toLong()
            val desc = if (action.actionType == "longPressSwipe" && duration != null) {
                "长按滑动 ($startX,$startY) → ($endX,$endY) ${duration}ms"
            } else {
                "滑动 ($startX,$startY) → ($endX,$endY)"
            }
            return desc
        }
        "type" -> {
            val text = action.params["text"] as? String ?: ""
            "输入: ${text.take(20)}${if(text.length > 20) "..." else ""}"
        }
        "scroll" -> "滚动"
        else -> action.actionType
    }
}

private fun resolvePathPoint(
    point: Map<*, *>?,
    screenWidth: Int,
    screenHeight: Int
): Pair<Int, Int> {
    val x = (point?.get("x") as? Number)?.toFloat()
    val y = (point?.get("y") as? Number)?.toFloat()
    if (x == null || y == null) return 0 to 0
    val isZeroToOne = x in 0f..1.5f && y in 0f..1.5f
    return if (isZeroToOne && screenWidth > 0 && screenHeight > 0) {
        (x * screenWidth).toInt() to (y * screenHeight).toInt()
    } else {
        x.toInt() to y.toInt()
    }
}

private fun getScreenSize(context: Context): Pair<Int, Int> {
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val metrics = DisplayMetrics()
    @Suppress("DEPRECATION")
    windowManager.defaultDisplay.getRealMetrics(metrics)
    return metrics.widthPixels to metrics.heightPixels
}
