package com.withcareer.screenpal_android.overlay

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.withcareer.screenpal_android.ScreenPalApplication
import com.withcareer.screenpal_android.R
import com.withcareer.screenpal_android.data.preference.PreferencesRepository
import com.withcareer.screenpal_android.core.model.ChatUiState
import com.withcareer.screenpal_android.util.AccessibilityServiceHelper
import com.withcareer.screenpal_android.core.tts.TtsManager
import com.withcareer.screenpal_android.core.voice.VoiceController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import com.withcareer.screenpal_android.util.ToastUtils
import android.widget.Toast

import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import com.withcareer.screenpal_android.util.DeviceUtils
import com.withcareer.screenpal_android.util.OperationGuard

import com.withcareer.screenpal_android.util.OverlayViewUtils
import com.withcareer.screenpal_android.overlay.state.FloatingStateManager
import com.withcareer.screenpal_android.overlay.state.OverlayState

class FloatingAssistantService : Service() {
    companion object {
        private const val ACTION_START = "com.withcareer.screenpal_android.overlay.action.START"
        private const val ACTION_STOP = "com.withcareer.screenpal_android.overlay.action.STOP"
        private const val ACTION_RUN_INSTRUCTION = "ACTION_RUN_INSTRUCTION"

        private const val NOTIFICATION_CHANNEL_ID = "floating_assistant"
        private const val NOTIFICATION_ID = 1001

        private var instance: java.lang.ref.WeakReference<FloatingAssistantService>? = null

        // Static bridge methods
        fun setActionMode(active: Boolean) {
            val service = instance?.get() ?: return
            Handler(Looper.getMainLooper()).post {
                service.stateManager.setActionMode(active)
            }
        }

        fun setScreenshotMode(active: Boolean, onAnimationEnd: (() -> Unit)? = null) {
            val service = instance?.get()
            if (service == null) {
                // FloatingAssistantService 未运行，直接调用回调
                Log.w("FloatingAssistantService", "setScreenshotMode: service not running, invoking callback directly")
                onAnimationEnd?.invoke()
                return
            }
            Handler(Looper.getMainLooper()).post {
                service.stateManager.setScreenshotMode(active)
                // Since state manager is reactive, we might need to wait for UI update?
                // Or just assume the state update triggers the fade out immediately.
                // For now, let's keep the delay logic or callback simple.
                // In reactive world, "onAnimationEnd" is tricky.
                // We'll let the service handle the transition and call the callback.
                // But `setScreenshotMode` in StateManager doesn't take a callback.
                // We'll handle the callback manually here for backward compatibility.

                // Hack: Wait a bit for fade out if active=true
                if (active) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        onAnimationEnd?.invoke()
                    }, 150) // 150ms > fade out duration
                } else {
                    onAnimationEnd?.invoke()
                }
            }
        }


        fun setTaskListVisible(visible: Boolean) {
            val service = instance?.get() ?: return
            Handler(Looper.getMainLooper()).post {
                service.stateManager.setTaskListVisible(visible)
            }
        }

        fun showInteraction(message: String, choices: List<String> = emptyList(), onResult: (String?) -> Unit) {
            val service = instance?.get() ?: return
            Handler(Looper.getMainLooper()).post {
                service.showInteractionInternal(message, choices, onResult)
            }
        }

        fun showClickEffect(x: Float, y: Float) {
            val service = instance?.get() ?: return
            Handler(Looper.getMainLooper()).post {
                service.showClickEffectInternal(x, y)
            }
        }

        fun showSwipeEffect(startX: Float, startY: Float, endX: Float, endY: Float) {
            val service = instance?.get() ?: return
            Handler(Looper.getMainLooper()).post {
                service.showSwipeEffectInternal(startX, startY, endX, endY)
            }
        }

        fun showBoundsCenter(left: Int, top: Int, right: Int, bottom: Int) {
            val cx = (left + right) / 2f
            val cy = (top + bottom) / 2f
            showClickEffect(cx, cy)
        }

        fun start(context: Context) {
            val intent = Intent(context, FloatingAssistantService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingAssistantService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var windowManager: WindowManager? = null

    private lateinit var bubbleManager: FloatingBubbleManager
    private lateinit var stepsManager: FloatingStepsManager
    private lateinit var panelManager: FloatingPanelManager
    private lateinit var interactionManager: FloatingInteractionManager
    private lateinit var effectManager: FloatingEffectManager
    private lateinit var trashManager: FloatingTrashManager

    // State Manager
    private lateinit var stateManager: FloatingStateManager

    private var ttsEnabled: Boolean = false
    private var lastSpokenMessage: String? = null
    private var lastSpokenError: String? = null
    private var lastInteractiveMessageId: String? = null

    private var ttsManager: TtsManager? = null

    private val preferencesRepository by lazy {
        PreferencesRepository(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private val operationGuard by lazy {
       OperationGuard(applicationContext)
    }

    private val wakeupListener = fun(): Boolean {
        DeviceUtils.wakeUpScreen(applicationContext)

        scope.launch {
            if (!operationGuard.check(requireApiKey = false, requireRecordAudio = true, requireAccessibility = true, requireOverlay = true)) {
                VoiceController.getInstance(this@FloatingAssistantService).restartWakeup()
                return@launch
            }

            // Check State via StateManager's exposed state (indirectly)
            // Or just check panel manager directly
            if (!panelManager.isVisible()) {
                val startRecognition = {
                    scope.launch(Dispatchers.Main) {
                        kotlinx.coroutines.delay(200)
                        panelManager.startVoiceRecognition()
                    }
                }

                if (ttsEnabled) {
                    ttsManager?.speakWithFirstFrameCallback(
                        text = getString(R.string.i_am_here),
                        onFirstFrame = {
                            scope.launch(Dispatchers.Main) {
                                stateManager.togglePanel() // Ensure open
                                // Ideally, togglePanel should force open if closed.
                                // We might need a generic `openPanel()` in StateManager
                                if (!panelManager.isVisible()) {
                                     stateManager.togglePanel()
                                } else {
                                     panelManager.resetInput()
                                }
                            }
                        },
                        onDone = {
                            startRecognition()
                        }
                    )
                } else {
                    if (!panelManager.isVisible()) {
                        stateManager.togglePanel()
                    } else {
                        panelManager.resetInput()
                    }
                    startRecognition()
                }
            } else {
                if (ttsEnabled) {
                    ttsManager?.speak(getString(R.string.i_am_here))
                }
                VoiceController.getInstance(this@FloatingAssistantService).restartWakeup()
            }
        }
        return true
    }

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("FloatingAssistant", "onCreate: Service created")
        instance = java.lang.ref.WeakReference(this)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        stateManager = FloatingStateManager(scope, preferencesRepository)

        bubbleManager = FloatingBubbleManager(this, windowManager!!, scope, preferencesRepository) {
            // On Bubble Tap
            if (stateManager.state.value.isTaskRunning) {
                requestStopTask()
            } else {
                stateManager.togglePanel()
            }
        }

        bubbleManager.dragListener = object : FloatingBubbleManager.OnDragListener {
            override fun onDragStart() {
                trashManager.show()
            }

            override fun onDrag(rawX: Float, rawY: Float) {
                trashManager.updateHoverState(rawX, rawY)
            }

            override fun onDragEnd(rawX: Float, rawY: Float) {
                if (trashManager.updateHoverState(rawX, rawY)) {
                    // 拖拽到了垃圾桶，关闭服务
                    scope.launch {
                        try {
                            preferencesRepository.saveFloatingAssistantEnabled(false)
                        } catch (_: Exception) {
                            Log.w("FloatingAssistant", "Failed to persist disabled state")
                        } finally {
                            stopSelf()
                        }
                    }
                }
                trashManager.hide()
            }
        }

        stepsManager = FloatingStepsManager(this, windowManager!!) {
            requestStopTask()
        }
        interactionManager = FloatingInteractionManager(this, windowManager!!)
        effectManager = FloatingEffectManager(this, windowManager!!)
        trashManager = FloatingTrashManager(this, windowManager!!)

        panelManager = FloatingPanelManager(this, windowManager!!, scope, preferencesRepository, object : FloatingPanelManager.PanelCallbacks {
            override fun onStartTask(text: String) {
                if (!AccessibilityServiceHelper.isServiceConnected.value) {
                    ToastUtils.show(this@FloatingAssistantService, getString(R.string.please_enable_accessibility), Toast.LENGTH_LONG)
                    AccessibilityServiceHelper.goToAccessibilitySettings(this@FloatingAssistantService)
                    return
                }

                val runtime = (application as ScreenPalApplication).agentRuntime
                scope.launch {
                    runtime.startTask(text)
                }
                // Keep panel visible during task execution
                stateManager.setTaskStarted()
            }

            override fun onStopTask() {}

            override fun onMinimize() {
                stateManager.closePanel()
            }

            override fun onCloseService() {
                stopSelf()
            }

            override fun onPanelVisibilityChanged(visible: Boolean) {
                if (visible) {
                    bubbleManager.cancelBubbleHide()
                } else {
                    // Logic moved to StateManager / Render
                    VoiceController.getInstance(this@FloatingAssistantService).stopListening()
                    VoiceController.getInstance(this@FloatingAssistantService).restartWakeup()
                }
            }

            override fun onFocusChange(hasFocus: Boolean) {}
        })

        ttsManager = TtsManager.getInstance(this)

        VoiceController.getInstance(this).addWakeupListener(wakeupListener)

        // Start observing state
        observeState()
        bindRuntime()
        bindTtsEnabled()
        bindFloatingBallSize()
        bindFloatingBallPosition()
    }

    private fun observeState() {
        scope.launch {
            stateManager.state.collectLatest { state ->
                renderState(state)
            }
        }
    }

    private fun renderState(state: OverlayState) {
        android.util.Log.d("FloatingAssistant", "renderState: panel=${state.isPanelVisible}, steps=${state.isStepsVisible}, bubble=${state.isBubbleVisible}, taskRunning=${state.isTaskRunning}")

        // 【关键修复】先同步状态，确保后面的 setVisible 等方法能看到最新的 taskRunning 状态
        bubbleManager.isTaskRunning = state.isTaskRunning

        // Render Bubble
        if (state.isBubbleVisible) {
            if (!bubbleManager.isVisible()) {
                bubbleManager.addBubble()
            }
            bubbleManager.setVisible(true)
            bubbleManager.updateIconState(state.isTaskRunning)

            if (state.isBreathing) {
                // bubbleManager.startBreathing() // Not exposed publicly, handled internally by setVisible(true) + updateIconState?
                // Currently bubbleManager starts breathing automatically if not running task?
                // Actually `updateIconState(false)` calls `stopBreathing()`.
                // We might need to expose `startBreathing` if we want fine control.
                // For now, assume default behavior is acceptable or add method if needed.
            }

            if (state.shouldHideBubbleDelayed) {
                bubbleManager.scheduleBubbleHide()
            } else {
                bubbleManager.cancelBubbleHide()
            }

            // Handle Alpha (Screenshot mode)
            bubbleManager.bubbleView?.alpha = state.bubbleAlpha

        } else {
            bubbleManager.setVisible(false)
        }

        // Render Panel
        if (state.isPanelVisible) {
            android.util.Log.d("FloatingAssistant", "renderState: showing panel, panelView=${panelManager.panelView != null}")
            panelManager.addPanel()
            panelManager.setVisible(true)
            panelManager.panelView?.alpha = state.panelAlpha
        } else {
            android.util.Log.d("FloatingAssistant", "renderState: hiding panel")
            panelManager.setVisible(false)
        }

        // Render Steps
        if (state.isStepsVisible) {
            if (!stepsManager.isVisible()) {
                stepsManager.addStepsDialog()
            }
            stepsManager.setVisible(true)
            // Note: Alpha is handled by setVisible() for both textContainerView and stopButtonView
        } else {
            stepsManager.setVisible(false)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("FloatingAssistant", "onStartCommand received")
        when (intent?.action) {
            ACTION_STOP -> {
                android.util.Log.d("FloatingAssistant", "onStartCommand: Stopping service")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RUN_INSTRUCTION -> {
                val instructionId = intent.getStringExtra("INSTRUCTION_ID")
                val isPreview = intent.getBooleanExtra("IS_PREVIEW", false)
                if (!instructionId.isNullOrBlank()) {
                     runInstruction(instructionId, isPreview)
                }
                return START_STICKY
            }
            ACTION_START, null -> {
                val canDraw = Settings.canDrawOverlays(this)
                if (!canDraw) {
                    ToastUtils.show(this, getString(R.string.floating_permission_request), Toast.LENGTH_LONG)
                    val permissionIntent = Intent(this, com.withcareer.screenpal_android.ui_v2.V2MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(permissionIntent)
                    stopSelf()
                    return START_NOT_STICKY
                }

                try {
                    startForegroundInternal()
                } catch (e: Exception) {
                    android.util.Log.e("FloatingAssistant", "startForegroundInternal failed", e)
                    stopSelf()
                    return START_NOT_STICKY
                }
                return START_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }


    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        try {
            bubbleManager.updateConfiguration(newConfig)

            // 移除旧视图并由 renderState 统一处理重建
            try { panelManager.removePanel() } catch (e: Exception) {}
            try { stepsManager.removeStepsDialog() } catch (e: Exception) {}
            try { interactionManager.removeInteractionView() } catch (e: Exception) {}

            // 使用当前状态重新渲染 UI，renderState 会根据逻辑决定是否重新 addPanel 等
            renderState(stateManager.state.value)

        } catch (e: Exception) {
            android.util.Log.e("FloatingAssistant", "Error handling configuration change", e)
        }
    }

    override fun onDestroy() {
        VoiceController.getInstance(this).removeWakeupListener(wakeupListener)
        VoiceController.getInstance(this).stopListening()

        // 不要 shutdown TtsManager，因为它是全局单例
        // ttsManager?.shutdown()
        panelManager.destroy()
        trashManager.hide()

        bubbleManager.bubbleView?.animate()?.cancel()
        panelManager.panelView?.animate()?.cancel()
        // Note: stepsManager views are handled by removeStepsDialog() which removes them completely

        try {
            removeOverlayViews()
        } catch (e: Exception) {
            android.util.Log.e("FloatingAssistant", "Error removing overlays", e)
        }
        scope.cancel()
        instance = null
        super.onDestroy()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun startForegroundInternal() {
        createNotificationChannelIfNeeded()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val activityIntent = Intent(this, com.withcareer.screenpal_android.ui_v2.V2MainActivity::class.java)
        val activityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, FloatingAssistantService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.floating_service_running))
            .setContentText(getString(R.string.floating_notification_content))
            .setContentIntent(activityPendingIntent)
            .setOngoing(true)
            .addAction(NotificationCompat.Action(0, getString(R.string.close), stopPendingIntent))
            .build()
    }

    private fun createNotificationChannelIfNeeded() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.floating_assistant),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun removeOverlayViews() {
        bubbleManager.removeBubble()
        panelManager.removePanel()
        interactionManager.removeInteractionView()
        stepsManager.removeStepsDialog()
        trashManager.hide()
    }

    private fun requestStopTask() {
        interactionManager.showStopDialog {
            val runtime = (application as ScreenPalApplication).agentRuntime
            runtime.stopTask()

            // UI update will happen automatically via StateManager observing Runtime state
            panelManager.inputField?.clearFocus()
            panelManager.updateFocusable(false)
            stateManager.closePanel()
        }
    }

    private fun showInteractionInternal(message: String, choices: List<String>, onResult: (String?) -> Unit) {
        interactionManager.showInteraction(message, choices, onResult)
    }

    private fun showClickEffectInternal(x: Float, y: Float) {
        scope.launch {
            effectManager.showClickEffect(x, y)
        }
    }

    private fun showSwipeEffectInternal(startX: Float, startY: Float, endX: Float, endY: Float) {
        scope.launch {
            effectManager.showSwipeEffect(startX, startY, endX, endY)
        }
    }

    // --- Bindings ---

    @SuppressLint("SetTextI18n")
    private fun bindRuntime() {
        android.util.Log.d("FloatingAssistant", "bindRuntime: starting to observe runtime.uiState")
        val runtime = (application as ScreenPalApplication).agentRuntime
        scope.launch {
            runtime.uiState.collectLatest { state ->
                android.util.Log.d("FloatingAssistant", "bindRuntime: state update - isLoading=${state.isLoading}, messages=${state.messages.size}, hasError=${state.error != null}")
                // Update State Manager
                stateManager.updateRuntimeState(state)

                // Handle Side Effects (TTS, Interactions)
                handleSideEffects(state)

                // Steps Manager still needs data update
                stepsManager.updateUiFromState(state)
            }
        }
    }

    private fun handleSideEffects(state: ChatUiState) {
        // TTS
        if (state.taskCompletedMessage != null && state.taskCompletedMessage != lastSpokenMessage) {
            lastSpokenMessage = state.taskCompletedMessage
            if (ttsEnabled) {
                ttsManager?.speak(state.taskCompletedMessage)
            }
        }

        if (state.error != null && state.error != lastSpokenError) {
            lastSpokenError = state.error
            if (ttsEnabled) {
                ttsManager?.speak(state.error.take(80))
            }
        }

        // Interaction
        val lastMsg = state.messages.lastOrNull()
        if (lastMsg != null && !lastMsg.interactiveActions.isNullOrEmpty()) {
            if (lastMsg.id != lastInteractiveMessageId) {
                lastInteractiveMessageId = lastMsg.id
                val actions = lastMsg.interactiveActions
                val choices = actions.map { it.label }
                interactionManager.showInteraction(lastMsg.content, choices) { selectedLabel ->
                    if (selectedLabel != null) {
                        val action = actions.find { it.label == selectedLabel }
                        if (action != null) {
                            (application as ScreenPalApplication).agentRuntime.handleUserInteraction(action.actionId, action.payload)
                        }
                    }
                }
            }
        } else {
             if (state.messages.isEmpty()) {
                 lastInteractiveMessageId = null
                 interactionManager.removeInteractionView()
             }
        }
    }

    private fun bindTtsEnabled() {
        val repo = PreferencesRepository(this)
        scope.launch {
            repo.ttsEnabled.collectLatest { enabled ->
                ttsEnabled = enabled
            }
        }
    }

    private fun bindFloatingBallSize() {
        val repo = PreferencesRepository(this)
        scope.launch {
            repo.floatingBallSize.collectLatest { size ->
                bubbleManager.updateSize(size)
            }
        }
    }

    private fun bindFloatingBallPosition() {
        val repo = PreferencesRepository(this)
        scope.launch {
            kotlinx.coroutines.flow.combine(repo.floatingBallX, repo.floatingBallY) { x, y ->
                Pair(x, y)
            }.collectLatest { (x, y) ->
                bubbleManager.setSavedPosition(x, y)
            }
        }
    }

    private fun runInstruction(id: String, isPreview: Boolean = false) {
        val app = application as ScreenPalApplication
        if (!AccessibilityServiceHelper.isServiceConnected.value) {
            ToastUtils.show(this, getString(R.string.please_enable_accessibility), Toast.LENGTH_LONG)
            AccessibilityServiceHelper.goToAccessibilitySettings(this)
            return
        }
        ToastUtils.show(this, "开始执行指令...", Toast.LENGTH_SHORT)
        val runtime = app.agentRuntime
        runtime.runInstruction(id, isPreview)
    }
}
