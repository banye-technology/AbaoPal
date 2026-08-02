package com.withcareer.screenpal_android.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.withcareer.screenpal_android.R
import com.withcareer.screenpal_android.util.OverlayViewUtils

/**
 * 垃圾桶管理器
 * 当拖拽悬浮球时显示，拖拽到此处关闭服务
 */
class FloatingTrashManager(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private var trashView: View? = null
    private val trashSize = OverlayViewUtils.dpToPx(72)
    private val trashHoverSize = OverlayViewUtils.dpToPx(80)
    private val iconSize = OverlayViewUtils.dpToPx(32)

    private var isHovered = false

    private fun updateTrashLayoutSize(view: View, targetSize: Int) {
        val layoutParams = view.layoutParams as? WindowManager.LayoutParams ?: return
        if (layoutParams.width == targetSize && layoutParams.height == targetSize) return
        layoutParams.width = targetSize
        layoutParams.height = targetSize
        try {
            windowManager.updateViewLayout(view, layoutParams)
        } catch (e: Exception) {
            android.util.Log.e("FloatingTrashManager", "Failed to update trash view size", e)
        }
    }

    private fun applyTrashBackground(view: View, color: Int) {
        val container = view as? FrameLayout ?: return
        container.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        container.invalidate()
    }

    fun show() {
        if (trashView != null) return

        val container = FrameLayout(context)
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#CC000000")) // 半透明黑色背景
        }
        container.background = bg

        val icon = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_delete)
            setColorFilter(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
        }
        container.addView(icon)

        val params = OverlayViewUtils.createLayoutParams(trashSize, trashSize).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = OverlayViewUtils.dpToPx(48)
            // 确保显示在所有其他悬浮窗之上，且不处理点击事件（由 BubbleManager 的 dragEnd 判断位置）
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        try {
            windowManager.addView(container, params)
            trashView = container
            trashView?.alpha = 0f
            trashView?.scaleX = 0.8f
            trashView?.scaleY = 0.8f
            trashView?.animate()?.alpha(1f)?.scaleX(1f)?.scaleY(1f)?.setDuration(200)?.start()
        } catch (e: Exception) {
            android.util.Log.e("FloatingTrashManager", "Failed to add trash view", e)
        }
    }

    fun hide() {
        trashView?.let { view ->
            view.animate()?.alpha(0f)?.scaleX(0.8f)?.scaleY(0.8f)?.setDuration(200)?.withEndAction {
                try {
                    windowManager.removeView(view)
                } catch (e: Exception) {
                    // ignore
                }
            }?.start()
            trashView = null
        }
        isHovered = false
    }

    fun updateHoverState(rawX: Float, rawY: Float): Boolean {
        val view = trashView ?: return false
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val centerX = location[0] + view.width / 2
        val centerY = location[1] + view.height / 2

        // 计算距离中心点的距离
        val dx = rawX - centerX
        val dy = rawY - centerY
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)

        val baseRadius = trashSize / 2f
        val hovered = if (isHovered) {
            distance < baseRadius * 1.05f
        } else {
            distance < baseRadius * 0.9f
        }

        if (hovered != isHovered) {
            isHovered = hovered
            if (hovered) {
                updateTrashLayoutSize(view, trashHoverSize)
                applyTrashBackground(view, Color.parseColor("#CCFF4444"))
            } else {
                updateTrashLayoutSize(view, trashSize)
                applyTrashBackground(view, Color.parseColor("#CC000000"))
            }
        }

        return hovered
    }

    fun isVisible(): Boolean = trashView != null
}
