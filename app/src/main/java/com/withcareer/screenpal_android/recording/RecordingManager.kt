package com.withcareer.screenpal_android.recording

import android.content.Context
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 录制管理器
 * 负责管理录制状态、记录操作序列和自动计算操作间隔
 *
 */
class RecordingManager(
    private val context: Context
) {
    /**
     * 视觉反馈回调接口
     * 当录制新操作时触发，用于显示视觉反馈
     */
    interface FeedbackCallback {
        fun onActionRecorded(action: RecordedAction, stepNumber: Int)
    }
    companion object {
        var instance: RecordingManager? = null

        private const val MIN_DELAY_MS = 100L      // 快速操作的最小延迟
        private const val MAX_DELAY_MS = 2000L     // 长时间停顿的最大延迟
        private const val FAST_OPERATION_THRESHOLD = 500L  // 快速操作阈值
        private const val LONG_PAUSE_THRESHOLD = 5000L    // 长时间停顿阈值
    }

    private val _recordedActions = MutableStateFlow<List<RecordedAction>>(emptyList())
    val recordedActions: StateFlow<List<RecordedAction>> = _recordedActions.asStateFlow()

    var isRecording = false
        private set

    private var startTime: Long = 0
    private var lastActionTime: Long = 0
    private var pausedDurationMs: Long = 0
    private var batchPauseStartMs: Long? = null
    private var batchDisplayReduction: Int = 0

    // 视觉反馈回调
    var feedbackCallback: FeedbackCallback? = null

    // 录制时的屏幕尺寸（用于后续坐标转换）
    var screenWidth: Int = 0
        private set
    var screenHeight: Int = 0
        private set

    private fun updateScreenSize() {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
    }

    /**
     * 开始录制
     * 重置所有状态并开始记录操作
     */
    fun startRecording() {
        updateScreenSize()
        isRecording = true
        startTime = SystemClock.elapsedRealtime()
        lastActionTime = startTime
        pausedDurationMs = 0
        batchPauseStartMs = null
        batchDisplayReduction = 0
        _recordedActions.value = emptyList()
    }

    /**
     * 重置录制状态（不开始录制）
     * 用于取消录制或开始前清理旧数据
     */
    fun resetRecordingState() {
        isRecording = false
        startTime = 0
        lastActionTime = 0
        pausedDurationMs = 0
        batchPauseStartMs = null
        batchDisplayReduction = 0
        _recordedActions.value = emptyList()
    }

    /**
     * 停止录制
     * @return 录制结果，包含所有操作和屏幕尺寸信息
     */
    @Suppress("DEPRECATION")
    fun stopRecording(): RecordingResult {
        isRecording = false
        val actions = _recordedActions.value
        val duration = if (actions.isNotEmpty()) {
            actions.last().timestamp + 1000L // 默认加上 1s 结束延迟
        } else {
            0L
        }

        return RecordingResult(
            id = null,
            title = null,
            actions = actions,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            totalDuration = duration
        )
    }

    /**
     * 记录点击操作
     * @param x 绝对X坐标（像素）
     * @param y 绝对Y坐标（像素）
     * @param targetPackage 目标应用包名（可选）
     */
    fun recordTap(x: Int, y: Int, targetPackage: String? = null) {
        if (!isRecording) return
        updateScreenSize()

        val now = SystemClock.elapsedRealtime()
        val action = RecordedAction.create(
            actionType = "tap",
            params = mapOf("x" to x, "y" to y),
            timestamp = getElapsedRecordingMs(now),
            delayBefore = calculateAutoDelay(now),
            targetPackage = targetPackage
        )

        _recordedActions.value += action
        lastActionTime = now

        // 通知视觉反馈层
        feedbackCallback?.onActionRecorded(action, getDisplayActionCount())
    }

    /**
     * 记录多点同时点击操作
     * @param points 多点坐标（像素）
     * @param targetPackage 目标应用包名（可选）
     */
    fun recordMultiTap(points: List<Pair<Int, Int>>, targetPackage: String? = null) {
        if (!isRecording || points.isEmpty()) return
        updateScreenSize()

        val now = SystemClock.elapsedRealtime()
        val params = mapOf(
            "points" to points.map { (x, y) -> mapOf("x" to x, "y" to y) }
        )

        val action = RecordedAction.create(
            actionType = "multiTap",
            params = params,
            timestamp = getElapsedRecordingMs(now),
            delayBefore = calculateAutoDelay(now),
            targetPackage = targetPackage
        )

        _recordedActions.value += action
        lastActionTime = now

        feedbackCallback?.onActionRecorded(action, getDisplayActionCount())
    }

    /**
     * 批量记录多点点击（同一时间戳，无延迟）
     */
    fun recordMultiTapBatch(
        batches: List<List<Pair<Int, Int>>>,
        targetPackage: String? = null,
        timestampOverride: Long
    ) {
        if (!isRecording || batches.isEmpty()) return
        updateScreenSize()

        val now = SystemClock.elapsedRealtime()
        val mergedPoints = batches.flatten()
        if (mergedPoints.isEmpty()) return
        val params = mapOf(
            "points" to mergedPoints.map { (x, y) -> mapOf("x" to x, "y" to y) }
        )
        val action = RecordedAction.create(
            actionType = "multiTap",
            params = params,
            timestamp = timestampOverride,
            delayBefore = 0L,
            targetPackage = targetPackage
        )
        _recordedActions.value += action
        lastActionTime = now
    }

    /**
     * 记录滑动操作（带完整轨迹）
     * @param path 轨迹点列表 (x, y, relativeTime)
     * @param targetPackage 目标应用包名（可选）
     * @param duration 滑动持续时间（毫秒，可选）
     */
    fun recordSwipeWithPath(path: List<Triple<Int, Int, Long>>, targetPackage: String? = null, duration: Long = 0) {
        if (!isRecording || path.size < 2) return
        updateScreenSize()

        val now = SystemClock.elapsedRealtime()

        // 将轨迹点转换为 List<Map<String, Any>>
        // 归一化坐标 (0-1) 以支持跨设备回放
        val pathList = path.map { (x, y, t) ->
            mapOf(
                "x" to (x.toFloat() / screenWidth),
                "y" to (y.toFloat() / screenHeight),
                "t" to t
            )
        }

        val params = mutableMapOf<String, Any>(
            "path" to pathList
        )
        if (duration > 0) {
            params["duration"] = duration
        }

        val actionType = if (duration > 500) "longPressSwipe" else "swipe"

        val action = RecordedAction.create(
            actionType = actionType,
            params = params,
            timestamp = getElapsedRecordingMs(now),
            delayBefore = calculateAutoDelay(now),
            targetPackage = targetPackage
        )

        _recordedActions.value += action
        lastActionTime = now

        // 通知视觉反馈层（使用起点坐标显示）
        feedbackCallback?.onActionRecorded(action, getDisplayActionCount())
    }

    /**
     * 记录滑动操作（旧方法，向后兼容）
     * @param startX 起点X坐标
     * @param startY 起点Y坐标
     * @param endX 终点X坐标
     * @param endY 终点Y坐标
     */
    fun recordSwipe(startX: Int, startY: Int, endX: Int, endY: Int) {
        // 作为两点轨迹调用新方法
        // 简单滑动默认持续 300ms
        val defaultDuration = 300L
        recordSwipeWithPath(
            listOf(
                Triple(startX, startY, 0L),
                Triple(endX, endY, defaultDuration)
            ),
            duration = defaultDuration
        )
    }

    /**
     * 记录长按操作
     * @param x 绝对X坐标（像素）
     * @param y 绝对Y坐标（像素）
     * @param duration 长按时长（毫秒）
     * @param targetPackage 目标应用包名（可选）
     */
    fun recordLongPress(x: Int, y: Int, duration: Long, targetPackage: String? = null) {
        if (!isRecording) return
        updateScreenSize()

        val now = SystemClock.elapsedRealtime()
        val action = RecordedAction.create(
            actionType = "longPress",
            params = mapOf(
                "x" to x,
                "y" to y,
                "duration" to duration
            ),
            timestamp = getElapsedRecordingMs(now),
            delayBefore = calculateAutoDelay(now),
            targetPackage = targetPackage
        )

        _recordedActions.value += action
        lastActionTime = now

        // 通知视觉反馈层
        feedbackCallback?.onActionRecorded(action, getDisplayActionCount())
    }

    /**
     * 记录文本输入操作
     * @param text 输入的文本
     */
    fun recordType(text: String) {
        if (!isRecording) return
        updateScreenSize()

        val now = SystemClock.elapsedRealtime()
        val action = RecordedAction.create(
            actionType = "type",
            params = mapOf("text" to text),
            timestamp = getElapsedRecordingMs(now),
            delayBefore = calculateAutoDelay(now)
        )

        _recordedActions.value += action
        lastActionTime = now
    }

    /**
     * 记录滚动操作
     * @param direction 滚动方向：up/down
     */
    fun recordScroll(direction: String) {
        if (!isRecording) return
        updateScreenSize()

        val now = SystemClock.elapsedRealtime()
        val action = RecordedAction.create(
            actionType = "scroll",
            params = mapOf("direction" to direction),
            timestamp = getElapsedRecordingMs(now),
            delayBefore = calculateAutoDelay(now)
        )

        _recordedActions.value += action
        lastActionTime = now
    }

    /**
     * 自动计算延迟时间
     * 根据操作间隔智能调整延迟，避免过短或过长的延迟
     *
     * @param currentTime 当前时间戳
     * @return 建议的延迟时间（毫秒）
     */
    private fun calculateAutoDelay(currentTime: Long): Long {
        val interval = currentTime - lastActionTime
        return when {
            interval < FAST_OPERATION_THRESHOLD -> MIN_DELAY_MS      // 快速操作，使用最小延迟
            interval > LONG_PAUSE_THRESHOLD -> MAX_DELAY_MS          // 长时间停顿，限制最大延迟
            else -> interval                                         // 保留真实间隔
        }
    }

    /**
     * 获取当前已录制的操作数量
     */
    fun getActionCount(): Int = _recordedActions.value.size

    fun getDisplayActionCount(): Int {
        return (_recordedActions.value.size - batchDisplayReduction).coerceAtLeast(0)
    }

    /**
     * 获取录制时长（毫秒）
     */
    @Suppress("DEPRECATION")
    fun getRecordingDuration(): Long {
        return if (isRecording) {
            val now = SystemClock.elapsedRealtime()
            getElapsedRecordingMs(now)
        } else {
            _recordedActions.value.lastOrNull()?.timestamp ?: 0
        }
    }

    fun markBatchMultiTapStart() {
        endBatchPause()
        lastActionTime = SystemClock.elapsedRealtime()
    }

    fun beginBatchPause() {
        if (batchPauseStartMs == null) {
            batchPauseStartMs = SystemClock.elapsedRealtime()
        }
    }

    fun endBatchPause() {
        val start = batchPauseStartMs ?: return
        val now = SystemClock.elapsedRealtime()
        val pauseDuration = now - start
        pausedDurationMs += pauseDuration
        lastActionTime += pauseDuration
        batchPauseStartMs = null
    }

    private fun getElapsedRecordingMs(now: Long): Long {
        val ongoingPause = batchPauseStartMs?.let { now - it } ?: 0L
        return (now - startTime - pausedDurationMs - ongoingPause).coerceAtLeast(0L)
    }

    /**
     * 辅助函数：从 Any? 安全转换为 Int
     * 处理 Gson 解析 JSON 时数字可能是 Double 的情况
     */
    private fun safeToInt(value: Any?): Int {
        return when (value) {
            is Int -> value
            is Double -> value.toInt()
            is Number -> value.toInt()
            else -> 0
        }
    }

    /**
     * 设置反馈回调（简化版，用于 RecordingService）
     */
    fun setFeedbackCallback(callback: (action: String, step: Int, x: Int, y: Int) -> Unit) {
        feedbackCallback = object : FeedbackCallback {
            override fun onActionRecorded(action: RecordedAction, stepNumber: Int) {
                // 从 action 中提取坐标
                val x = when (action.actionType) {
                    "tap", "longPress" -> safeToInt(action.params["x"])
                    "multiTap" -> {
                        val points = action.params["points"] as? List<*>
                        val first = points?.firstOrNull() as? Map<*, *>
                        safeToInt(first?.get("x"))
                    }
                    "swipe", "longPressSwipe" -> {
                        val path = action.params["path"] as? List<*>
                        val firstPoint = path?.firstOrNull() as? Map<*, *>
                        safeToInt(firstPoint?.get("x"))
                    }
                    else -> 0
                }
                val y = when (action.actionType) {
                    "tap", "longPress" -> safeToInt(action.params["y"])
                    "multiTap" -> {
                        val points = action.params["points"] as? List<*>
                        val first = points?.firstOrNull() as? Map<*, *>
                        safeToInt(first?.get("y"))
                    }
                    "swipe", "longPressSwipe" -> {
                        val path = action.params["path"] as? List<*>
                        val firstPoint = path?.firstOrNull() as? Map<*, *>
                        safeToInt(firstPoint?.get("y"))
                    }
                    else -> 0
                }

                android.util.Log.d("RecordingManager", "Feedback callback: step=$stepNumber")
                callback(action.actionType, stepNumber, x, y)
            }
        }
    }

    /**
     * 获取所有录制的操作
     */
    fun getRecordedActions(): List<RecordedAction> = _recordedActions.value
}
