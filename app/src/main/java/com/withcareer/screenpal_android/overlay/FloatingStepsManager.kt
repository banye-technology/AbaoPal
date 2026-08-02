package com.withcareer.screenpal_android.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.gson.JsonParser
import com.withcareer.screenpal_android.R
import com.withcareer.screenpal_android.core.model.ChatUiState
import com.withcareer.screenpal_android.core.model.MessageRole
import com.withcareer.screenpal_android.util.OverlayViewUtils

/**
 * 步骤提示框管理器 - 已优化点击穿透
 * 将停止按钮和文字信息拆分为两个窗口：按钮可点击，文字全穿透。
 */
class FloatingStepsManager(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onStopTask: () -> Unit
) {

    private var stopButtonView: View? = null
    private var textContainerView: View? = null
    private var stepsDialogText: TextView? = null

    private val isDarkMode: Boolean
        get() = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    @SuppressLint("SetTextI18n")
    fun addStepsDialog() {
        if (stopButtonView != null) return

        val screenWidth = context.resources.displayMetrics.widthPixels
        val btnSize = OverlayViewUtils.dpToPx(32)
        val btnRadius = btnSize / 2 // 16dp
        val textWidth = OverlayViewUtils.dpToPx(240) // 文字区域宽度

        // 胶囊宽度：从按钮中心开始，包含按钮右半部分 + 间距 + 文字 + 右padding
        val capsuleWidth = btnRadius + OverlayViewUtils.dpToPx(8) + textWidth + OverlayViewUtils.dpToPx(16)

        // 整体组合实际占用宽度（按钮左半部分 + 胶囊）
        val totalWidth = btnRadius + capsuleWidth

        // 整体组合的左边界（使组合居中）
        val comboLeftEdge = (screenWidth - totalWidth) / 2

        // 1. 创建文字背景胶囊，左边缘对齐按钮右侧
        val textRoot = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                // 只在右侧使用圆角，左侧使用小圆角以贴合按钮
                cornerRadii = floatArrayOf(
                    OverlayViewUtils.dpToPx(100).toFloat(), OverlayViewUtils.dpToPx(100).toFloat(), // 左上
                    OverlayViewUtils.dpToPx(100).toFloat(), OverlayViewUtils.dpToPx(100).toFloat(), // 右上
                    OverlayViewUtils.dpToPx(100).toFloat(), OverlayViewUtils.dpToPx(100).toFloat(), // 右下
                    OverlayViewUtils.dpToPx(100).toFloat(), OverlayViewUtils.dpToPx(100).toFloat()  // 左下
                )
                val startColor = ContextCompat.getColor(context, R.color.floating_step_start)
                val endColor = ContextCompat.getColor(context, R.color.floating_step_end)
                colors = intArrayOf(startColor, endColor)
                setStroke(OverlayViewUtils.dpToPx(1), ContextCompat.getColor(context, R.color.floating_step_stroke))
            }
            // 左边：按钮半径 + 间距，右边：padding
            setPadding(btnRadius + OverlayViewUtils.dpToPx(8), OverlayViewUtils.dpToPx(6), OverlayViewUtils.dpToPx(16), OverlayViewUtils.dpToPx(6))
            elevation = OverlayViewUtils.dpToPx(2).toFloat() // 降低层级，确保在按钮之下
        }

        stepsDialogText = TextView(context).apply {
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.floating_step_text))
            text = context.getString(R.string.ready)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setSingleLine(true)
        }
        textRoot.addView(stepsDialogText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, // 填充父容器
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // 2. 创建独立的停止按钮
        val stopButton = android.widget.ImageView(context).apply {
            setImageResource(R.drawable.ic_stop)
            setColorFilter(Color.RED)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ContextCompat.getColor(context, R.color.bubble_background))
                setStroke(OverlayViewUtils.dpToPx(1), ContextCompat.getColor(context, R.color.bubble_stroke))
            }
            val padding = OverlayViewUtils.dpToPx(6)
            setPadding(padding, padding, padding, padding)
            elevation = OverlayViewUtils.dpToPx(6).toFloat()
            isClickable = true
            setOnClickListener { onStopTask() }
        }

        // 按钮窗口：位于组合最左侧
        val btnCenterX = comboLeftEdge + btnRadius // 按钮中心 = 组合左边界 + 按钮半径
        val btnOffsetX = btnCenterX - (screenWidth / 2)

        // 文字胶囊窗口：左边界对齐按钮中心（即按钮的中点）
        val capsuleLeftEdge = comboLeftEdge + btnRadius // 胶囊左边界 = 按钮中心
        val capsuleCenterX = capsuleLeftEdge + capsuleWidth / 2
        val capsuleOffsetX = capsuleCenterX - (screenWidth / 2)

        val textParams = OverlayViewUtils.createLayoutParams(
            capsuleWidth, // 使用固定宽度
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE // 完全穿透
        ).apply {
            // 移除可能存在的 FLAG_NOT_FOCUSABLE (虽然 createLayoutParams 可能添加了它，
            // 但我们要确保 NOT_TOUCHABLE 生效，且有时候 NOT_FOCUSABLE 会影响某些机型的穿透行为，
            // 这里显式设置 flags 确保万无一失)
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = OverlayViewUtils.dpToPx(48)
            x = capsuleOffsetX
            alpha = 0.95f
        }

        val btnParams = OverlayViewUtils.createLayoutParams(
            btnSize, btnSize
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = OverlayViewUtils.dpToPx(48)
            x = btnOffsetX
            alpha = 1.0f
        }

        try {
            windowManager.addView(textRoot, textParams)
            windowManager.addView(stopButton, btnParams)
            textContainerView = textRoot
            stopButtonView = stopButton

            textRoot.visibility = View.GONE
            stopButton.visibility = View.GONE
        } catch (e: Exception) {
            android.util.Log.e("FloatingStepsManager", "Failed to add views", e)
        }
    }

    fun removeStepsDialog() {
        textContainerView?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
        stopButtonView?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
        textContainerView = null
        stopButtonView = null
        stepsDialogText = null
    }

    fun updateUiFromState(state: ChatUiState) {
        val lastAssistant = state.messages.lastOrNull { it.role == MessageRole.ASSISTANT }
        val thinking = state.thinkingContent.ifBlank {
            lastAssistant?.thinking?.trim().orEmpty()
        }

        val textView = stepsDialogText ?: return

        if (state.error != null) {
            textView.text = context.getString(R.string.error_prefix, state.error)
            textView.setTextColor(Color.RED)
        } else {
            textView.setTextColor(ContextCompat.getColor(context, R.color.floating_step_text))

            if (state.isLoading) {
                if (state.thinkingContent.isBlank()) {
                    textView.text = "规划中..."
                } else {
                    displayThinking(state.thinkingContent)
                }
            } else if (state.taskCompletedMessage != null) {
                textView.text = context.getString(R.string.task_completed)
            } else if (thinking.isNotBlank()) {
                displayThinking(thinking)
            } else {
                textView.text = context.getString(R.string.ready)
            }
        }

        // 动态调整按钮位置（可选：根据文字长度简单估算，或者保持固定位置）
        // 这里暂时保持固定位置，确保它在左侧
    }

    private fun displayThinking(thinking: String) {
        val cleanThinking = if (thinking.trim().startsWith("{") && thinking.trim().endsWith("}")) {
            try {
                val json = JsonParser.parseString(thinking).asJsonObject
                json.get("thought")?.asString ?: json.get("thinking")?.asString ?: thinking
            } catch (e: Exception) {
                thinking
            }
        } else {
            thinking
        }
        stepsDialogText?.text = cleanThinking
    }

    fun setVisible(visible: Boolean) {
        if (visible) {
            OverlayViewUtils.fadeIn(textContainerView, 1f)
            OverlayViewUtils.fadeIn(stopButtonView, 1f)
        } else {
            OverlayViewUtils.fadeOut(textContainerView)
            OverlayViewUtils.fadeOut(stopButtonView)
        }
    }

    fun isVisible(): Boolean {
        return textContainerView?.visibility == View.VISIBLE
    }

    fun reset() {
        stepsDialogText?.text = "分析中..."
    }
}
