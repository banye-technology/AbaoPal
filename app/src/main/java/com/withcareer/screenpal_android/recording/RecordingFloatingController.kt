package com.withcareer.screenpal_android.recording

import android.annotation.SuppressLint
import android.content.Context
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.withcareer.screenpal_android.R

/**
 * 录制悬浮窗控制器
 * 提供开始/停止/取消录制的UI控制
 *
 * 重要：此控制器在整个录制过程中永不被 remove/add，确保不闪烁
 */
@SuppressLint("ClickableViewAccessibility")
class RecordingFloatingController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val recordingManager: RecordingManager,
    private val onStart: () -> Unit,
    private val onComplete: () -> Unit,
    private val onCancel: () -> Unit,
    private val onBatchMultiTapChanged: (Boolean) -> Unit = {}
) {

    companion object {
        private const val TAG = "RecordingController"
    }

    // UI 元素
    private var floatingView: View? = null
    private lateinit var contentContainer: LinearLayout
    private lateinit var layoutReady: LinearLayout
    private lateinit var layoutRecording: LinearLayout
    private lateinit var btnStart: Button
    private lateinit var btnCancelReady: Button
    private lateinit var tvDuration: TextView
    private lateinit var tvActionCount: TextView
    private lateinit var checkboxBatchMultiTap: CheckBox
    private lateinit var btnStop: Button
    private lateinit var btnCancel: Button
    private lateinit var recordingHandle: ImageView
    private lateinit var recordingHandleCollapsed: TextView

    // 状态
    private var isRecording = false
    private var isAttached = false
    private var isCollapsed = false
    private var isBatchMultiTapEnabled = false
    private var pendingConfirmAction: ConfirmAction? = null
    private var confirmDialog: Dialog? = null

    private enum class ConfirmAction {
        COMPLETE,
        CANCEL
    }

    // 定时更新
    private val handler = Handler(Looper.getMainLooper())
    private var updateTask: Runnable? = null

    // 位置变化回调
    private var onPositionChanged: ((Rect) -> Unit)? = null

    // 布局参数（保存以便更新位置）
    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE,
        // 悬浮窗可触摸但不抢焦点，避免影响输入法弹出
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        x = 20  // 距离右边 20px
        y = 100  // 距离顶部 100px
    }

    init {
        // 加载布局
        floatingView = LayoutInflater.from(context).inflate(
            R.layout.floating_recording_control,
            null
        ).apply {
            layoutReady = findViewById(R.id.layoutReady)
            layoutRecording = findViewById(R.id.layoutRecording)
            contentContainer = findViewById(R.id.contentContainer)
            btnStart = findViewById(R.id.btnStart)
            btnCancelReady = findViewById(R.id.btnCancelReady)
            tvDuration = findViewById(R.id.tvDuration)
            tvActionCount = findViewById(R.id.tvActionCount)
            checkboxBatchMultiTap = findViewById(R.id.checkboxBatchMultiTap)
            btnStop = findViewById(R.id.btnStop)
            btnCancel = findViewById(R.id.btnCancel)
            recordingHandle = findViewById(R.id.recordingHandle)
            recordingHandleCollapsed = findViewById(R.id.recordingHandleCollapsed)

            // 开始按钮点击事件
            btnStart.setOnClickListener {
                startRecording()
            }

            // 准备状态取消按钮点击事件（未开始录制就退出）
            btnCancelReady.setOnClickListener {
                hide()
                onCancel()
            }

            // 完成按钮点击事件
            btnStop.setOnClickListener {
                showConfirm(ConfirmAction.COMPLETE)
            }

            // 录制状态取消按钮点击事件（放弃当前录制）
            btnCancel.setOnClickListener {
                showConfirm(ConfirmAction.CANCEL)
            }

            checkboxBatchMultiTap.setOnCheckedChangeListener { _, isChecked ->
                isBatchMultiTapEnabled = isChecked
                onBatchMultiTapChanged(isChecked)
            }

            recordingHandle.setOnClickListener { toggleCollapse() }
            recordingHandleCollapsed.setOnClickListener { toggleCollapse() }
            recordingHandle.visibility = View.VISIBLE
            recordingHandleCollapsed.visibility = View.GONE

            // 拖动功能
            var dX = 0f
            var dY = 0f
            var downRawX = 0f
            var downRawY = 0f
            var isDragging = false
            var downTargetView: View? = null
            val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop

            fun findTargetView(x: Float, y: Float): View? {
                val rect = android.graphics.Rect()
                val candidates = listOf(
                    btnStart,
                    btnCancelReady,
                    btnStop,
                    btnCancel,
                    recordingHandle,
                    recordingHandleCollapsed
                )
                for (v in candidates) {
                    v.getHitRect(rect)
                    if (rect.contains(x.toInt(), y.toInt())) {
                        return v
                    }
                }
                return null
            }

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 记录初始触摸点和当前 layoutParams
                        downRawX = event.rawX
                        downRawY = event.rawY

                        // 计算当前窗口左上角的屏幕坐标
                        val metrics = context.resources.displayMetrics
                        val screenWidth = metrics.widthPixels
                        val currentScreenX = screenWidth - this@RecordingFloatingController.layoutParams.x - v.width
                        val currentScreenY = this@RecordingFloatingController.layoutParams.y

                        // 记录触摸点相对于窗口左上角的偏移
                        dX = currentScreenX.toFloat() - event.rawX
                        dY = currentScreenY.toFloat() - event.rawY

                        isDragging = false
                        downTargetView = findTargetView(event.x, event.y)
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = kotlin.math.abs(event.rawX - downRawX)
                        val deltaY = kotlin.math.abs(event.rawY - downRawY)

                        if (!isDragging && (deltaX > touchSlop || deltaY > touchSlop)) {
                            isDragging = true
                        }

                        if (isDragging) {
                            // 计算新的屏幕绝对位置（左上角）
                            val newScreenX = (dX + event.rawX).toInt()
                            val newScreenY = (dY + event.rawY).toInt()

                            // 转换为 WindowManager 坐标系
                            val metrics = context.resources.displayMetrics
                            val screenWidth = metrics.widthPixels

                            // Gravity.END: x 表示距离右边的距离
                            this@RecordingFloatingController.layoutParams.x = screenWidth - newScreenX - v.width
                            this@RecordingFloatingController.layoutParams.y = newScreenY

                            windowManager.updateViewLayout(v, this@RecordingFloatingController.layoutParams)
                            // 拖动时实时通知位置（无需延迟，因为布局是连续的）
                            notifyPositionChanged()
                        }
                    }

                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            // 手动触发点击，避免被拖动逻辑吞掉
                            downTargetView?.performClick()
                            downTargetView = null
                            return@setOnTouchListener true
                        }
                        downTargetView = null
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        isDragging = false
                        downTargetView = null
                    }
                }
                true
            }
        }
    }

    fun dispatchTouchEvent(event: MotionEvent): Boolean {
        floatingView?.let { view ->
            // 将屏幕坐标转换为 View 的本地坐标
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            val localEvent = MotionEvent.obtain(event)
            localEvent.setLocation(
                event.rawX - location[0],
                event.rawY - location[1]
            )
            val result = view.dispatchTouchEvent(localEvent)
            localEvent.recycle()
            return result
        }
        return false
    }

    /**
     * 显示悬浮窗
     */
    fun show() {
        floatingView?.let { view ->
            if (!isAttached) {
                try {
                    windowManager.addView(view, layoutParams)
                    isAttached = true

                    // 显示准备状态
                    layoutReady.visibility = View.VISIBLE
                    layoutRecording.visibility = View.GONE
                    recordingHandle.visibility = View.VISIBLE
                    recordingHandleCollapsed.visibility = View.GONE

                    // 通知位置
                    notifyPositionChanged()

                    Log.d(TAG, "Floating controller shown")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to show floating controller", e)
                }
            }
        }
    }

    /**
     * 隐藏悬浮窗
     */
    fun hide() {
        floatingView?.let { view ->
            if (isAttached) {
                try {
                    stopUpdating()
                    windowManager.removeView(view)
                    isAttached = false
                    Log.d(TAG, "Floating controller hidden")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to hide floating controller", e)
                }
            }
        }
    }

    /**
     * 开始录制
     */
    private fun startRecording() {
        isRecording = true
        // 先清理旧数据，避免 UI 显示残留
        recordingManager.resetRecordingState()

        // 切换UI
        layoutReady.visibility = View.GONE
        layoutRecording.visibility = View.VISIBLE
        expandToFull()

        // 开始定时更新
        startUpdating()

        // 通知外部
        onStart()

        Log.d(TAG, "Recording started from floating controller")
    }

    /**
     * 停止录制（完成）
     */
    private fun stopRecordingConfirmed() {
        if (!isRecording) return

        isRecording = false
        checkboxBatchMultiTap.isChecked = false
        stopUpdating()
        hideConfirm()
        expandToFull()
        hide()

        // 通知外部
        onComplete()

        Log.d(TAG, "Recording stopped (completed)")
    }

    /**
     * 取消录制
     */
    private fun cancelRecordingConfirmed() {
        if (!isRecording) return

        isRecording = false
        checkboxBatchMultiTap.isChecked = false
        stopUpdating()
        hideConfirm()
        expandToFull()
        hide()

        // 通知外部
        onCancel()

        Log.d(TAG, "Recording cancelled")
    }

    private fun showConfirm(action: ConfirmAction) {
        if (!isRecording) return
        if (confirmDialog?.isShowing == true) return
        pendingConfirmAction = action
        stopUpdating()
        confirmDialog = AlertDialog.Builder(context)
            .setTitle("确认操作")
            .setMessage(
                when (action) {
                    ConfirmAction.COMPLETE -> "确认完成录制？"
                    ConfirmAction.CANCEL -> "确认取消录制？"
                }
            )
            .setPositiveButton("确定") { _, _ ->
                when (pendingConfirmAction) {
                    ConfirmAction.COMPLETE -> stopRecordingConfirmed()
                    ConfirmAction.CANCEL -> cancelRecordingConfirmed()
                    null -> hideConfirm()
                }
            }
            .setNegativeButton("继续录制") { _, _ ->
                hideConfirm()
            }
            .setOnDismissListener {
                if (pendingConfirmAction != null && isRecording) {
                    startUpdating()
                }
                pendingConfirmAction = null
                confirmDialog = null
            }
            .create()
        val dialog = confirmDialog ?: return
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        dialog.window?.setType(windowType)
        try {
            dialog.show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show confirm dialog", e)
            hideConfirm()
        }
    }

    private fun hideConfirm() {
        confirmDialog?.dismiss()
        confirmDialog = null
        pendingConfirmAction = null
        if (isRecording) {
            startUpdating()
        }
    }

    /**
     * 开始定时更新
     */
    private fun startUpdating() {
        updateTask = object : Runnable {
            override fun run() {
                updateStatus()
                handler.postDelayed(this, 500) // 每500ms更新一次
            }
        }
        handler.post(updateTask!!)
    }

    /**
     * 停止定时更新
     */
    private fun stopUpdating() {
        updateTask?.let { handler.removeCallbacks(it) }
        updateTask = null
    }

    /**
     * 更新显示状态
     */
    private fun updateStatus() {
        if (!isRecording) return

        val duration = recordingManager.getRecordingDuration()
        val baseCount = recordingManager.getDisplayActionCount()
        val actionCount = baseCount + if (isBatchMultiTapEnabled) 1 else 0

        tvDuration.text = formatDuration(duration)
        tvActionCount.text = "${actionCount}步"
    }

    private fun collapseToRightEdge() {
        isCollapsed = true
        contentContainer.visibility = View.GONE
        recordingHandle.visibility = View.GONE
        recordingHandleCollapsed.visibility = View.VISIBLE
        floatingView?.post {
            val view = floatingView ?: return@post
            layoutParams.x = 0
            try {
                windowManager.updateViewLayout(view, layoutParams)
                notifyPositionChanged()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to collapse recording controller", e)
            }
        }
    }

    private fun expandToFull() {
        isCollapsed = false
        contentContainer.visibility = View.VISIBLE
        recordingHandle.visibility = View.VISIBLE
        recordingHandleCollapsed.visibility = View.GONE
        floatingView?.post {
            val view = floatingView ?: return@post
            layoutParams.x = 0
            try {
                windowManager.updateViewLayout(view, layoutParams)
                notifyPositionChanged()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to expand recording controller", e)
            }
        }
    }

    private fun toggleCollapse() {
        if (isCollapsed) {
            expandToFull()
        } else {
            collapseToRightEdge()
        }
    }

    /**
     * 格式化时长显示
     */
    private fun formatDuration(ms: Long): String {
        val seconds = ms / 1000
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", minutes, secs)
    }

    /**
     * 更新位置（在配置变化时调用）
     */
    fun updatePosition() {
        floatingView?.let { view ->
            if (isAttached) {
                try {
                    windowManager.updateViewLayout(view, layoutParams)
                    // 延迟通知位置，确保布局完成
                    view.post {
                        notifyPositionChanged()
                        Log.d(TAG, "Position updated after configuration change")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update position", e)
                }
            }
        }
    }

    /**
     * 设置位置变化监听器
     */
    fun setOnPositionChangedListener(listener: (Rect) -> Unit) {
        this.onPositionChanged = listener
        // 立即通知当前位置
        notifyPositionChanged()
    }

    /**
     * 通知位置变化
     */
    private fun notifyPositionChanged() {
        floatingView?.let { view ->
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            val rect = Rect(
                location[0],
                location[1],
                location[0] + view.width,
                location[1] + view.height
            )
            val metrics = context.resources.displayMetrics
            Log.d(TAG, "Floating control position changed")
            onPositionChanged?.invoke(rect)
        }
    }
}
