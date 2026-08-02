package com.withcareer.screenpal_android.overlay

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.withcareer.screenpal_android.R
import com.withcareer.screenpal_android.util.OverlayViewUtils

/**
 * 悬浮交互管理器
 * 负责显示交互对话框（如多选一）和停止任务确认框
 */
class FloatingInteractionManager(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private var interactionView: View? = null

    private val isDarkMode: Boolean
        get() = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun showInteraction(message: String, choices: List<String>, onResult: (String?) -> Unit) {
        Handler(Looper.getMainLooper()).post {
            removeInteractionView()
            addInteractionView(message, choices, onResult)
        }
    }

    fun removeInteractionView() {
        if (interactionView != null) {
            try {
                windowManager.removeView(interactionView)
            } catch (e: Exception) {
                // ignore
            }
            interactionView = null
        }
    }

    fun setVisible(visible: Boolean) {
        if (visible) {
            OverlayViewUtils.fadeIn(interactionView, 1f)
        } else {
            OverlayViewUtils.fadeOut(interactionView)
        }
    }

    @Suppress("DEPRECATION")
    fun showStopDialog(onConfirmStop: () -> Unit) {
        val theme = if (isDarkMode) android.R.style.Theme_DeviceDefault_Dialog_Alert else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        val dialog = AlertDialog.Builder(context, theme)
            .setTitle(context.getString(R.string.task_stop_title))
            .setMessage(context.getString(R.string.task_stop_message))
            .setPositiveButton(context.getString(R.string.stop)) { _, _ ->
                onConfirmStop()
            }
            .setNegativeButton(context.getString(R.string.cancel), null)
            .create()

        dialog.window?.setType(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE)

        dialog.show()
    }

    @SuppressLint("SetTextI18n")
    private fun addInteractionView(message: String, choices: List<String>, onResult: (String?) -> Unit) {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = OverlayViewUtils.dpToPx(20).toFloat()
                val startColor = ContextCompat.getColor(context, R.color.floating_step_start)
                val endColor = ContextCompat.getColor(context, R.color.floating_step_end)
                colors = intArrayOf(startColor, endColor)
                gradientType = GradientDrawable.LINEAR_GRADIENT
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                setStroke(OverlayViewUtils.dpToPx(1), ContextCompat.getColor(context, R.color.floating_step_stroke))
            }
            setPadding(OverlayViewUtils.dpToPx(16), OverlayViewUtils.dpToPx(16), OverlayViewUtils.dpToPx(16), OverlayViewUtils.dpToPx(16))
            elevation = OverlayViewUtils.dpToPx(16).toFloat()
        }

        val titleView = TextView(context).apply {
            text = context.getString(R.string.app_name)
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.floating_step_title))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_HORIZONTAL
        }
        root.addView(titleView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = OverlayViewUtils.dpToPx(8)
        })

        val messageView = TextView(context).apply {
            text = message
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.floating_step_text))
            gravity = Gravity.CENTER
        }
        root.addView(messageView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = OverlayViewUtils.dpToPx(16)
        })

        if (choices.isNotEmpty()) {
            // 添加选项按钮
            choices.forEach { choice ->
                val button = Button(context).apply {
                    text = choice
                    setOnClickListener {
                        onResult(choice)
                        removeInteractionView()
                    }
                }
                root.addView(button, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            }
        } else {
            // 默认确认按钮
            val button = Button(context).apply {
                text = context.getString(R.string.continue_text)
                setOnClickListener {
                    onResult(null)
                    removeInteractionView()
                }
            }
            root.addView(button, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        val width = kotlin.math.min(OverlayViewUtils.dpToPx(300), (context.resources.displayMetrics.widthPixels * 0.8f).toInt())
        val params = OverlayViewUtils.createLayoutParams(
            width = width,
            height = WindowManager.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }

        try {
            windowManager.addView(root, params)
            interactionView = root
        } catch (e: Exception) {
            android.util.Log.e("FloatingAssistant", "addInteractionView: failed", e)
        }
    }
}
