package com.withcareer.screenpal_android.util

import android.content.res.Resources
import android.graphics.PixelFormat
import android.os.Build
import android.view.View
import android.view.WindowManager

/**
 * 悬浮窗视图工具类
 */
object OverlayViewUtils {

    fun dpToPx(dp: Int): Int {
        return (dp * Resources.getSystem().displayMetrics.density).toInt()
    }

    @Suppress("DEPRECATION")
    fun createLayoutParams(width: Int, height: Int, flags: Int = 0): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val defaultFlags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        return WindowManager.LayoutParams(
            width,
            height,
            type,
            defaultFlags or flags,
            PixelFormat.TRANSLUCENT
        )
    }

    fun fadeIn(view: View?, targetAlpha: Float = 1f, duration: Long = 200, onEnd: (() -> Unit)? = null) {
        if (view == null) {
            onEnd?.invoke()
            return
        }
        if (view.visibility != View.VISIBLE) {
            view.alpha = 0f
            view.visibility = View.VISIBLE
        }
        view.animate()
            .alpha(targetAlpha)
            .setDuration(duration)
            .withEndAction {
                onEnd?.invoke()
            }
            .start()
    }

    fun fadeOut(view: View?, duration: Long = 200, onEnd: (() -> Unit)? = null) {
        if (view == null || view.visibility == View.GONE) {
            onEnd?.invoke()
            return
        }
        view.animate()
            .alpha(0f)
            .setDuration(duration)
            .withEndAction {
                view.visibility = View.GONE
                onEnd?.invoke()
            }
            .start()
    }
}
