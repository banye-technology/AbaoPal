package com.withcareer.screenpal_android.overlay

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.withcareer.screenpal_android.R
import com.withcareer.screenpal_android.ScreenPalApplication
import com.withcareer.screenpal_android.data.preference.PreferencesRepository
import com.withcareer.screenpal_android.util.AccessibilityServiceHelper
import com.withcareer.screenpal_android.util.OverlayViewUtils
import com.withcareer.screenpal_android.util.ToastUtils
import com.withcareer.screenpal_android.core.voice.VoiceController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 悬浮面板管理器
 * 负责主面板的创建、显示和交互逻辑
 */
class FloatingPanelManager(
    private val context: Context,
    private val windowManager: WindowManager,
    private val scope: CoroutineScope,
    private val preferencesRepository: PreferencesRepository,
    private val callbacks: PanelCallbacks
) {
    /**
     * 面板回调接口
     * 用于处理面板的交互事件
     */
    interface PanelCallbacks {
        /**
         * 开始任务回调
         * @param text 用户输入的任务文本
         */
        fun onStartTask(text: String)

        /**
         * 停止任务回调
         */
        fun onStopTask()

        /**
         * 最小化面板回调
         */
        fun onMinimize()

        /**
         * 关闭服务回调
         */
        fun onCloseService()

        /**
         * 面板可见性变更回调
         * @param visible 是否可见
         */
        fun onPanelVisibilityChanged(visible: Boolean)

        /**
         * 输入框焦点变更回调
         * @param hasFocus 是否获得焦点
         */
        fun onFocusChange(hasFocus: Boolean)
    }

    var panelView: View? = null
        private set

    var inputField: EditText? = null
        private set

    private var micButton: ImageView? = null

    private var sendButton: ImageView? = null

    private var actionRow: FrameLayout? = null

    private val isDarkMode: Boolean
        get() = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private var inputHintDefault: String = ""

    private var voiceController: VoiceController? = null
    private var isVoiceInputting = false
    private var voiceInputBaseText: String = ""
    private var autoSubmitJob: Job? = null
    private var voiceJob: Job? = null
    private var isUpdatingFocus = false
    private val operationGuard: com.withcareer.screenpal_android.util.OperationGuard
    private var isFocusInterrupted = false
    private var shouldAcceptVoiceUpdates = true  // 标志位：是否接受语音更新


    private var lastFocusRequestTime: Long = 0

    init {
        voiceController = VoiceController.getInstance(context)
        operationGuard = com.withcareer.screenpal_android.util.OperationGuard(context)
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n", "AppCompatCustomView")
    fun addPanel() {
        if (panelView != null) return

        android.util.Log.d("FloatingPanelManager", "addPanel: preparing to add panel")

        val voiceRecognitionSupported = voiceController?.isAvailable() == true
        inputHintDefault = if (voiceRecognitionSupported) context.getString(R.string.input_hint) else context.getString(R.string.input_hint_simple)

        val root = object : LinearLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    if (inputField?.hasFocus() == true) {
                        inputField?.clearFocus()
                        updateFocusable(false)
                        return true
                    }
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = OverlayViewUtils.dpToPx(28).toFloat()
                // 使用与 TaskDetailScreen 一致的背景色
                val backgroundColor = ContextCompat.getColor(context, R.color.panel_background)
                setColor(backgroundColor)
            }
            setPadding(OverlayViewUtils.dpToPx(24), OverlayViewUtils.dpToPx(24), OverlayViewUtils.dpToPx(24), OverlayViewUtils.dpToPx(24))
            elevation = OverlayViewUtils.dpToPx(6).toFloat()
            // 初始设置为不可见，由 setVisible(true) 或 renderState 显式控制
            visibility = View.GONE
        }

        var lastKeypadHeight = 0
        root.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = android.graphics.Rect()
            root.getWindowVisibleDisplayFrame(rect)
            val screenHeight = context.resources.displayMetrics.heightPixels
            val keypadHeight = screenHeight - rect.bottom

            if (keypadHeight == lastKeypadHeight) return@addOnGlobalLayoutListener
            lastKeypadHeight = keypadHeight

            // 简单的键盘高度检测
            if (keypadHeight < screenHeight * 0.15) {
                // 键盘收起
                // 忽略刚刚请求焦点的情况，给键盘弹起预留时间
                if (System.currentTimeMillis() - lastFocusRequestTime < 1000) return@addOnGlobalLayoutListener

                if (inputField?.hasFocus() == true && !isUpdatingFocus) {
                    // 再次检查确认不是因为布局变化导致的瞬时误判
                    root.postDelayed({
                        val currentRect = android.graphics.Rect()
                        root.getWindowVisibleDisplayFrame(currentRect)
                        val currentKeypadHeight = screenHeight - currentRect.bottom
                        if (currentKeypadHeight < screenHeight * 0.15 && inputField?.hasFocus() == true && !isUpdatingFocus) {
                            inputField?.clearFocus()
                            updateFocusable(false)
                        }
                    }, 150)
                }
            }
        }

        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_OUTSIDE -> {
                    if (inputField?.hasFocus() == true) {
                        inputField?.clearFocus()
                        updateFocusable(false)
                    } else {
                        callbacks.onMinimize()
                    }
                    true
                }
                else -> false
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener { _, insets ->
                val imeVisible = insets.isVisible(WindowInsets.Type.ime())
                if (!imeVisible) {
                    if (inputField?.hasFocus() == true && !isUpdatingFocus) {
                        root.post {
                            if (inputField?.hasFocus() == true && !isUpdatingFocus && !isVoiceInputting) {
                                inputField?.clearFocus()
                                // 注意：这里不直接调 updateFocusable(false)，由 focusChangeListener 处理
                            }
                        }
                    }
                }
                insets
            }
        }

        setupHeader(root)
        setupInputArea(root, voiceRecognitionSupported)

        val width = kotlin.math.min(OverlayViewUtils.dpToPx(400), (context.resources.displayMetrics.widthPixels * 0.96f).toInt())
        val params = OverlayViewUtils.createLayoutParams(
            width = width,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = OverlayViewUtils.dpToPx(24)
            flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        }

        try {
            windowManager.addView(root, params)
            android.util.Log.d("FloatingPanelManager", "addPanel: panel added successfully")
            panelView = root
            bindVoice()
        } catch (e: Exception) {
            android.util.Log.e("FloatingPanelManager", "addPanel: failed to add panel", e)
            panelView = null
        }
    }

    private fun setupHeader(root: LinearLayout) {
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logo = ImageView(context).apply {
            setImageResource(R.drawable.logo_remove)
            clipToOutline = true
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = OverlayViewUtils.dpToPx(12).toFloat()
            }
            setPadding(OverlayViewUtils.dpToPx(4), OverlayViewUtils.dpToPx(4), OverlayViewUtils.dpToPx(4), OverlayViewUtils.dpToPx(4))
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, OverlayViewUtils.dpToPx(12).toFloat())
                }
            }
            layoutParams = LinearLayout.LayoutParams(OverlayViewUtils.dpToPx(52), OverlayViewUtils.dpToPx(52)).apply {
                marginEnd = OverlayViewUtils.dpToPx(8)
            }
        }
        val title = TextView(context).apply {
            text = context.getString(R.string.app_title_home)
            setTextColor(if (isDarkMode) Color.WHITE else Color.parseColor("#111827"))
            textSize = 20f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        }
        val minimizeButton = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(if (isDarkMode) Color.parseColor("#9CA3AF") else Color.parseColor("#6B7280"))
            layoutParams = LinearLayout.LayoutParams(OverlayViewUtils.dpToPx(24), OverlayViewUtils.dpToPx(24))
            setOnClickListener { callbacks.onMinimize() }
        }

        header.addView(logo)
        header.addView(title)
        header.addView(spacer)
        header.addView(minimizeButton)
        root.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = OverlayViewUtils.dpToPx(16)
        })
    }

    @SuppressLint("SetTextI18n")
    fun resetInput() {
        if (isVoiceInputting) {
            stopVoiceInput()
        }
        inputField?.setText("")
        voiceInputBaseText = ""
        isFocusInterrupted = false
        inputField?.hint = context.getString(R.string.input_hint)
    }

    @SuppressLint("SetTextI18n")
    fun startVoiceRecognition() {
        if (isVoiceInputting) return

        scope.launch {
            // 使用 OperationGuard 统一检查权限和状态
            if (!operationGuard.check(requireApiKey = true, requireRecordAudio = true, requireAccessibility = true)) {
                // 如果检查失败（例如没有 API Key），唤醒循环可能已经停止（因为 wakeupListener 被触发），
                // 但我们没有开始录音，所以必须重启唤醒，否则无法再次唤醒。
                voiceController?.restartWakeup()
                return@launch
            }

            // 检查悬浮窗是否可用
            if (panelView == null || micButton == null) {
                ToastUtils.show(context, context.getString(R.string.floating_permission_request), Toast.LENGTH_SHORT)
                // 即使没有悬浮窗，也要重启唤醒，否则变成一次性
                voiceController?.restartWakeup()
                return@launch
            }

            // 清空输入框，确保本次语音输入是全新的
            inputField?.setText("")
            voiceInputBaseText = ""
            isFocusInterrupted = false

            micButton?.let {
                it.alpha = 0.9f
                it.scaleX = 0.95f
                it.scaleY = 0.95f
                startVoiceInput()
                it.setImageResource(R.drawable.ic_stop)
                it.setColorFilter(Color.WHITE)
                it.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#B3261E"))
                }
            }
            inputField?.hint = context.getString(R.string.listening)
        }
    }

    @SuppressLint("ClickableViewAccessibility", "AppCompatCustomView")
    private fun setupInputArea(root: LinearLayout, voiceRecognitionSupported: Boolean) {
        val primaryColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getColor(android.R.color.system_accent1_600)
        } else {
            Color.parseColor("#6650a4")
        }

        val inputGroup = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = OverlayViewUtils.dpToPx(24).toFloat()
                setColor(if (isDarkMode) Color.parseColor("#1F2937") else Color.WHITE)
                setStroke(OverlayViewUtils.dpToPx(1), if (isDarkMode) Color.parseColor("#374151") else Color.parseColor("#D1D5DB"))
            }
            setPadding(OverlayViewUtils.dpToPx(8), OverlayViewUtils.dpToPx(8), OverlayViewUtils.dpToPx(8), OverlayViewUtils.dpToPx(8))
        }

        val inputContainer = FrameLayout(context)

        sendButton = ImageView(context).apply {
            setImageResource(R.drawable.ic_send)
            setColorFilter(primaryColor)
            visibility = View.GONE
            val size = OverlayViewUtils.dpToPx(48)
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                marginEnd = OverlayViewUtils.dpToPx(4)
            }
            setPadding(OverlayViewUtils.dpToPx(10), OverlayViewUtils.dpToPx(10), OverlayViewUtils.dpToPx(10), OverlayViewUtils.dpToPx(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
            }
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { v.alpha = 0.6f; false }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { v.alpha = 1.0f; false }
                    else -> false
                }
            }
            setOnClickListener { submitInput() }
        }

        inputField = object : EditText(context) {
            override fun onKeyPreIme(keyCode: Int, event: KeyEvent?): Boolean {
                if (keyCode == KeyEvent.KEYCODE_BACK && event?.action == KeyEvent.ACTION_UP) {
                    clearFocus()
                    updateFocusable(false)
                }
                return super.onKeyPreIme(keyCode, event)
            }
        }.apply {
            hint = inputHintDefault
            textSize = 16f
            setTextColor(if (isDarkMode) Color.parseColor("#E6E1E5") else Color.parseColor("#1C1B1F")) // OnSurfaceVariant
            setHintTextColor(if (isDarkMode) Color.parseColor("#8C8C8C") else Color.parseColor("#B0B0B0"))
            background = null
            minHeight = OverlayViewUtils.dpToPx(48)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 0)
            maxLines = 5
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE

            setOnFocusChangeListener { _, hasFocus ->
                if (isUpdatingFocus) return@setOnFocusChangeListener
                callbacks.onFocusChange(hasFocus)
                updateInputUiState(hasFocus)
                if (hasFocus) {
                    isFocusInterrupted = true
                    voiceController?.stopListening()
                }
            }
            setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        val params = panelView?.layoutParams as? WindowManager.LayoutParams
                        val isNotFocusable = (params?.flags ?: 0) and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0
                        if (isNotFocusable) {
                            updateFocusable(true)
                        } else {
                            // 已经是 focusable，但可能键盘未显示，强制显示
                            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                            inputField?.requestFocus()
                            imm.showSoftInput(inputField, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                        }
                        return@setOnTouchListener true
                    }
                    false
                }
        }
        inputContainer.addView(inputField, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        inputContainer.addView(sendButton)

        if (voiceRecognitionSupported) {
            micButton = ImageView(context).apply {
                setImageResource(R.drawable.ic_mic)
                setColorFilter(primaryColor)
                val size = OverlayViewUtils.dpToPx(48)
                layoutParams = FrameLayout.LayoutParams(size, size).apply {
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                    marginEnd = OverlayViewUtils.dpToPx(4)
                }
                setPadding(OverlayViewUtils.dpToPx(10), OverlayViewUtils.dpToPx(10), OverlayViewUtils.dpToPx(10), OverlayViewUtils.dpToPx(10))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                }
                setOnClickListener {
                    scope.launch {
                        if (!operationGuard.check(requireApiKey = true, requireRecordAudio = true)) {
                            return@launch
                        }

                        if (isVoiceInputting) {
                            // 手动停止，触发自动提交
                            stopVoiceInput()
                        } else {
                            // 开始录音
                            startVoiceRecognition()
                        }
                    }
                }
            }
            inputContainer.addView(micButton)
        }

        inputGroup.addView(inputContainer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        root.addView(inputGroup, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = OverlayViewUtils.dpToPx(4)
        })
    }

    private fun submitInput() {
        // 面板不可见时不提交，避免关闭后残留内容被执行
        if (!isVisible()) return

        val text = inputField?.text?.toString()?.trim() ?: return
        if (text.isBlank()) return

        scope.launch {
            if (!operationGuard.check(requireApiKey = true)) return@launch
            callbacks.onStartTask(text)
            inputField?.text?.clear()
            inputField?.clearFocus()
            updateFocusable(false)
        }
    }

    private fun updateInputUiState(hasFocus: Boolean) {
        if (hasFocus) {
            if (inputField?.hint == context.getString(R.string.auto_execute_hint)) {
                inputField?.hint = inputHintDefault
            }
            micButton?.visibility = View.GONE
            sendButton?.visibility = View.VISIBLE
            inputField?.setPadding(0, 0, OverlayViewUtils.dpToPx(48), 0)
        } else {
            micButton?.visibility = View.VISIBLE
            sendButton?.visibility = View.GONE
            if (micButton != null) {
                inputField?.setPadding(0, 0, OverlayViewUtils.dpToPx(48), 0)
            } else {
                inputField?.setPadding(0, 0, 0, 0)
            }
        }
    }

    fun updateFocusable(focusable: Boolean) {
        val view = panelView ?: return
        val params = view.layoutParams as WindowManager.LayoutParams
        val currentFlags = params.flags

        val secureFlag = if ((currentFlags and WindowManager.LayoutParams.FLAG_SECURE) != 0) {
            WindowManager.LayoutParams.FLAG_SECURE
        } else {
            0
        }

        if (focusable) {
            lastFocusRequestTime = System.currentTimeMillis()
            params.flags = (currentFlags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()) or secureFlag
            // 添加 FLAG_NOT_TOUCH_MODAL 允许点击外部事件穿透，这样可以更好地处理焦点失去逻辑
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

            // 改回手动位移，因为悬浮窗类型下 ADJUST_RESIZE 往往无效
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE

            // 手动抬高位置，避开键盘 (310dp)
            params.y = OverlayViewUtils.dpToPx(310)
        } else {
            // 确保移除 focusable 相关标志
            params.flags = (currentFlags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv() or secureFlag

            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            // 强制重置到底部位置
            params.y = OverlayViewUtils.dpToPx(24)
            params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }

        try {
            isUpdatingFocus = true
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) {
            android.util.Log.w("FloatingPanelManager", "Failed to update panel focus")
        } finally {
            isUpdatingFocus = false
        }

        // 手动触发 UI 更新，确保状态一致
        updateInputUiState(focusable)

        if (focusable) {
             inputField?.postDelayed({
                 val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                 inputField?.isFocusable = true
                 inputField?.isFocusableInTouchMode = true
                 inputField?.requestFocus()
                 imm.showSoftInput(inputField, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
             }, 200)
        }
    }

    /**
     * 更新面板位置，不改变焦点状态
     * @param yDp 距离底部的距离 (dp)
     */
    fun updatePosition(yDp: Int) {
        val view = panelView ?: return
        val params = view.layoutParams as WindowManager.LayoutParams
        params.y = OverlayViewUtils.dpToPx(yDp)

        try {
            isUpdatingFocus = true // 借用此标志位防止监听器误判键盘收起
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) {
            android.util.Log.w("FloatingPanelManager", "Failed to restore panel focus")
        } finally {
            isUpdatingFocus = false
        }
    }

    fun removePanel() {
        voiceJob?.cancel()
        autoSubmitJob?.cancel()

        if (inputField?.hasFocus() == true) {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(inputField?.windowToken, 0)
            inputField?.clearFocus()
        }

        panelView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        panelView = null
        inputField = null
        micButton = null
        sendButton = null
        actionRow = null
    }

    fun setVisible(visible: Boolean) {
        if (visible) {
            // 【关键】在重新打开面板前，先清空 VoiceController 的状态，防止 StateFlow 的最后值被立即收集
            voiceController?.cancel()

            // 允许接受语音更新
            shouldAcceptVoiceUpdates = true

            resetInput()
            panelView?.alpha = 1f
            panelView?.visibility = View.VISIBLE

            // 重新绑定 voiceJob，确保语音状态监听有效
            if (voiceJob == null || voiceJob?.isActive != true) {
                bindVoice()
            }

            callbacks.onPanelVisibilityChanged(true)
        } else {
            // 【关键】立即禁止接受语音更新，阻止 voiceJob 回调更新输入框
            shouldAcceptVoiceUpdates = false

            // 先设置面板不可见，阻止 submitInput() 执行
            panelView?.visibility = View.GONE

            // 立即取消自动提交任务
            autoSubmitJob?.cancel()
            autoSubmitJob = null

            // 【关键修复】强制取消 VoiceController 的识别，防止后续状态更新触发自动提交
            voiceController?.cancel()

            // 取消 voiceJob，防止 VoiceController 的状态回调触发 stopVoiceInput()
            voiceJob?.cancel()
            voiceJob = null

            // 停止语音并取消自动提交
            stopVoiceInput(cancelAutoSubmit = true)

            // 【关键修复】无论是否有焦点，隐藏时都强制重置位置和 focusable 状态
            // 确保下次打开时在底部，而不是停留在键盘上方
            if (inputField?.hasFocus() == true) {
                inputField?.clearFocus()
            }
            updateFocusable(false)

            // 清空输入框和语音输入基础文本
            inputField?.setText("")

            callbacks.onPanelVisibilityChanged(false)
        }
    }

    fun isVisible(): Boolean {
        return panelView?.visibility == View.VISIBLE
    }

    fun destroy() {
        voiceJob?.cancel()
        autoSubmitJob?.cancel()
        voiceController?.destroy()
        removePanel()
    }

    private fun bindVoice() {
        val vc = voiceController ?: return
        voiceJob = scope.launch {
            vc.uiState.collectLatest { state ->
                // 【关键】即使收到状态更新，也要检查是否应该处理
                if (!shouldAcceptVoiceUpdates) return@collectLatest
                // 如果正在监听、处理或用户正在输入语音，取消之前的自动提交任务
                if (state.isListening || state.isProcessing || isVoiceInputting) {
                    autoSubmitJob?.cancel()
                    if (inputField?.hint == context.getString(R.string.about_to_execute)) {
                        inputField?.hint = inputHintDefault
                    }
                }

                // 动效更新
                if (state.isListening) {
                    // 保持提示语状态
                } else if (state.isProcessing) {
                    inputField?.hint = context.getString(R.string.recognizing)
                } else if (!isVoiceInputting) {
                    if (inputField?.hint != context.getString(R.string.about_to_execute)) {
                        inputField?.hint = inputHintDefault
                    }
                }

                if (state.error != null) {
                    ToastUtils.show(context, state.error, Toast.LENGTH_SHORT)
                }

                // 监听 VoiceController 的状态变化，如果它自动停止了（例如 VAD），我们需要同步状态并触发提交
                if (!state.isListening && !state.isProcessing && isVoiceInputting) {
                    stopVoiceInput()
                }

                // 只有在允许接受语音更新时才更新输入框，避免关闭面板后残留文本被填入
                if (!state.finalText.isNullOrBlank() && shouldAcceptVoiceUpdates) {
                    val separator = if (voiceInputBaseText.isNotEmpty() && !voiceInputBaseText.endsWith(" ")) " " else ""
                    val newText = "${voiceInputBaseText}${separator}${state.finalText}"
                    inputField?.setText(newText)
                    inputField?.setSelection(inputField?.text?.length ?: 0)
                }
            }
        }
    }

    private fun startVoiceInput() {
        if (!AccessibilityServiceHelper.isServiceConnected.value) {
            ToastUtils.show(context, context.getString(R.string.please_enable_accessibility), Toast.LENGTH_LONG)
            return
        }

        val vc = voiceController ?: return

        if (!vc.isAvailable()) {
            ToastUtils.show(context, context.getString(R.string.no_microphone_support), Toast.LENGTH_LONG)
            return
        }

        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ToastUtils.show(context, context.getString(R.string.please_grant_record_audio), Toast.LENGTH_SHORT)
            return
        }

        isVoiceInputting = true
        inputField?.clearFocus()

        voiceInputBaseText = inputField?.text?.toString() ?: ""

        inputField?.hint = context.getString(R.string.listening)

        vc.startListening()
    }

    private fun stopVoiceInput(cancelAutoSubmit: Boolean = false) {
        // 先检查面板可见性，再检查 isVoiceInputting
        // 如果面板不可见且不是强制取消，说明是 voiceJob 回调触发的但面板已关闭，直接返回
        if (!isVisible() && !cancelAutoSubmit) {
            isVoiceInputting = false
            voiceController?.stopListening()
            return
        }

        // 如果不是语音输入状态，直接返回
        if (!isVoiceInputting) return

        isVoiceInputting = false
        voiceController?.stopListening()

        // 更新 UI 状态
        val micBtn = micButton
        if (micBtn != null) {
            micBtn.alpha = 1.0f
            micBtn.scaleX = 1.0f
            micBtn.scaleY = 1.0f
            micBtn.setImageResource(R.drawable.ic_mic)
            micBtn.setColorFilter(Color.WHITE)
            val primaryColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getColor(android.R.color.system_accent1_600)
            } else {
                Color.parseColor("#6650a4")
            }
            micBtn.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(primaryColor)
            }
        }

        // 只有在不是因为焦点变化打断的情况下，且允许自动提交且面板可见时，才自动提交
        if (!cancelAutoSubmit && !isFocusInterrupted && isVisible()) {
            if (autoSubmitJob?.isActive != true) {
                autoSubmitJob = scope.launch {
                    inputField?.hint = context.getString(R.string.about_to_execute)
                    delay(500)
                    // 延迟后再次检查面板是否仍然可见，避免在延迟期间面板被关闭
                    if (isVisible()) {
                        submitInput()
                    }
                    inputField?.hint = inputHintDefault
                }
            }
        }
        isFocusInterrupted = false // 重置状态
    }
}
