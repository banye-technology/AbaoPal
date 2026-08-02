package com.withcareer.screenpal_android.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.withcareer.screenpal_android.util.OverlayViewUtils

/**
 * 点击特效管理器
 * 负责在屏幕上显示临时的点击反馈动画
 */
class FloatingEffectManager(
    private val context: Context,
    private val windowManager: WindowManager
) {

    companion object {
        // 点击特效：半透明青色填充，亮青色描边
        private const val CLICK_FILL_COLOR = "#4D00FFFF"
        private const val CLICK_STROKE_COLOR = Color.CYAN

        // 滑动特效：半透明橙色填充，橙色描边
        private const val SWIPE_FILL_COLOR = "#80FF9800"
        private const val SWIPE_STROKE_COLOR = "#FF9800"

        // 缩放特效：半透明紫色填充，紫色描边
        private const val PINCH_FILL_COLOR = "#809C27B0"
        private const val PINCH_STROKE_COLOR = "#9C27B0"
    }

    /**
     * 在指定位置显示点击特效
     * @param x 屏幕绝对 X 坐标
     * @param y 屏幕绝对 Y 坐标
     */
    fun showClickEffect(x: Float, y: Float) {
        val size = OverlayViewUtils.dpToPx(40)
        android.util.Log.d("FloatingEffectManager", "Showing click effect")

        // 创建一个圆形的 View
        val view = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(CLICK_FILL_COLOR))
                setStroke(OverlayViewUtils.dpToPx(2), CLICK_STROKE_COLOR)
            }
        }

        // 设置布局参数：不可触摸、不可聚焦、悬浮在应用之上
        val params = OverlayViewUtils.createLayoutParams(
            size,
            size,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        ).apply {
            // 关键修正：设置 Gravity 为 TOP|START 以使用绝对坐标
            gravity = Gravity.TOP or Gravity.START

            // 居中显示在点击位置
            this.x = (x - size / 2).toInt()
            this.y = (y - size / 2).toInt()
            // 初始透明度和缩放
            this.alpha = 1.0f
        }

        try {
            windowManager.addView(view, params)

            // 执行动画：放大并淡出
            view.animate()
                .scaleX(1.5f)
                .scaleY(1.5f)
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    try {
                        windowManager.removeView(view)
                    } catch (e: Exception) {
                        // 忽略移除失败（可能已经被系统移除）
                    }
                }
                .start()
        } catch (e: Exception) {
            android.util.Log.e("FloatingEffectManager", "Error showing click effect", e)
        }
    }

    /**
     * 显示滑动特效（模拟手指移动轨迹）
     * @param startX 起点 X
     * @param startY 起点 Y
     * @param endX 终点 X
     * @param endY 终点 Y
     */
    fun showSwipeEffect(startX: Float, startY: Float, endX: Float, endY: Float) {
        val size = OverlayViewUtils.dpToPx(30)
        android.util.Log.d("FloatingEffectManager", "Showing swipe effect")

        // 创建一个圆点 View，模拟手指
        val view = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(SWIPE_FILL_COLOR))
                setStroke(OverlayViewUtils.dpToPx(2), Color.parseColor(SWIPE_STROKE_COLOR))
            }
        }

        val params = OverlayViewUtils.createLayoutParams(
            size,
            size,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = (startX - size / 2).toInt()
            this.y = (startY - size / 2).toInt()
            this.alpha = 0.8f
        }

        try {
            windowManager.addView(view, params)

            // 计算位移增量
            val deltaX = endX - startX
            val deltaY = endY - startY

            // 执行滑动动画
            view.animate()
                .translationX(deltaX)
                .translationY(deltaY)
                .setDuration(500) // 动画持续 500ms
                .withEndAction {
                    // 动画结束后淡出并移除
                    view.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction {
                            try {
                                windowManager.removeView(view)
                            } catch (e: Exception) {
                                // ignore
                            }
                        }
                        .start()
                }
                .start()

        } catch (e: Exception) {
            android.util.Log.e("FloatingEffectManager", "Error showing swipe effect", e)
        }
    }



    private fun createDotView(size: Int, fillColor: String, strokeColor: String): View {
        return View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(fillColor))
                setStroke(OverlayViewUtils.dpToPx(2), Color.parseColor(strokeColor))
            }
        }
    }

    private fun createParams(size: Int, x: Float, y: Float): WindowManager.LayoutParams {
        return OverlayViewUtils.createLayoutParams(
            size, size,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x.toInt()
            this.y = y.toInt()
            this.alpha = 0.8f
        }
    }

    private fun removeView(view: View) {
        view.animate().alpha(0f).setDuration(200).withEndAction {
            try {
                windowManager.removeView(view)
            } catch (e: Exception) { }
        }.start()
    }
}
