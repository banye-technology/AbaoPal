package com.withcareer.screenpal_android.recording

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.withcareer.screenpal_android.R

/**
 * 播放悬浮窗控制器
 * 提供开始/暂停/终止的UI控制
 */
@SuppressLint("ClickableViewAccessibility")
class PlaybackFloatingController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onStartPlayback: () -> Unit,
    private val onPauseResume: () -> Unit,
    private val onStopPlayback: () -> Unit,
    private val onCancel: () -> Unit
) {

    companion object {
        private const val TAG = "PlaybackController"
    }

    private var floatingView: View? = null
    private lateinit var contentContainer: LinearLayout
    private lateinit var layoutReady: LinearLayout
    private lateinit var layoutPlaying: LinearLayout
    private lateinit var tvPlayingTitle: TextView
    private lateinit var playbackHandle: TextView
    private lateinit var btnStartPlayback: Button
    private lateinit var btnCancelReady: Button
    private lateinit var btnPauseResume: Button
    private lateinit var btnStopPlayback: Button

    private var isAttached = false
    private var isPaused = false
    private var isCollapsed = false

    private var onPositionChanged: ((Rect) -> Unit)? = null

    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        x = 0
        y = 140
    }

    init {
        floatingView = LayoutInflater.from(context).inflate(
            R.layout.floating_playback_control,
            null
        ).apply {
            layoutReady = findViewById(R.id.layoutReady)
            layoutPlaying = findViewById(R.id.layoutPlaying)
            contentContainer = findViewById(R.id.contentContainer)
            tvPlayingTitle = findViewById(R.id.tvPlayingTitle)
            btnStartPlayback = findViewById(R.id.btnStartPlayback)
            btnCancelReady = findViewById(R.id.btnCancelReady)
            btnPauseResume = findViewById(R.id.btnPauseResume)
            btnStopPlayback = findViewById(R.id.btnStopPlayback)
            playbackHandle = findViewById(R.id.playbackHandle)

            btnStartPlayback.setOnClickListener { onStartPlayback() }
            btnCancelReady.setOnClickListener { onCancel() }
            btnPauseResume.setOnClickListener { onPauseResume() }
            btnStopPlayback.setOnClickListener { onStopPlayback() }
            playbackHandle.setOnClickListener { toggleCollapse() }
            playbackHandle.visibility = View.GONE

            setupDrag(this)
        }
    }

    private fun setupDrag(rootView: View) {
        var dX = 0f
        var dY = 0f
        var downRawX = 0f
        var downRawY = 0f
        var isDragging = false
        var downTargetView: View? = null
        val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop

        fun findTargetView(x: Float, y: Float): View? {
            val rect = android.graphics.Rect()
            val candidates = listOf(btnStartPlayback, btnCancelReady, btnPauseResume, btnStopPlayback)
            for (v in candidates) {
                v.getHitRect(rect)
                if (rect.contains(x.toInt(), y.toInt())) {
                    return v
                }
            }
            return null
        }

        rootView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY

                    val metrics = context.resources.displayMetrics
                    val screenWidth = metrics.widthPixels
                    val currentScreenX = screenWidth - layoutParams.x - v.width
                    val currentScreenY = layoutParams.y

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
                        val newScreenX = (dX + event.rawX).toInt()
                        val newScreenY = (dY + event.rawY).toInt()

                        val metrics = context.resources.displayMetrics
                        val screenWidth = metrics.widthPixels

                        layoutParams.x = screenWidth - newScreenX - v.width
                        layoutParams.y = newScreenY

                        windowManager.updateViewLayout(v, layoutParams)
                        notifyPositionChanged()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
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

    fun show() {
        floatingView?.let { view ->
            if (!isAttached) {
                try {
                    windowManager.addView(view, layoutParams)
                    isAttached = true
                    showReady()
                    notifyPositionChanged()
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to show playback controller", e)
                }
            }
        }
    }

    fun hide() {
        floatingView?.let { view ->
            if (isAttached) {
                try {
                    windowManager.removeView(view)
                    isAttached = false
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to hide playback controller", e)
                }
            }
        }
    }

    fun showReady() {
        layoutReady.visibility = View.VISIBLE
        layoutPlaying.visibility = View.GONE
        playbackHandle.visibility = View.GONE
    }

    fun showPlaying(paused: Boolean) {
        isPaused = paused
        layoutReady.visibility = View.GONE
        layoutPlaying.visibility = View.VISIBLE
        updatePauseState(paused)
        playbackHandle.visibility = View.GONE
    }

    fun collapseToRightEdge(handleDp: Int = 24) {
        isCollapsed = true
        contentContainer.visibility = View.GONE
        playbackHandle.text = "<"
        playbackHandle.visibility = View.VISIBLE
        floatingView?.post {
            val view = floatingView ?: return@post
            layoutParams.x = 0
            try {
                windowManager.updateViewLayout(view, layoutParams)
                notifyPositionChanged()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to collapse playback controller", e)
            }
        }
    }

    private fun expandFromEdge() {
        isCollapsed = false
        contentContainer.visibility = View.VISIBLE
        playbackHandle.text = ">"
        playbackHandle.visibility = View.GONE
        floatingView?.post {
            val view = floatingView ?: return@post
            layoutParams.x = 0
            try {
                windowManager.updateViewLayout(view, layoutParams)
                notifyPositionChanged()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to expand playback controller", e)
            }
        }
    }

    private fun toggleCollapse() {
        if (isCollapsed) {
            expandFromEdge()
        } else {
            collapseToRightEdge()
        }
    }

    fun expandToFull() {
        if (isCollapsed) {
            expandFromEdge()
        } else {
            playbackHandle.visibility = View.GONE
        }
    }

    fun updatePauseState(paused: Boolean) {
        isPaused = paused
        tvPlayingTitle.text = if (paused) "已暂停" else "播放中"
        btnPauseResume.text = if (paused) "继续" else "暂停"
    }

    fun updatePosition() {
        floatingView?.let { view ->
            if (isAttached) {
                try {
                    windowManager.updateViewLayout(view, layoutParams)
                    view.post { notifyPositionChanged() }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to update position", e)
                }
            }
        }
    }

    fun setOnPositionChangedListener(listener: (Rect) -> Unit) {
        this.onPositionChanged = listener
        notifyPositionChanged()
    }

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
            onPositionChanged?.invoke(rect)
        }
    }
}
