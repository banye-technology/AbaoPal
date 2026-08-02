package com.withcareer.screenpal_android.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.withcareer.screenpal_android.R
import com.withcareer.screenpal_android.data.preference.PreferencesRepository
import com.withcareer.screenpal_android.util.OverlayViewUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 悬浮球管理器
 * 负责悬浮球的创建、显示、拖拽和隐藏逻辑
 */
class FloatingBubbleManager(
    private val context: Context,
    private val windowManager: WindowManager,
    private val scope: CoroutineScope,
    private val preferencesRepository: PreferencesRepository,
    private val onBubbleTap: () -> Unit
) {
    /**
     * 拖拽监听器
     */
    interface OnDragListener {
        fun onDragStart()
        fun onDrag(rawX: Float, rawY: Float)
        fun onDragEnd(rawX: Float, rawY: Float)
    }

    var dragListener: OnDragListener? = null

    var bubbleView: View? = null
        private set

    var isTaskRunning: Boolean = false

    private var savedBubbleX: Int = 0
    private var savedBubbleY: Int = 200
    private var currentSizeIndex: Int = 1

    private val hideHandler = Handler(Looper.getMainLooper())
    private var hideRunnable: Runnable? = null

    private val screenWidth: Int
        get() = context.resources.displayMetrics.widthPixels

    private var breathingAnimator: ValueAnimator? = null

    fun updateIconState(isLoading: Boolean) {
        val container = bubbleView as? FrameLayout ?: return
        val bubbleImage = container.getChildAt(0) as? ImageView ?: return

        if (!isLoading) {
            bubbleImage.setImageResource(R.drawable.logo)
            bubbleImage.clearColorFilter()
            bubbleImage.setPadding(0, 0, 0, 0)

            stopBreathing()
        }
    }

    private fun startBreathing() {
        if (breathingAnimator?.isRunning == true) return

        breathingAnimator = ValueAnimator.ofFloat(1.0f, 0.4f, 1.0f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                bubbleView?.alpha = value
            }
            start()
        }
    }

    fun stopBreathing() {
        breathingAnimator?.cancel()
        breathingAnimator = null
        bubbleView?.alpha = 1.0f
    }

    fun setSavedPosition(x: Int, y: Int) {
        savedBubbleX = x
        savedBubbleY = y

        bubbleView?.let { view ->
            val params = view.layoutParams as WindowManager.LayoutParams
            params.x = x
            params.y = y
            try {
                windowManager.updateViewLayout(view, params)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun updateSize(sizeIndex: Int) {
        currentSizeIndex = sizeIndex
        val view = bubbleView ?: return

        val containerSizeDp = when (sizeIndex) {
            0 -> 40
            1 -> 53
            2 -> 67
            3 -> 80
            else -> 53
        }
        val size = OverlayViewUtils.dpToPx(containerSizeDp)

        val params = view.layoutParams as WindowManager.LayoutParams
        params.width = size
        params.height = size
        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            android.util.Log.e("FloatingBubbleManager", "Failed to update bubble size", e)
        }
    }

    private fun calculateSnapTargetX(currentX: Int, width: Int): Int {
        val centerX = currentX + width / 2
        val margin = OverlayViewUtils.dpToPx(8)
        return if (centerX < screenWidth / 2) {
            margin
        } else {
            screenWidth - width - margin
        }
    }

    fun updateConfiguration(newConfig: Configuration) {
        val container = bubbleView as? FrameLayout ?: return

        // 更新背景颜色（深色模式适配）
        container.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(context, R.color.bubble_background))
            setStroke(OverlayViewUtils.dpToPx(1), ContextCompat.getColor(context, R.color.bubble_stroke))
        }

        // 重新布局以适应可能的边距变化
        val params = container.layoutParams as WindowManager.LayoutParams
        val targetX = calculateSnapTargetX(params.x, params.width)

        if (params.x != targetX) {
            animateBubbleToX(targetX) {
                scope.launch {
                    preferencesRepository.saveFloatingBallPosition(targetX, params.y)
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun addBubble() {
        if (bubbleView != null) return

        val containerSizeDp = when (currentSizeIndex) {
            0 -> 40
            1 -> 53
            2 -> 67
            3 -> 80
            else -> 53
        }
        val size = OverlayViewUtils.dpToPx(containerSizeDp)

        val container = FrameLayout(context)
        val image = ImageView(context).apply {
            setImageResource(R.drawable.logo)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        container.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(context, R.color.bubble_background))
            setStroke(OverlayViewUtils.dpToPx(1), ContextCompat.getColor(context, R.color.bubble_stroke))
        }
        container.clipToOutline = true
        container.elevation = OverlayViewUtils.dpToPx(4).toFloat()
        container.addView(image)

        val params = OverlayViewUtils.createLayoutParams(size, size).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedBubbleX
            y = savedBubbleY
            flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }

        container.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var moved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        cancelBubbleHide()
                        // 恢复完全不透明
                        v.alpha = 1f
                        startX = params.x
                        startY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - touchX).toInt()
                        val dy = (event.rawY - touchY).toInt()
                        if (kotlin.math.abs(dx) > 6 || kotlin.math.abs(dy) > 6) {
                            if (!moved) {
                                moved = true
                                dragListener?.onDragStart()
                            }
                        }
                        params.x = startX + dx
                        params.y = startY + dy
                        try {
                            windowManager.updateViewLayout(container, params)
                        } catch (e: Exception) {
                            // ignore
                        }
                        if (moved) {
                            dragListener?.onDrag(event.rawX, event.rawY)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (moved) {
                            dragListener?.onDragEnd(event.rawX, event.rawY)
                        }
                        if (!moved) {
                            onBubbleTap()
                            scheduleBubbleHide()
                        } else {
                            // 拖动结束，自动贴边
                            val targetX = calculateSnapTargetX(params.x, params.width)
                            animateBubbleToX(targetX) {
                                scheduleBubbleHide()
                                scope.launch {
                                    preferencesRepository.saveFloatingBallPosition(targetX, params.y)
                                }
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager.addView(container, params)
            bubbleView = container
        } catch (e: Exception) {
            android.util.Log.e("FloatingBubbleManager", "addBubble: failed to add bubble", e)
            bubbleView = null
        }
    }

    fun removeBubble() {
        bubbleView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // ignore
            }
        }
        bubbleView = null
    }

    private fun animateBubbleToX(targetX: Int, onEnd: (() -> Unit)? = null) {
        val view = bubbleView ?: return
        val params = view.layoutParams as WindowManager.LayoutParams
        val currentX = params.x

        val animator = ValueAnimator.ofInt(currentX, targetX)
        animator.duration = 300
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.addUpdateListener { animation ->
            params.x = animation.animatedValue as Int
            try {
                windowManager.updateViewLayout(view, params)
            } catch (e: Exception) {
                // ignore
            }
        }
        if (onEnd != null) {
            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onEnd()
                }
            })
        }
        animator.start()
    }

    fun scheduleBubbleHide() {
        cancelBubbleHide()
        val runnable = Runnable {
            // 半透明隐藏并向边缘移动一半
            val view = bubbleView ?: return@Runnable
            val params = view.layoutParams as WindowManager.LayoutParams

            // 判断是靠左还是靠右
            val centerX = params.x + params.width / 2
            val targetX = if (centerX < screenWidth / 2) {
                -params.width / 2 // 左侧隐藏一半
            } else {
                screenWidth - params.width / 2 // 右侧隐藏一半
            }

            animateBubbleToX(targetX)
            view.animate().alpha(0.3f).setDuration(500).start()
        }
        hideRunnable = runnable
        hideHandler.postDelayed(runnable, 3000)
    }

    fun cancelBubbleHide() {
        hideRunnable?.let { hideHandler.removeCallbacks(it) }
        hideRunnable = null
        bubbleView?.animate()?.cancel()
        bubbleView?.alpha = 1f

        // 恢复到正常贴边位置
        val view = bubbleView ?: return
        val params = view.layoutParams as WindowManager.LayoutParams
        val targetX = calculateSnapTargetX(params.x, params.width)

        // 如果当前不在正常位置，则移动回去
        if (params.x != targetX) {
             animateBubbleToX(targetX)
        }
    }

    fun setVisible(visible: Boolean) {
        if (visible) {
            OverlayViewUtils.fadeIn(bubbleView, 1f)
            scheduleBubbleHide()
        } else {
            stopBreathing()
            OverlayViewUtils.fadeOut(bubbleView)
        }
    }

    /**
     * Restore visibility from a hidden state (e.g. after screenshot)
     * Respected isTaskRunning state.
     */
    fun restoreVisibility(targetAlpha: Float, onEnd: (() -> Unit)? = null) {
        if (isTaskRunning) {
            // If task is running, we do NOT restore visibility.
            // But we should still execute the callback to ensure logic continuity
            onEnd?.invoke()
            return
        }
        OverlayViewUtils.fadeIn(bubbleView, targetAlpha, onEnd = onEnd)
    }

    fun isVisible(): Boolean {
        return bubbleView?.visibility == View.VISIBLE
    }
}
