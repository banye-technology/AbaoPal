package com.withcareer.screenpal_android.recording

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View

/**
 * 录制反馈图标视图
 * 使用 Canvas 绘制操作图标，显示步数和操作类型
 *
 * 图标设计：
 * - 点击（tap）: 绿色圆圈 + 中心白色圆点 + 步数
 * - 滑动（swipe）: 蓝色圆圈 + 白色箭头 + 步数
 * - 长按（longPress）: 橙色圆圈 + 中心白色圆点 + 外圈进度环 + 步数
 *
 */
class FeedbackIconView(context: Context) : View(context) {

    companion object {
        const val ICON_SIZE_DP = 32           // 图标整体大小
        const val CIRCLE_RADIUS_DP = 14       // 圆圈半径
        const val ICON_ELEMENT_SIZE_DP = 8    // 内部图形元素大小
        const val STROKE_WIDTH_DP = 2f        // 线条宽度
        const val TEXT_SIZE_SP = 10f          // 步数文字大小

        // 颜色定义
        const val TAP_COLOR = "#4CAF50"        // 点击 - 绿色
        const val SWIPE_COLOR = "#2196F3"      // 滑动 - 蓝色
        const val LONG_PRESS_COLOR = "#FF9800" // 长按 - 橙色
        const val ICON_COLOR = "#FFFFFF"       // 图标元素 - 白色
        const val TEXT_COLOR = "#FFFFFF"       // 文字 - 白色
    }

    private var actionType: String = "tap"
    private var stepNumber: Int = 1
    private var centerLabel: String? = null

    // 绘制工具
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(ICON_COLOR)
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(TEXT_COLOR)
        textSize = sp(TEXT_SIZE_SP)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val centerLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(TEXT_COLOR)
        textSize = sp(14f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(ICON_COLOR)
        style = Paint.Style.STROKE
        strokeWidth = dp(STROKE_WIDTH_DP)
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = width / 2f - dp(4f)

        // 1. 绘制主圆圈背景
        circlePaint.color = when (actionType) {
            "tap", "multiTap" -> Color.parseColor(TAP_COLOR)           // 绿色
            "swipe", "longPressSwipe" -> Color.parseColor(SWIPE_COLOR)       // 蓝色
            "longPress" -> Color.parseColor(LONG_PRESS_COLOR) // 橙色
            else -> Color.parseColor(TAP_COLOR)
        }
        canvas.drawCircle(centerX, centerY, radius, circlePaint)

        // 2. 绘制图标符号
        when (actionType) {
            "tap", "multiTap" -> drawTapIcon(canvas, centerX, centerY)
            "swipe", "longPressSwipe" -> drawSwipeIcon(canvas, centerX, centerY)
            "longPress" -> drawLongPressIcon(canvas, centerX, centerY)
        }

        centerLabel?.let { label ->
            val textY = centerY - (centerLabelPaint.descent() + centerLabelPaint.ascent()) / 2
            canvas.drawText(label, centerX, textY, centerLabelPaint)
        }

        // 3. 绘制步数文字（右上角）
        val textX = width - dp(8f)
        val textY = dp(14f)
        textPaint.textSize = sp(TEXT_SIZE_SP)
        canvas.drawText(stepNumber.toString(), textX, textY, textPaint)
    }

    /**
     * 绘制点击图标（中心小圆点）
     */
    private fun drawTapIcon(canvas: Canvas, cx: Float, cy: Float) {
        canvas.drawCircle(cx, cy, dp(4f), iconPaint)
    }

    /**
     * 绘制滑动图标（箭头）
     */
    private fun drawSwipeIcon(canvas: Canvas, cx: Float, cy: Float) {
        // 滑动图标不再绘制箭头，使用中心字母标记起止点
    }

    /**
     * 绘制长按图标（中心圆点 + 外圈进度环）
     */
    private fun drawLongPressIcon(canvas: Canvas, cx: Float, cy: Float) {
        // 中心圆点
        canvas.drawCircle(cx, cy, dp(4f), iconPaint)

        // 外圈进度环（270度圆弧）
        val outerRadius = width / 2f - dp(6f)
        val rect = RectF(
            cx - outerRadius,
            cy - outerRadius,
            cx + outerRadius,
            cy + outerRadius
        )
        canvas.drawArc(rect, -90f, 270f, false, progressPaint)
    }

    /**
     * dp 转换为 px
     */
    private fun dp(value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics
        )
    }

    /**
     * sp 转换为 px
     */
    private fun sp(value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            value,
            resources.displayMetrics
        )
    }


    /**
     * 设置图标类型
     */
    fun setIcon(type: String) {
        this.actionType = type
        invalidate()
    }

    /**
     * 设置步数
     */
    fun setStepNumber(number: Int) {
        this.stepNumber = number
        invalidate()
    }

    fun setCenterLabel(label: String?) {
        this.centerLabel = label
        invalidate()
    }
}
