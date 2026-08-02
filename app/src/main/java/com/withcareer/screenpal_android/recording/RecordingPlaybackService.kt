package com.withcareer.screenpal_android.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.util.Log
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.withcareer.screenpal_android.R
import com.withcareer.screenpal_android.ScreenPalApplication
import com.withcareer.screenpal_android.util.AccessibilityServiceHelper
import com.withcareer.screenpal_android.util.ToastUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

class RecordingPlaybackService : Service() {

    companion object {
        private const val ACTION_START = "com.withcareer.screenpal_android.recording.action.PLAYBACK_START"
        private const val ACTION_STOP = "com.withcareer.screenpal_android.recording.action.PLAYBACK_STOP"
        private const val EXTRA_INSTRUCTION_ID = "EXTRA_INSTRUCTION_ID"
        private const val EXTRA_SKILL_INSTRUCTION_SET = "EXTRA_SKILL_INSTRUCTION_SET"
        private const val EXTRA_RECORDED_WIDTH = "EXTRA_RECORDED_WIDTH"
        private const val EXTRA_RECORDED_HEIGHT = "EXTRA_RECORDED_HEIGHT"

        private const val NOTIFICATION_CHANNEL_ID = "recording_playback_service"
        private const val NOTIFICATION_ID = 2002

        fun start(context: Context, instructionId: String) {
            val intent = Intent(context, RecordingPlaybackService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_INSTRUCTION_ID, instructionId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startWithSkill(
            context: Context,
            skillInstructionSet: com.withcareer.screenpal_android.data.model.SkillInstructionSet,
            recordedWidth: Int,
            recordedHeight: Int
        ) {
            val intent = Intent(context, RecordingPlaybackService::class.java).apply {
                action = ACTION_START
                val gson = com.google.gson.Gson()
                putExtra(EXTRA_SKILL_INSTRUCTION_SET, gson.toJson(skillInstructionSet))
                putExtra(EXTRA_RECORDED_WIDTH, recordedWidth)
                putExtra(EXTRA_RECORDED_HEIGHT, recordedHeight)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, RecordingPlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var windowManager: WindowManager? = null
    private var controller: PlaybackFloatingController? = null
    private var instructionId: String? = null
    private var skillInstructionSet: com.withcareer.screenpal_android.data.model.SkillInstructionSet? = null
    private var recordedWidth: Int = 0
    private var recordedHeight: Int = 0
    private var isPlaying = false
    private var isPaused = false
    private var uiStateJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopPlayback()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                val canDraw = Settings.canDrawOverlays(this)
                if (!canDraw) {
                    ToastUtils.show(this, getString(R.string.floating_permission_request), android.widget.Toast.LENGTH_LONG)
                    stopSelf()
                    return START_NOT_STICKY
                }

                startForegroundInternal()
                instructionId = intent?.getStringExtra(EXTRA_INSTRUCTION_ID)
                val skillJson = intent?.getStringExtra(EXTRA_SKILL_INSTRUCTION_SET)
                if (!skillJson.isNullOrBlank()) {
                    try {
                        val gson = com.google.gson.Gson()
                        skillInstructionSet = gson.fromJson(skillJson, com.withcareer.screenpal_android.data.model.SkillInstructionSet::class.java)
                        recordedWidth = intent.getIntExtra(EXTRA_RECORDED_WIDTH, 0)
                        recordedHeight = intent.getIntExtra(EXTRA_RECORDED_HEIGHT, 0)
                    } catch (e: Exception) {
                        Log.e("RecordingPlaybackService", "Failed to parse skill instruction set", e)
                    }
                }
                showController()
                bindRuntimeState()
                return START_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        uiStateJob?.cancel()
        controller?.hide()
        controller = null
        windowManager = null
        scope.cancel()
        super.onDestroy()
    }

    private fun showController() {
        if (controller == null) {
            controller = PlaybackFloatingController(
                context = this,
                windowManager = windowManager ?: return,
                onStartPlayback = { startPlayback() },
                onPauseResume = { togglePause() },
                onStopPlayback = { stopPlayback() },
                onCancel = {
                    stopPlayback()
                    stopSelf()
                }
            )
        }
        controller?.show()
    }

    private fun startPlayback() {
        if (!AccessibilityServiceHelper.isServiceConnected.value) {
            ToastUtils.show(this, "请先启用无障碍服务", android.widget.Toast.LENGTH_LONG)
            AccessibilityServiceHelper.goToAccessibilitySettings(this)
            return
        }

        val runtime = (application as ScreenPalApplication).agentRuntime

        if (skillInstructionSet != null) {
            runtime.runSkillInstructionSet(
                skillInstructionSet = skillInstructionSet!!,
                recordedWidth = recordedWidth,
                recordedHeight = recordedHeight,
                ignoreTargetPackage = true
            )
        } else if (!instructionId.isNullOrBlank()) {
            runtime.runInstruction(instructionId!!, ignoreTargetPackage = true)
        } else {
            ToastUtils.show(this, "未找到指令集", android.widget.Toast.LENGTH_SHORT)
            return
        }

        isPlaying = true
        isPaused = false
        controller?.showPlaying(paused = false)
        controller?.collapseToRightEdge()
    }

    private fun togglePause() {
        val runtime = (application as ScreenPalApplication).agentRuntime
        if (!isPlaying) {
            ToastUtils.show(this, "当前没有播放任务", android.widget.Toast.LENGTH_SHORT)
            return
        }
        val updated = if (isPaused) {
            runtime.resumeInstructionPlayback()
        } else {
            runtime.pauseInstructionPlayback()
        }
        if (updated) {
            isPaused = !isPaused
            controller?.updatePauseState(isPaused)
        }
    }

    private fun stopPlayback() {
        val runtime = (application as ScreenPalApplication).agentRuntime
        if (isPlaying) {
            runtime.stopInstructionPlayback()
        }
        isPlaying = false
        isPaused = false
        controller?.showReady()
    }

    private fun bindRuntimeState() {
        if (uiStateJob != null) return
        val runtime = (application as ScreenPalApplication).agentRuntime
        uiStateJob = scope.launch {
            runtime.uiState.collectLatest { state ->
                if (isPlaying && !state.isLoading) {
                    isPlaying = false
                    isPaused = false
                    controller?.showReady()
                    controller?.expandToFull()
                }
            }
        }
    }

    private fun startForegroundInternal() {
        createNotificationChannelIfNeeded()
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("录制回放控制")
            .setContentText("播放控制悬浮窗正在运行")
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannelIfNeeded() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "录制回放",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }
}
