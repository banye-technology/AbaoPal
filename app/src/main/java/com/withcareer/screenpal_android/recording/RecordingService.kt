package com.withcareer.screenpal_android.recording

import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import com.withcareer.screenpal_android.R
import com.withcareer.screenpal_android.service.ScreenPalAccessibilityService
import com.withcareer.screenpal_android.util.ToastUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

/**
 * 录制服务
 * 管理录制过程中的所有组件（overlay、feedback、floating controller）
 *
 * 核心设计：
 * - FloatingController 永不 detach/reattach（避免闪烁）
 * - 回放时只 detach overlay 和 feedback（如果需要）
 * - 使用优化的前台应用检测逻辑
 */
class RecordingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val TAG = "RecordingService"
        private const val REPLAY_CLICK_THRESHOLD = 20f
        private const val REPLAY_LONG_PRESS_THRESHOLD = 400L
        private const val MULTI_TAP_REPLAY_DELAY_MS = 120L

        // 广播 action
        const val ACTION_RECORDING_COMPLETED = "com.withcareer.screenpal_android.RECORDING_COMPLETED"
        const val ACTION_RECORDING_INSERT_COMPLETED = "com.withcareer.screenpal_android.RECORDING_INSERT_COMPLETED"
        private const val EXTRA_CAPTURE_ONLY = "recording_capture_only"
        private const val EXTRA_CAPTURE_AUTO_START = "recording_capture_auto_start"
        const val EXTRA_RECORDING_SOURCE = "recording_source" // 录制来源标识

        // 录制来源常量
        const val SOURCE_NORMAL = "normal" // 普通录制（旧UI）
        const val SOURCE_SKILL_EDITOR = "skill_editor" // 技能编辑页面录制

        // 单例，供 RecordingOverlayView 访问
        @Volatile
        var instance: RecordingService? = null

        // 录制结果（供 MainActivity 获取）
        @Volatile
        var lastRecordingResult: RecordingResult? = null

        // 录制来源标识
        @Volatile
        var recordingSource: String = SOURCE_NORMAL

        @Volatile
        private var isServiceRunning: Boolean = false

        /**
         * 启动录制服务
         * @param source 录制来源标识，默认为普通录制
         */
        fun start(context: android.content.Context, source: String = SOURCE_NORMAL) {
            recordingSource = source // 保存录制来源
            instance?.let {
                android.util.Log.d(TAG, "RecordingService already running, restarting recording UI")
                it.isCaptureOnly = false
                it.restartRecordingUi()
                return
            }
            val intent = Intent(context, RecordingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startInsert(context: android.content.Context) {
            instance?.let {
                android.util.Log.d(TAG, "RecordingService already running, restart recording UI for insert")
                it.isCaptureOnly = true
                it.isCaptureOnlyAutoStart = true
                it.restartRecordingUi()
                return
            }
            val intent = Intent(context, RecordingService::class.java).apply {
                putExtra(EXTRA_CAPTURE_ONLY, true)
                putExtra(EXTRA_CAPTURE_AUTO_START, true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 停止录制服务
         */
        fun stop(context: android.content.Context) {
            val intent = Intent(context, RecordingService::class.java)
            context.stopService(intent)
        }
    }

    // Binder
    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        fun getService(): RecordingService = this@RecordingService
    }

    // 组件
    private lateinit var windowManager: WindowManager
    private lateinit var recordingManager: RecordingManager
    private lateinit var floatingController: RecordingFloatingController
    private var overlayView: RecordingOverlayView? = null
    private var feedbackView: RecordingFeedbackView? = null

    // Overlay 布局参数（保存以便修改 flags）
    private lateinit var overlayParams: WindowManager.LayoutParams

    // 状态标志
    @Volatile
    var isReplaying = false
        private set

    @Volatile
    var isOverlayRecordingActive = false
        private set

    @Volatile
    var isBatchMultiTapEnabled = false
        private set

    private val pendingBatchMultiTaps = mutableListOf<List<Pair<Int, Int>>>()
    private var batchAnchorTimestampMs: Long? = null

    // 回放冷却期（避免回放后的触摸被误录）
    private var replayIgnoreUntilMs: Long = 0

    // 当前底层应用包名（实时更新）
    @Volatile
    private var currentUnderlyingApp: String? = null

    // Detach 状态标志
    private var overlayDetachedForReplay = false
    private var feedbackDetachedForReplay = false
    private var typingPassThroughJob: Job? = null
    @Volatile
    private var lastTextChangeTs: Long = 0L

    // 待显示的图标信息（回放完成后显示）
    private data class PendingFeedback(
        val actionType: String,
        val params: Map<String, Any>,
        val stepNumber: Int
    )
    // private var pendingFeedback: PendingFeedback? = null

    private var recordingScreenshotPath: String? = null
    @Volatile
    private var isCaptureOnly: Boolean = false
    @Volatile
    private var isCaptureOnlyAutoStart: Boolean = false
    @Volatile
    private var insertCompletionRequested: Boolean = false

    private fun safeToInt(value: Any?): Int {
        return when (value) {
            is Int -> value
            is Double -> value.toInt()
            is Number -> value.toInt()
            else -> 0
        }
    }

    private fun resolvePointValue(xValue: Any?, yValue: Any?): Pair<Int, Int>? {
        val x = (xValue as? Number)?.toFloat() ?: return null
        val y = (yValue as? Number)?.toFloat() ?: return null
        val width = recordingManager.screenWidth
        val height = recordingManager.screenHeight
        return if (x <= 1.5f && y <= 1.5f && width > 0 && height > 0) {
            (x * width).toInt() to (y * height).toInt()
        } else {
            x.toInt() to y.toInt()
        }
    }

    /**
     * 显示操作反馈图标
     */
    private fun showFeedbackAction(feedback: PendingFeedback) {
        val actionType = feedback.actionType
        val params = feedback.params
        val stepNumber = feedback.stepNumber

        if (actionType == "swipe" || actionType == "longPressSwipe") {
            val pathList = params["path"] as? List<*>
            val points = if (pathList != null) {
                pathList.mapNotNull { point ->
                    val map = point as? Map<*, *> ?: return@mapNotNull null
                    resolvePointValue(map["x"], map["y"])
                }
            } else {
                val start = resolvePointValue(params["startX"], params["startY"])
                val end = resolvePointValue(params["endX"], params["endY"])
                listOfNotNull(start, end)
            }

            if (points.size >= 2) {
                val start = points.first()
                val end = points.last()
                feedbackView?.showSwipePath(points)
                feedbackView?.showSwipeEndpoints(start, end, stepNumber)
            }
        } else if (actionType == "multiTap") {
            val points = params["points"] as? List<*>
            val parsed = points?.mapNotNull { point ->
                val map = point as? Map<*, *> ?: return@mapNotNull null
                resolvePointValue(map["x"], map["y"])
            } ?: emptyList()
            parsed.forEach { (x, y) ->
                feedbackView?.showFeedback(x, y, actionType, stepNumber)
            }
        } else {
            val point = resolvePointValue(params["x"], params["y"])
            if (point != null) {
                feedbackView?.showFeedback(point.first, point.second, actionType, stepNumber)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        isServiceRunning = true

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        recordingManager = RecordingManager(this)

        // 创建前台服务通知
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "recording_service_channel"
            val channelName = getString(R.string.recording_service_channel_name)
            val importance = android.app.NotificationManager.IMPORTANCE_LOW
            val channel = android.app.NotificationChannel(channelId, channelName, importance)
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)

            val notification = android.app.Notification.Builder(this, channelId)
                .setContentTitle(getString(R.string.recording_service_title))
                .setContentText(getString(R.string.recording_service_running))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()

            startForeground(1, notification)
        }

        Log.d(TAG, "RecordingService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called")
        isCaptureOnly = intent?.getBooleanExtra(EXTRA_CAPTURE_ONLY, false) == true
        isCaptureOnlyAutoStart = intent?.getBooleanExtra(EXTRA_CAPTURE_AUTO_START, false) == true
        // 自动开始录制
        startRecording()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopRecording()
        instance = null
        isServiceRunning = false
        Log.d(TAG, "RecordingService destroyed")
    }

    /**
     * 开始录制
     */
    fun startRecording() {
        Log.d(TAG, "========================================")
        Log.d(TAG, ">>> startRecording() called")
        recordingScreenshotPath = null
        insertCompletionRequested = false

        // 创建悬浮窗控制器
        floatingController = RecordingFloatingController(
            context = this,
            windowManager = windowManager,
            recordingManager = recordingManager,
            onStart = { startActualRecording() },
            onComplete = { completeRecording() },
            onCancel = { cancelRecording() },
            onBatchMultiTapChanged = { enabled ->
                setBatchMultiTapEnabled(enabled)
            }
        )

        // 显示悬浮窗（在用户点击"开始"前只显示准备UI）
        floatingController.show()

        if (isCaptureOnly && isCaptureOnlyAutoStart) {
            startActualRecording()
            floatingController.hide()
        }

        Log.d(TAG, "<<< startRecording() finished")
        Log.d(TAG, "========================================")
    }

    private fun restartRecordingUi() {
        if (::floatingController.isInitialized) {
            floatingController.hide()
        }
        if (overlayView != null || feedbackView != null) {
            cleanup()
        }
        startRecording()
    }

    /**
     * 实际开始录制（用户点击"开始"按钮后）
     */
    private fun startActualRecording() {
        Log.d(TAG, "========================================")
        Log.d(TAG, ">>> startActualRecording() called")

        // 先清理旧数据，避免录制中 UI 残留上次结果
        recordingManager.resetRecordingState()
        feedbackView?.clearAllIcons()

        val service = ScreenPalAccessibilityService.getInstance()
        if (service == null) {
            Log.e(TAG, "✗ AccessibilityService is not available")
            ToastUtils.show(this, getString(R.string.recording_accessibility_unavailable), Toast.LENGTH_LONG)
            // 关闭悬浮窗
            floatingController.hide()
            return
        }
        Log.d(TAG, "✓ AccessibilityService is available")

        // 记录当前底层应用（支持跨应用录制）
        updateUnderlyingApp()
        Log.d(TAG, "Initial underlying app detected=${currentUnderlyingApp != null}")

        // 先启动录制，避免截图耗时阻塞录制计时与触摸捕获
        setupOverlayAndStartRecording()

        // 截图并行执行，失败不影响录制流程
        serviceScope.launch(Dispatchers.Main) {
            val screenshotPath = captureScreenshotToCache()
            if (!screenshotPath.isNullOrBlank()) {
                recordingScreenshotPath = screenshotPath
                Log.d(TAG, "Recording screenshot cached")
            } else {
                Log.w(TAG, "Recording screenshot failed or empty, continue without screenshot")
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class) // 截图可能需要较长时间，保留 DelicateCoroutinesApi 标记如果使用了 GlobalScope，但这里我们用 serviceScope
    private suspend fun captureScreenshotToCache(): String? {
        val service = ScreenPalAccessibilityService.getInstance() ?: return null
        return try {
            val bitmap = service.takeScreenshotSuspend() ?: return null
            withContext(Dispatchers.IO) {
                val file = File(cacheDir, "recording_snapshot_${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                file.absolutePath
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture screenshot", e)
            null
        }
    }

    private fun setupOverlayAndStartRecording() {
        // 创建 overlay 布局参数
        Log.d(TAG, "Creating overlay parameters...")
        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        Log.d(TAG, "✓ Overlay parameters created (recording enabled)")

        // 创建 overlay view
        Log.d(TAG, "Creating RecordingOverlayView")
        overlayView = RecordingOverlayView(this, recordingManager)

        // 添加 overlay view
        Log.d(TAG, "Adding overlay view to window manager...")
        try {
            windowManager.addView(overlayView, overlayParams)
            Log.d(TAG, "✓ Overlay view added successfully")
        } catch (e: Exception) {
            Log.e(TAG, "✗ Failed to add overlay view", e)
            return
        }

        // 创建 feedback view
        Log.d(TAG, "Creating RecordingFeedbackView...")
        feedbackView = RecordingFeedbackView(this)

        // 添加 feedback view
        Log.d(TAG, "Adding feedback view to window manager...")
        try {
            windowManager.addView(feedbackView, WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            })
            Log.d(TAG, "✓ Feedback view added successfully")
        } catch (e: Exception) {
            Log.e(TAG, "✗ Failed to add feedback view", e)
        }

        // 连接悬浮窗位置回调
        Log.d(TAG, "Connecting floating controller position callback...")
        floatingController.setOnPositionChangedListener { rect ->
            overlayView?.floatingControllerRect = rect
            Log.d(TAG, "Floating controller position updated")
        }

        // 触发初始位置更新
        Log.d(TAG, "Triggering initial position update...")
        floatingController.updatePosition()

        // 设置反馈回调（立即显示图标）
        Log.d(TAG, "Setting feedback callback...")
        recordingManager.feedbackCallback = object : RecordingManager.FeedbackCallback {
            override fun onActionRecorded(action: RecordedAction, stepNumber: Int) {
                val actionType = action.actionType
                Log.d(TAG, "Action recorded: step=$stepNumber")

                // 立即清除旧图标并显示新图标
                feedbackView?.clearAllIcons()

                if (isCaptureOnly) {
                    val feedback = PendingFeedback(actionType, action.params, stepNumber)
                    showFeedbackAction(feedback)
                    if (!insertCompletionRequested && isCaptureOnlyAutoStart) {
                        insertCompletionRequested = true
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            completeRecording()
                        }
                    }
                } else {
                    // 立即显示图标，不再等待回放
                    val feedback = PendingFeedback(actionType, action.params, stepNumber)
                    showFeedbackAction(feedback)
                    Log.d(TAG, "✓ Feedback shown immediately")
                }
            }
        }
        Log.d(TAG, "✓ Feedback callback set")

        // 启动录制
        Log.d(TAG, "Starting RecordingManager...")
        recordingManager.startRecording()
        pendingBatchMultiTaps.clear()
        isOverlayRecordingActive = true
        Log.d(TAG, "✓ RecordingManager started")

        Log.d(TAG, "<<< startActualRecording() finished")
        Log.d(TAG, "========================================")
    }

    /**
     * 完成录制
     */
    private fun completeRecording() {
        Log.d(TAG, "Recording completed")
        setBatchMultiTapEnabled(false)

        val result = recordingManager.stopRecording()
        val resultWithScreenshot = result.copy(screenshotPath = recordingScreenshotPath)
        Log.d(TAG, "Total actions recorded: ${resultWithScreenshot.actions.size}")

        if (resultWithScreenshot.actions.isEmpty()) {
            lastRecordingResult = null
            cleanup()
            ToastUtils.show(this, getString(R.string.recording_no_actions_exit), Toast.LENGTH_SHORT)
            return
        }

        lastRecordingResult = resultWithScreenshot
        if (isCaptureOnly) {
            finishRecordingFlow(resultWithScreenshot, sendExplicit = true)
            return
        }
        val batchPoints = flushBatchMultiTapsIfNeeded()
        if (batchPoints.isNotEmpty()) {
            isReplaying = true
            serviceScope.launch(Dispatchers.Default) {
                replayMultiTapBatch(batchPoints)
                withContext(Dispatchers.Main) {
                    finishRecordingFlow(resultWithScreenshot, sendExplicit = true)
                }
            }
        } else {
            finishRecordingFlow(resultWithScreenshot, sendExplicit = true)
        }
    }

    /**
     * 取消录制
     */
    private fun cancelRecording() {
        Log.d(TAG, "Recording cancelled")
        setBatchMultiTapEnabled(false)

        // 清理
        recordingManager.resetRecordingState()
        pendingBatchMultiTaps.clear()
        batchAnchorTimestampMs = null
        recordingScreenshotPath = null
        // pendingFeedback = null
        feedbackView?.clearAllIcons()
        cleanup()
    }

    /**
     * 停止录制（外部调用）
     */
    fun stopRecording() {
        Log.d(TAG, "stopRecording() called")
        setBatchMultiTapEnabled(false)

        val result = recordingManager.stopRecording()
        val resultWithScreenshot = result.copy(screenshotPath = recordingScreenshotPath)
        lastRecordingResult = resultWithScreenshot

        if (isCaptureOnly) {
            finishRecordingFlow(resultWithScreenshot, sendExplicit = false)
            return
        }
        val batchPoints = flushBatchMultiTapsIfNeeded()
        if (batchPoints.isNotEmpty()) {
            isReplaying = true
            serviceScope.launch(Dispatchers.Default) {
                replayMultiTapBatch(batchPoints)
                withContext(Dispatchers.Main) {
                    finishRecordingFlow(resultWithScreenshot, sendExplicit = false)
                }
            }
        } else {
            finishRecordingFlow(resultWithScreenshot, sendExplicit = false)
        }
    }

    /**
     * 清理资源
     */
    private fun cleanup() {
        // 移除 overlay
        overlayView?.let {
            try {
                windowManager.removeView(it)
                Log.d(TAG, "Overlay view removed")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove overlay view", e)
            }
        }
        overlayView = null

        // 移除 feedback
        feedbackView?.let {
            try {
                windowManager.removeView(it)
                Log.d(TAG, "Feedback view removed")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove feedback view", e)
            }
        }
        feedbackView = null

        // 隐藏悬浮窗（不是移除，只是隐藏）
        if (::floatingController.isInitialized) {
            floatingController.hide()
        }

        isOverlayRecordingActive = false

        Log.d(TAG, "Cleanup completed")
    }

    /**
     * 手势完成回调（由 RecordingOverlayView 调用）
     */
    fun onGestureComplete(events: List<MotionEvent>) {
        Log.d(TAG, "========================================")
        Log.d(TAG, ">>> onGestureComplete() called in RecordingService")
        Log.d(TAG, "Events count: ${events.size}")

        if (events.isEmpty()) {
            Log.w(TAG, "No events to replay")
            return
        }
        if (isCaptureOnly) {
            Log.d(TAG, "Capture-only mode enabled, skipping replay")
            return
        }

        val first = events.first()
        val last = events.last()
        val distance = calculateDistance(first.rawX, first.rawY, last.rawX, last.rawY)
        val duration = last.eventTime - first.eventTime

        Log.d(TAG, "Gesture recorded")
        Log.d(TAG, "Duration: $duration ms")

        // Step 1: 设置回放标志
        Log.d(TAG, "Step 1: Setting replaying flag to true...")
        isReplaying = true

        // Step 2: 禁用蒙层（让触摸穿透）
        Log.d(TAG, "Step 2: Disabling mask (enabling touch pass-through)...")
        removeMask()

        // Step 3: 回放手势
        Log.d(TAG, "Step 3: Replaying gesture...")
        serviceScope.launch(Dispatchers.Default) {
            replayGesture(events)
        }

        Log.d(TAG, "<<< onGestureComplete() finished")
        Log.d(TAG, "========================================")
    }

    /**
     * 多点同时点击完成回调（由 RecordingOverlayView 调用）
     */
    fun onMultiTapComplete(points: List<Pair<Int, Int>>) {
        Log.d(TAG, "========================================")
        Log.d(TAG, ">>> onMultiTapComplete() called in RecordingService")
        Log.d(TAG, "MultiTap points count: ${points.size}")

        if (points.isEmpty()) {
            Log.w(TAG, "No points to replay")
            return
        }
        if (isCaptureOnly) {
            Log.d(TAG, "Capture-only mode enabled, skipping replay")
            return
        }

        if (isBatchMultiTapEnabled) {
            pendingBatchMultiTaps.add(points)
            Log.d(TAG, "Batch multiTap enabled, queued points (total=${pendingBatchMultiTaps.size})")
            val stepNumber = recordingManager.getDisplayActionCount() + 1
            points.forEach { (x, y) ->
                feedbackView?.showFeedback(x, y, "multiTap", stepNumber)
            }
            return
        }

        isReplaying = true
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            removeMask()
            replayMultiTap(points)
        }, MULTI_TAP_REPLAY_DELAY_MS)

        Log.d(TAG, "<<< onMultiTapComplete() finished")
        Log.d(TAG, "========================================")
    }

    private fun setBatchMultiTapEnabled(enabled: Boolean) {
        if (isBatchMultiTapEnabled == enabled) {
            return
        }
        isBatchMultiTapEnabled = enabled
        Log.d(TAG, "Batch multiTap enabled=$enabled")

        if (enabled) {
            batchAnchorTimestampMs = recordingManager.getRecordingDuration()
            recordingManager.beginBatchPause()
            pendingBatchMultiTaps.clear()
            feedbackView?.clearAllIcons()
            return
        }

        recordingManager.endBatchPause()
        val batchPoints = flushBatchMultiTapsIfNeeded()
        batchAnchorTimestampMs = null
        if (batchPoints.isNotEmpty()) {
            isReplaying = true
            serviceScope.launch(Dispatchers.Default) {
                replayMultiTapBatch(batchPoints)
            }
        }
    }

    private fun finishRecordingFlow(result: RecordingResult, sendExplicit: Boolean) {
        cleanup()

        // 普通录制完成，将 V2MainActivity 带回前台并打开录制编辑
        // 技能编辑器的录制通过广播和导航处理，不需要启动Activity
        if (!isCaptureOnly && recordingSource == SOURCE_NORMAL) {
            val mainIntent = Intent(this, com.withcareer.screenpal_android.ui_v2.V2MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra("openRecordingEdit", true)
            }
            startActivity(mainIntent)
            Log.d(TAG, "V2MainActivity started with edit flag")
        } else if (!isCaptureOnly && recordingSource == SOURCE_SKILL_EDITOR) {
            // 技能编辑器录制完成，需要将 V2MainActivity 带回前台
            val v2Intent = Intent(this, com.withcareer.screenpal_android.ui_v2.V2MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(v2Intent)
            Log.d(TAG, "V2MainActivity brought to front")
        } else if (recordingSource != SOURCE_NORMAL) {
            Log.d(TAG, "Skipping MainActivity start; using broadcast navigation")
        }

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val action = if (isCaptureOnly) ACTION_RECORDING_INSERT_COMPLETED else ACTION_RECORDING_COMPLETED
            val intent = Intent(action).apply {
                if (sendExplicit) {
                    setPackage(packageName)
                }
                putExtra(EXTRA_RECORDING_SOURCE, recordingSource) // 添加录制来源信息
            }
            sendBroadcast(intent)
            Log.d(TAG, "Recording completion broadcast sent: actionCount=${result.actions.size}")
            isCaptureOnly = false
            isCaptureOnlyAutoStart = false
            recordingSource = SOURCE_NORMAL // 重置为默认值
        }, 500)
    }

    private fun flushBatchMultiTapsIfNeeded(): List<Pair<Int, Int>> {
        if (pendingBatchMultiTaps.isEmpty()) {
            return emptyList()
        }
        recordingManager.endBatchPause()
        val batchPoints = pendingBatchMultiTaps.flatten()
        pendingBatchMultiTaps.clear()

        val targetPackage = getCurrentUnderlyingApp()
        val anchor = batchAnchorTimestampMs ?: recordingManager.getRecordingDuration()
        recordingManager.recordMultiTapBatch(listOf(batchPoints), targetPackage, anchor)
        return batchPoints
    }

    private suspend fun replayMultiTapBatch(batch: List<Pair<Int, Int>>) {
        if (batch.isEmpty()) return
        val service = ScreenPalAccessibilityService.getInstance() ?: return

        val windowPackage = service.getActiveAppPackageNameFromWindows()
        val lastKnownPackage = service.getLastKnownPackageName()
        val packageName = this@RecordingService.packageName

        val targetPackage = when {
            !windowPackage.isNullOrBlank() && windowPackage != packageName -> windowPackage
            !lastKnownPackage.isNullOrBlank() && lastKnownPackage != packageName -> lastKnownPackage
            else -> null
        }

        val shouldDetach = !targetPackage.isNullOrBlank() && targetPackage != packageName
        withContext(Dispatchers.Main) {
            feedbackView?.clearAllIcons()
            if (shouldDetach) {
                overlayView?.let {
                    try {
                        windowManager.removeView(it)
                        overlayDetachedForReplay = true
                        Log.d(TAG, "[Coroutine] Overlay detached for multiTap batch")
                    } catch (e: Exception) {
                        Log.e(TAG, "[Coroutine] Failed to detach overlay for multiTap batch", e)
                    }
                }
            } else {
                removeMask()
            }
        }

        delay(if (overlayDetachedForReplay) 120 else 60)
        val floatPoints = batch.map { it.first.toFloat() to it.second.toFloat() }
        service.multiTap(floatPoints)

        withContext(Dispatchers.Main) {
            if (overlayDetachedForReplay) {
                overlayView?.let { view ->
                    try {
                        windowManager.addView(view, overlayParams)
                        addMask()
                        overlayDetachedForReplay = false
                        Log.d(TAG, "[Coroutine] Overlay re-attached for multiTap batch")
                    } catch (e: Exception) {
                        Log.e(TAG, "[Coroutine] Failed to re-attach overlay for multiTap batch", e)
                    }
                }
            } else {
                addMask()
            }
            overlayView?.resetFloatingTouchState()
            isReplaying = false
            replayIgnoreUntilMs = SystemClock.uptimeMillis() + 150
        }
    }

    /**
     * 禁用蒙层（让触摸穿透）
     */
    private fun removeMask() {
        Log.d(TAG, "removeMask() called - disabling touch interception")

        overlayView?.let {
            try {
                // 添加 FLAG_NOT_TOUCHABLE 让触摸穿透
                overlayParams.flags = overlayParams.flags or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

                windowManager.updateViewLayout(it, overlayParams)
                Log.d(TAG, "✓ Mask disabled (touch pass-through enabled)")
            } catch (e: Exception) {
                Log.e(TAG, "✗ Failed to disable mask", e)
            }
        } ?: run {
            Log.w(TAG, "overlayView is null, nothing to disable")
        }
    }

    /**
     * 启用蒙层（恢复触摸拦截）
     */
    private fun addMask() {
        Log.d(TAG, "addMask() called - enabling touch interception")

        overlayView?.let {
            try {
                // 移除 FLAG_NOT_TOUCHABLE 恢复触摸拦截
                overlayParams.flags = overlayParams.flags and
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()

                windowManager.updateViewLayout(it, overlayParams)
                Log.d(TAG, "✓ Mask enabled (touch interception restored)")
            } catch (e: Exception) {
                Log.e(TAG, "✗ Failed to enable mask", e)
            }
        } ?: run {
            Log.w(TAG, "overlayView is null, cannot enable mask")
        }
    }

    fun onTextChangedDuringRecording() {
        lastTextChangeTs = SystemClock.uptimeMillis()
    }

    private fun keepPassThroughWhileTyping(service: ScreenPalAccessibilityService) {
        typingPassThroughJob?.cancel()
        typingPassThroughJob = serviceScope.launch(Dispatchers.Default) {
            withContext(Dispatchers.Main) {
                removeMask()
            }
            val minPassThroughMs = 200L
            val idleStopMs = 300L
            val maxPassThroughMs = 8000L
            val startMs = SystemClock.uptimeMillis()
            lastTextChangeTs = startMs

            var stableHiddenCount = 0
            while (true) {
                val elapsed = SystemClock.uptimeMillis() - startMs
                if (elapsed >= maxPassThroughMs) {
                    Log.d(TAG, "Pass-through timeout reached, restoring mask")
                    break
                }
                val idleFor = SystemClock.uptimeMillis() - lastTextChangeTs
                if (elapsed >= minPassThroughMs && idleFor >= idleStopMs) {
                    Log.d(TAG, "Text input idle, restoring mask")
                    break
                }
                stableHiddenCount += 1
                delay(200)
            }
            withContext(Dispatchers.Main) {
                addMask()
            }
        }
    }

    /**
     * 悬浮窗区域触摸透传：临时禁用蒙层拦截
     */
    fun temporarilyDisableOverlayForFloating() {
        Log.d(TAG, "temporarilyDisableOverlayForFloating() called")
        removeMask()
    }

    /**
     * 悬浮窗区域触摸透传结束：恢复蒙层拦截
     */
    fun restoreOverlayAfterFloating() {
        Log.d(TAG, "restoreOverlayAfterFloating() called")
        addMask()
    }

    fun dispatchTouchToFloating(event: MotionEvent): Boolean {
        return if (::floatingController.isInitialized) {
            floatingController.dispatchTouchEvent(event)
        } else {
            false
        }
    }

    /**
     * 更新底层应用包名（在用户操作前调用）
     */
    fun updateUnderlyingApp() {
        val service = ScreenPalAccessibilityService.getInstance() ?: return

        // 获取非自己的前台应用
        var underlyingApp = service.getActiveAppPackageNameFromWindows()

        // 如果窗口检测失败，使用上次已知的应用
        if (underlyingApp.isNullOrBlank() || underlyingApp == packageName) {
            val lastKnown = service.getLastKnownPackageName()
            if (!lastKnown.isNullOrBlank() && lastKnown != packageName) {
                underlyingApp = lastKnown
            }
        }

        // 过滤掉桌面应用（除非没有其他选择）
        if (underlyingApp == "com.miui.home" || underlyingApp == "com.android.launcher3") {
            // 桌面也是有效的录制目标
            currentUnderlyingApp = underlyingApp
        } else if (underlyingApp != packageName) {
            currentUnderlyingApp = underlyingApp
        }

        Log.d(TAG, "Underlying app updated")
    }

    /**
     * 获取当前底层应用包名
     */
    fun getCurrentUnderlyingApp(): String? = currentUnderlyingApp

    /**
     * 回放手势序列
     *
     * 核心设计：
     * - 只 detach overlay 和 feedback（如果需要跨应用）
     * - FloatingController 永不 detach（避免闪烁）
     */
    private fun replayGesture(events: List<MotionEvent>) {
        Log.d(TAG, "replayGesture() called with ${events.size} events")

        val service = ScreenPalAccessibilityService.getInstance()
        if (service == null) {
            Log.e(TAG, "✗ AccessibilityService not available")
            // 恢复状态，避免蒙层长期不接收触摸
            serviceScope.launch(Dispatchers.Main) {
                addMask()
                overlayView?.resetFloatingTouchState()
                isReplaying = false
                replayIgnoreUntilMs = SystemClock.uptimeMillis() + 150
                Log.d(TAG, "Reset replaying flag to false (service null)")
            }
            return
        }
        Log.d(TAG, "✓ AccessibilityService is available")

        val first = events.first()
        val last = events.last()
        val distance = calculateDistance(first.rawX, first.rawY, last.rawX, last.rawY)
        val duration = last.eventTime - first.eventTime

        Log.d(TAG, "Launching coroutine to replay gesture...")

        serviceScope.launch(Dispatchers.Default) {
            try {
                Log.d(TAG, "[Coroutine] Gesture duration: $duration ms")

                // 获取目标应用包名（在用户触摸前的前台应用）
                // 优先使用窗口检测,因为它更准确
                val windowPackage = service.getActiveAppPackageNameFromWindows()
                val lastKnownPackage = service.getLastKnownPackageName()
                val packageName = this@RecordingService.packageName

                // 确定目标应用：优先使用非本应用的包名
                val targetPackage = when {
                    !windowPackage.isNullOrBlank() && windowPackage != packageName -> windowPackage
                    !lastKnownPackage.isNullOrBlank() && lastKnownPackage != packageName -> lastKnownPackage
                    else -> null
                }

                Log.d(TAG, "[Coroutine] Target app selected for replay")

                val isTapGesture =
                    distance < REPLAY_CLICK_THRESHOLD && duration < REPLAY_LONG_PRESS_THRESHOLD
                val shouldDetachForTap =
                    isTapGesture && !targetPackage.isNullOrBlank() && targetPackage != packageName

                // 临时移除/透传 overlay，让手势能直接作用到底层应用
                Log.d(TAG, "[Coroutine] Preparing overlay for replay (tap=$isTapGesture, detachTap=$shouldDetachForTap)")
                withContext(Dispatchers.Main) {
                    if (isTapGesture && !shouldDetachForTap) {
                        // 点击不 detach，避免长时间阻塞
                        removeMask()
                    } else {
                        // 移除 overlay（保留引用，稍后重新添加）
                        overlayView?.let {
                            try {
                                windowManager.removeView(it)
                                overlayDetachedForReplay = true
                                Log.d(TAG, "[Coroutine] Overlay removed for replay")
                            } catch (e: Exception) {
                                Log.e(TAG, "[Coroutine] Failed to remove overlay", e)
                            }
                        }
                    }
                }

                // 等待状态生效和系统稳定
                // 使用 removeView 需要稍长的等待时间以确保 WindowManager 完成更新
                delay(if (overlayDetachedForReplay) 80 else 20)

                // 执行回放
                when {
                    distance < REPLAY_CLICK_THRESHOLD && duration >= REPLAY_LONG_PRESS_THRESHOLD -> {
                        Log.d(TAG, "[Coroutine] Replaying long press")
                        service.longPress(first.rawX, first.rawY, duration)
                    }
                    distance < REPLAY_CLICK_THRESHOLD -> {
                        Log.d(TAG, "[Coroutine] Replaying tap")
                        service.tap(first.rawX, first.rawY)
                    }
                    else -> {
                        Log.d(TAG, "[Coroutine] Replaying swipe with ${events.size} points (timed)")
                        val startTime = events.first().eventTime
                        val timedPathPoints = events.map {
                            Triple(it.rawX, it.rawY, it.eventTime - startTime)
                        }
                        service.swipeWithTimedPath(timedPathPoints)
                    }
                }

                // 等待应用响应
                Log.d(TAG, "[Coroutine] Waiting 50ms for app to respond...")
                delay(50)

                Log.d(TAG, "[Coroutine] Checking for typing context (timeout 50ms)...")
                val shouldKeepPassThroughForTyping = if (distance < REPLAY_CLICK_THRESHOLD && duration < REPLAY_LONG_PRESS_THRESHOLD) {
                    try {
                        // 优先检查 IME，速度快
                        if (service.isImeVisible()) {
                            true
                        } else {
                            // 节点查找耗时较长，严格限制超时时间
                            withContext(Dispatchers.Default) {
                                try {
                                    kotlinx.coroutines.withTimeout(50) {
                                        service.isEditableNodeAt(first.rawX, first.rawY)
                                    }
                                } catch (e: Exception) {
                                    false // 超时或错误视为非输入框
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "[Coroutine] Check for typing context failed", e)
                        false
                    }
                } else {
                    false
                }
                Log.d(TAG, "[Coroutine] Typing context check result: $shouldKeepPassThroughForTyping")

                // 重新添加 overlay 并恢复状态
                Log.d(TAG, "[Coroutine] Restoring overlay...")
                withContext(Dispatchers.Main) {
                    // 重新添加 overlay
                    if (overlayDetachedForReplay) {
                        overlayView?.let { view ->
                            try {
                                windowManager.addView(view, overlayParams)

                                if (shouldKeepPassThroughForTyping) {
                                    Log.d(TAG, "[Coroutine] Keep pass-through while typing")
                                    keepPassThroughWhileTyping(service)
                                } else {
                                    addMask()
                                }
                                overlayDetachedForReplay = false
                                Log.d(TAG, "[Coroutine] Overlay re-added")
                            } catch (e: Exception) {
                                Log.e(TAG, "[Coroutine] Failed to re-add overlay", e)
                            }
                        }
                    } else {
                        // 如果没有 detach，则恢复 mask
                        if (shouldKeepPassThroughForTyping) {
                            Log.d(TAG, "[Coroutine] Keep pass-through while typing (no detach)")
                            keepPassThroughWhileTyping(service)
                        } else {
                            addMask()
                        }
                    }

                    // 重置悬浮窗触摸状态
                    overlayView?.resetFloatingTouchState()

                    // 重置回放标志
                    isReplaying = false
                    replayIgnoreUntilMs = SystemClock.uptimeMillis() + 150
                    Log.d(TAG, "[Coroutine] Reset replaying flag to false")
                }

                Log.d(TAG, "[Coroutine] Replay complete")

            } catch (e: Exception) {
                Log.e(TAG, "[Coroutine] ✗ Exception while replaying gesture", e)

                // 异常情况下也要恢复状态
                withContext(Dispatchers.Main) {
                    if (overlayDetachedForReplay) {
                        overlayView?.let { view ->
                            try {
                                windowManager.addView(view, overlayParams)
                                addMask()
                                Log.d(TAG, "[Coroutine] Overlay re-attached after exception")
                            } catch (re: Exception) {
                                Log.e(TAG, "[Coroutine] Failed to re-attach overlay after exception", re)
                            }
                        }
                        overlayDetachedForReplay = false
                    }

                    if (feedbackDetachedForReplay) {
                        feedbackView?.let { view ->
                            try {
                                windowManager.addView(view, WindowManager.LayoutParams(
                                    WindowManager.LayoutParams.MATCH_PARENT,
                                    WindowManager.LayoutParams.MATCH_PARENT,
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                    else
                                        @Suppress("DEPRECATION")
                                        WindowManager.LayoutParams.TYPE_PHONE,
                                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                                    PixelFormat.TRANSLUCENT
                                ).apply {
                                    gravity = Gravity.TOP or Gravity.START
                                    x = 0
                                    y = 0
                                })
                                Log.d(TAG, "[Coroutine] Feedback view re-attached after exception")
                            } catch (re: Exception) {
                                Log.e(TAG, "[Coroutine] Failed to re-attach feedback view after exception", re)
                            }
                        }
                        feedbackDetachedForReplay = false
                    }

                    addMask()
                    overlayView?.resetFloatingTouchState()
                    isReplaying = false
                    replayIgnoreUntilMs = SystemClock.uptimeMillis() + 150
                    Log.d(TAG, "[Coroutine] Reset replaying flag to false after exception")
                }
            }
        }
    }

    private fun replayMultiTap(points: List<Pair<Int, Int>>) {
        val service = ScreenPalAccessibilityService.getInstance()
        if (service == null) {
            Log.e(TAG, "✗ AccessibilityService not available")
            serviceScope.launch(Dispatchers.Main) {
                addMask()
                overlayView?.resetFloatingTouchState()
                isReplaying = false
                replayIgnoreUntilMs = SystemClock.uptimeMillis() + 150
            }
            return
        }

        serviceScope.launch(Dispatchers.Default) {
            try {
                val windowPackage = service.getActiveAppPackageNameFromWindows()
                val lastKnownPackage = service.getLastKnownPackageName()
                val packageName = this@RecordingService.packageName

                val targetPackage = when {
                    !windowPackage.isNullOrBlank() && windowPackage != packageName -> windowPackage
                    !lastKnownPackage.isNullOrBlank() && lastKnownPackage != packageName -> lastKnownPackage
                    else -> null
                }

                val shouldDetach = !targetPackage.isNullOrBlank() && targetPackage != packageName
                withContext(Dispatchers.Main) {
                    if (shouldDetach) {
                        overlayView?.let {
                            try {
                                windowManager.removeView(it)
                                overlayDetachedForReplay = true
                                Log.d(TAG, "[Coroutine] Overlay detached for multiTap")
                            } catch (e: Exception) {
                                Log.e(TAG, "[Coroutine] Failed to detach overlay for multiTap", e)
                            }
                        }
                    } else {
                        removeMask()
                    }
                }

                delay(if (overlayDetachedForReplay) 100 else 40)
                val floatPoints = points.map { it.first.toFloat() to it.second.toFloat() }
                service.multiTap(floatPoints)
                delay(100)

                withContext(Dispatchers.Main) {
                    if (overlayDetachedForReplay) {
                        overlayView?.let { view ->
                            try {
                                windowManager.addView(view, overlayParams)
                                addMask()
                                overlayDetachedForReplay = false
                                Log.d(TAG, "[Coroutine] Overlay re-attached for multiTap")
                            } catch (e: Exception) {
                                Log.e(TAG, "[Coroutine] Failed to re-attach overlay for multiTap", e)
                            }
                        }
                    } else {
                        addMask()
                    }

                    overlayView?.resetFloatingTouchState()
                    isReplaying = false
                    replayIgnoreUntilMs = SystemClock.uptimeMillis() + 150
                }
            } catch (e: Exception) {
                Log.e(TAG, "[Coroutine] ✗ Exception while replaying multiTap", e)
                withContext(Dispatchers.Main) {
                    if (overlayDetachedForReplay) {
                        overlayView?.let { view ->
                            try {
                                windowManager.addView(view, overlayParams)
                                addMask()
                            } catch (_: Exception) { }
                        }
                        overlayDetachedForReplay = false
                    }
                    addMask()
                    overlayView?.resetFloatingTouchState()
                    isReplaying = false
                    replayIgnoreUntilMs = SystemClock.uptimeMillis() + 150
                }
            }
        }
    }

    /**
     * 判断是否应该忽略触摸（回放期间或冷却期）
     */
    fun shouldIgnoreTouch(currentTimeMs: Long): Boolean {
        return isReplaying || currentTimeMs < replayIgnoreUntilMs
    }

    /**
     * 配置变化（如屏幕旋转）
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        Log.d(TAG, "Configuration changed")

        // 更新悬浮窗位置
        if (::floatingController.isInitialized) {
            floatingController.updatePosition()
        }
    }

    /**
     * 计算两点之间的距离
     */
    private fun calculateDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }
}
