package com.withcareer.screenpal_android.recording

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.TypedValue
import android.view.View

/**
 * 滑动路径绘制视图（虚线 + 起点标记）
 */
class SwipePathView(
    context: Context,
    private val points: List<Pair<Int, Int>>
) : View(context) {

    companion object {
        private const val PATH_COLOR = "#2196F3"
        private const val PATH_STROKE_DP = 3f
        private const val DASH_ON_DP = 10f
        private const val DASH_OFF_DP = 6f
        private const val START_RADIUS_DP = 5f
    }

    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor(PATH_COLOR)
        strokeWidth = dp(PATH_STROKE_DP)
        pathEffect = DashPathEffect(floatArrayOf(dp(DASH_ON_DP), dp(DASH_OFF_DP)), 0f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val startPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor(PATH_COLOR)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.size < 2) return

        val path = Path()
        points.forEachIndexed { index, (x, y) ->
            if (index == 0) {
                path.moveTo(x.toFloat(), y.toFloat())
            } else {
                path.lineTo(x.toFloat(), y.toFloat())
            }
        }

        canvas.drawPath(path, pathPaint)

        val (sx, sy) = points.first()
        canvas.drawCircle(sx.toFloat(), sy.toFloat(), dp(START_RADIUS_DP), startPaint)
    }

    private fun dp(value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics
        )
    }
}
