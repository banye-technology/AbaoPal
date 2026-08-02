package com.withcareer.screenpal_android.recording

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.util.TypedValue
import android.widget.FrameLayout
import kotlin.math.max

/**
 * 录制视觉反馈层
 * 管理多个反馈图标的显示和淡出动画
 *
 * 功能：
 * - 在操作位置显示对应类型的图标
 * - 新操作触发时，旧图标淡出
 * - 使用 FLAG_NOT_TOUCHABLE 确保不拦截触摸事件
 *
 */
@SuppressLint("ViewConstructor", "InternalInsetResource", "DiscouragedApi")
class RecordingFeedbackView(context: Context) : FrameLayout(context) {

    companion object {
        private const val TAG = "RecordingFeedbackView"
    }

    private val activeIcons = mutableListOf<FeedbackIconView>()
    private val activePaths = mutableListOf<SwipePathView>()

    init {
        // 设置为透明背景，不拦截触摸
        setBackgroundColor(0x00000000)
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
        fitsSystemWindows = false  // 确保不会自动添加内边距

        // 禁用系统窗口插入（确保覆盖整个屏幕包括状态栏）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT_WATCH) {
            setOnApplyWindowInsetsListener { v, insets ->
                @Suppress("DEPRECATION")
                Log.d(TAG, "WindowInsets applied")
                // 返回 CONSUMED 表示不需要系统添加内边距
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    android.view.WindowInsets.CONSUMED
                } else {
                    @Suppress("DEPRECATION")
                    insets.consumeSystemWindowInsets()
                }
            }
        }
    }

    /**
     * 显示操作反馈图标
     *
     * @param x 操作位置X坐标（屏幕绝对坐标，包含状态栏）
     * @param y 操作位置Y坐标（屏幕绝对坐标，包含状态栏）
     * @param actionType 操作类型：tap, swipe, longPress
     * @param stepNumber 步数序号
     */
    fun showFeedback(
        x: Int,
        y: Int,
        actionType: String,
        stepNumber: Int
    ) {
        // 注意：旧图标已在回放前清除
        addIcon(x, y, actionType, stepNumber, null)
    }

    /**
     * 显示滑动起止图标（S / E）
     */
    fun showSwipeEndpoints(
        start: Pair<Int, Int>,
        end: Pair<Int, Int>,
        stepNumber: Int
    ) {
        addIcon(start.first, start.second, "swipe", stepNumber, "S")
        addIcon(end.first, end.second, "swipe", stepNumber, "E")
    }

    private fun addIcon(
        x: Int,
        y: Int,
        actionType: String,
        stepNumber: Int,
        label: String?
    ) {
        val iconView = FeedbackIconView(context).apply {
            setIcon(actionType)
            setStepNumber(stepNumber)
            setCenterLabel(label)
            // 预设动画初始状态，避免添加后才设置导致的闪烁
            alpha = 0.5f
            scaleX = 0.85f
            scaleY = 0.85f
        }

        val iconSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            FeedbackIconView.ICON_SIZE_DP.toFloat(),
            context.resources.displayMetrics
        ).toInt()

        // 获取 FeedbackView 在屏幕上的实际位置
        val location = IntArray(2)
        getLocationOnScreen(location)
        val viewOffsetX = location[0]
        val viewOffsetY = location[1]

        // 计算图标在 FeedbackView 容器内的相对位置
        val adjustedX = x - viewOffsetX
        val adjustedY = y - viewOffsetY

        val layoutParams = LayoutParams(iconSize, iconSize).apply {
            leftMargin = adjustedX - iconSize / 2
            topMargin = max(0, adjustedY - iconSize / 2)
        }

        Log.d(TAG, "Placing feedback icon")

        addView(iconView, layoutParams)
        activeIcons.add(iconView)

        // 立即开始动画（iconView 已经有初始状态）
        iconView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(80)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
            .start()
    }

    /**
     * 显示滑动路径（虚线 + 起点标记）
     */
    fun showSwipePath(points: List<Pair<Int, Int>>) {
        if (points.size < 2) return

        // 获取 FeedbackView 在屏幕上的实际位置
        val location = IntArray(2)
        getLocationOnScreen(location)
        val viewOffsetX = location[0]
        val viewOffsetY = location[1]

        // 将屏幕绝对坐标转换为容器内相对坐标
        val adjustedPoints = points.map { (x, y) ->
            Pair(x - viewOffsetX, y - viewOffsetY)
        }

        val pathView = SwipePathView(context, adjustedPoints).apply {
            alpha = 0.5f  // 预设初始透明度
        }

        addView(pathView, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        activePaths.add(pathView)

        // 立即开始淡入动画
        pathView.animate().alpha(1f).setDuration(60).start()
    }


    /**
     * 清除所有图标和路径
     */
    fun clearAllIcons() {
        // 取消所有正在进行的动画
        activeIcons.forEach { icon ->
            icon.animate().cancel()
            removeView(icon)
        }
        activeIcons.clear()

        activePaths.forEach { path ->
            path.animate().cancel()
            removeView(path)
        }
        activePaths.clear()

        // 额外保险：移除所有子视图（防止有遗漏的）
        removeAllViews()

        Log.d(TAG, "All icons and paths cleared")
    }
}
