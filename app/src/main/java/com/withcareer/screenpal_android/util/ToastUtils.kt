package com.withcareer.screenpal_android.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.lang.ref.WeakReference

/**
 * Toast工具类，统一管理Toast显示
 */
object ToastUtils {

    private var currentToastView: WeakReference<View>? = null
    private val handler = Handler(Looper.getMainLooper())

    /**
     * 显示居中的Toast
     * 如果有悬浮窗权限，使用WindowManager实现完全自定义的居中效果
     * 否则回退到系统Toast（在Android 11+可能无法居中）
     * @param context 上下文
     * @param text 显示文本
     * @param duration 显示时长，默认短时间
     */
    fun show(context: Context, text: String, duration: Int = Toast.LENGTH_SHORT) {
        if (Settings.canDrawOverlays(context)) {
            showOverlayToast(context, text, duration)
        } else {
            showSystemToast(context, text, duration)
        }
    }

    private fun showSystemToast(context: Context, text: String, duration: Int) {
        handler.post {
            try {
                // 使用 applicationContext 防止 Activity/Service 上下文失效导致的问题
                // 但某些主题样式可能需要 Activity 上下文，优先尝试传入的 context
                val toast = try {
                    Toast.makeText(context, text, duration)
                } catch (e: Exception) {
                    Toast.makeText(context.applicationContext, text, duration)
                }

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    toast.setGravity(Gravity.CENTER, 0, 0)
                }
                toast.show()
            } catch (e: Exception) {
                Log.e("ToastUtils", "Show system toast failed", e)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun showOverlayToast(context: Context, text: String, duration: Int) {
        handler.post {
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post

                // 移除上一个 Toast
                currentToastView?.get()?.let { view ->
                    try {
                        wm.removeView(view)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                currentToastView = null

                // 创建新的 Toast View
                val textView = TextView(context).apply {
                    this.text = text
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    setPadding(dpToPx(context, 24), dpToPx(context, 12), dpToPx(context, 24), dpToPx(context, 12))
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#CC000000")) // 半透明黑色背景
                        cornerRadius = dpToPx(context, 24).toFloat()
                    }
                    gravity = Gravity.CENTER
                }

                val params = WindowManager.LayoutParams().apply {
                    width = WindowManager.LayoutParams.WRAP_CONTENT
                    height = WindowManager.LayoutParams.WRAP_CONTENT
                    type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        WindowManager.LayoutParams.TYPE_PHONE
                    }
                    flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    format = PixelFormat.TRANSLUCENT
                    gravity = Gravity.CENTER
                    // 添加动画效果
                    windowAnimations = android.R.style.Animation_Toast
                }

                try {
                    wm.addView(textView, params)
                    currentToastView = WeakReference(textView)

                    // 定时移除
                    val delay = if (duration == Toast.LENGTH_LONG) 3500L else 2000L
                    handler.postDelayed({
                        try {
                            if (currentToastView?.get() == textView) {
                                wm.removeView(textView)
                                currentToastView = null
                            }
                        } catch (e: Exception) {
                            // ignore
                        }
                    }, delay)
                } catch (e: Exception) {
                    Log.e("ToastUtils", "Add overlay view failed", e)
                    // 如果添加失败，回退到系统 Toast
                    showSystemToast(context, text, duration)
                }
            } catch (e: Exception) {
                Log.e("ToastUtils", "Show overlay toast failed", e)
            }
        }
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    /**
     * 显示带链接的 Toast
     * @param context 上下文
     * @param text 主要文本
     * @param linkText 链接文本
     * @param url 点击后打开的 URL
     * @param duration 显示时长
     */
    fun showWithLink(context: Context, text: String, linkText: String, url: String, duration: Int = Toast.LENGTH_LONG) {
        if (Settings.canDrawOverlays(context)) {
            showOverlayToastWithLink(context, text, linkText, url, duration)
        } else {
            // 没有悬浮窗权限时，显示普通 Toast 并尝试打开链接
            showSystemToast(context, "$text $linkText", duration)
            tryOpenUrl(context, url)
        }
    }

    private fun showOverlayToastWithLink(context: Context, text: String, linkText: String, url: String, duration: Int) {
        handler.post {
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager ?: return@post

                // 移除上一个 Toast
                currentToastView?.get()?.let { view ->
                    try {
                        wm.removeView(view)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                currentToastView = null

                // 创建容器
                val container = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dpToPx(context, 20), dpToPx(context, 12), dpToPx(context, 16), dpToPx(context, 12))
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#E6000000"))
                        cornerRadius = dpToPx(context, 24).toFloat()
                    }
                }

                // 文本
                val textView = TextView(context).apply {
                    this.text = text
                    setTextColor(Color.WHITE)
                    textSize = 14f
                }
                container.addView(textView)

                // 链接按钮
                val linkView = TextView(context).apply {
                    this.text = linkText
                    setTextColor(Color.parseColor("#4FC3F7"))
                    textSize = 14f
                    setPadding(dpToPx(context, 8), 0, dpToPx(context, 4), 0)
                    setOnClickListener {
                        tryOpenUrl(context, url)
                    }
                }
                container.addView(linkView)

                val params = android.view.WindowManager.LayoutParams().apply {
                    width = android.view.WindowManager.LayoutParams.WRAP_CONTENT
                    height = android.view.WindowManager.LayoutParams.WRAP_CONTENT
                    type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        android.view.WindowManager.LayoutParams.TYPE_PHONE
                    }
                    flags = android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    format = PixelFormat.TRANSLUCENT
                    gravity = Gravity.CENTER
                    windowAnimations = android.R.style.Animation_Toast
                }

                try {
                    wm.addView(container, params)
                    currentToastView = WeakReference(container)

                    val delay = if (duration == Toast.LENGTH_LONG) 4000L else 2500L
                    handler.postDelayed({
                        try {
                            if (currentToastView?.get() == container) {
                                wm.removeView(container)
                                currentToastView = null
                            }
                        } catch (e: Exception) {
                            // ignore
                        }
                    }, delay)
                } catch (e: Exception) {
                    Log.e("ToastUtils", "Add overlay view with link failed", e)
                    showSystemToast(context, "$text $linkText", duration)
                }
            } catch (e: Exception) {
                Log.e("ToastUtils", "Show overlay toast with link failed", e)
            }
        }
    }

    private fun tryOpenUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("ToastUtils", "Failed to open configured URL")
        }
    }
}
