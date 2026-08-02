package com.withcareer.screenpal_android.core.tts

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.withcareer.screenpal_android.config.AppConfig
import com.withcareer.screenpal_android.config.TtsProvider
import com.withcareer.screenpal_android.core.tts.impl.AliyunTtsEngine
import com.withcareer.screenpal_android.core.tts.impl.SystemTtsEngine
import com.withcareer.screenpal_android.data.preference.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * TTS管理器（单例）
 * 负责管理和切换不同的TTS引擎
 *
 */
class TtsManager private constructor(private val context: Context) {
    companion object {
        private const val TAG = "TtsManager"

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: TtsManager? = null

        fun getInstance(context: Context): TtsManager {
            return instance ?: synchronized(this) {
                instance ?: TtsManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private var currentEngine: TtsEngine? = null
    private val preferencesRepository = PreferencesRepository(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentLanguage: String = "zh"
    private var isInitializing = false

    init {
        // 先初始化系统 TTS 作为备选
        Log.d(TAG, "Initializing fallback System TTS Engine")
        val fallbackEngine = SystemTtsEngine()
        fallbackEngine.initialize(context) { success ->
            if (success) {
                currentEngine = fallbackEngine
                Log.d(TAG, "System TTS Engine initialized as fallback")
            }
        }

        // 监听语言变化
        scope.launch {
            preferencesRepository.language.collectLatest { lang ->
                currentLanguage = lang
                currentEngine?.setLanguage(lang)
            }
        }

        // 检查是否有可用的 API Key
        scope.launch {
            val hasApiKey = preferencesRepository.apiKey.firstOrNull()?.isNotBlank() == true

            if (hasApiKey) {
                Log.d(TAG, "API Key available, switching to Aliyun TTS Engine")
                switchEngine(TtsProvider.ALIYUN.value)
            } else {
                Log.w(TAG, "No API Key available, using System TTS Engine")
            }

            // 监听 TTS Provider 变化
            preferencesRepository.ttsProvider.collectLatest { provider ->
                Log.d(TAG, "TTS provider configuration changed")
                // 如果有阿里云 API Key，始终使用阿里云 TTS，忽略本地存储的 provider 值
                val currentHasApiKey = preferencesRepository.apiKey.firstOrNull()?.isNotBlank() == true
                if (currentHasApiKey && provider != TtsProvider.ALIYUN.value) {
                    Log.d(TAG, "API Key available, ignoring provider change, keeping Aliyun TTS")
                    return@collectLatest
                }
                switchEngine(provider)
            }
        }
    }

    private fun switchEngine(provider: String) {
        if (isInitializing) {
            Log.d(TAG, "Engine is initializing, skip switch")
            return
        }

        isInitializing = true

        // 释放旧引擎
        currentEngine?.shutdown()
        currentEngine = null

        // 创建新引擎
        val newEngine = when (provider) {
            TtsProvider.ALIYUN.value -> {
                Log.d(TAG, "Switching to Aliyun TTS Engine")
                AliyunTtsEngine()
            }
            TtsProvider.SYSTEM.value -> {
                Log.d(TAG, "Switching to System TTS Engine")
                SystemTtsEngine()
            }
            else -> {
                Log.d(TAG, "Unknown provider, using System TTS Engine")
                SystemTtsEngine()
            }
        }

        // 初始化新引擎
        newEngine.initialize(context) { success ->
            isInitializing = false
            if (success) {
                currentEngine = newEngine
                currentEngine?.setLanguage(currentLanguage)
                Log.d(TAG, "TTS Engine initialized successfully")
            } else {
                Log.e(TAG, "Failed to initialize TTS Engine")
                // 如果阿里云TTS初始化失败，降级到系统TTS
                if (newEngine.getType() == "aliyun") {
                    Log.d(TAG, "Falling back to System TTS Engine")
                    val fallbackEngine = SystemTtsEngine()
                    fallbackEngine.initialize(context) { fallbackSuccess ->
                        if (fallbackSuccess) {
                            currentEngine = fallbackEngine
                            currentEngine?.setLanguage(currentLanguage)
                            Log.d(TAG, "Fallback to System TTS Engine successful")
                        }
                    }
                }
            }
        }
    }

    /**
     * 语音播报
     * @param text 要播报的文本
     * @param onDone 播报完成回调（可选）
     */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (currentEngine == null) {
            Log.w(TAG, "TTS Engine is null, cannot speak")
            onDone?.invoke()
            return
        }

        if (!currentEngine!!.isReady()) {
            Log.w(TAG, "TTS Engine not ready, cannot speak")
            onDone?.invoke()
            return
        }

        Log.d(TAG, "Speaking: textLength=${text.length}")
        currentEngine?.speak(text, onDone)
    }

    /**
     * 语音播报（带首帧回调）
     * @param text 要播报的文本
     * @param onFirstFrame 首帧音频到达回调（可选）
     * @param onDone 播报完成回调（可选）
     */
    fun speakWithFirstFrameCallback(
        text: String,
        onFirstFrame: (() -> Unit)? = null,
        onDone: (() -> Unit)? = null
    ) {
        if (currentEngine == null) {
            Log.w(TAG, "TTS Engine is null, cannot speak")
            onFirstFrame?.invoke()
            onDone?.invoke()
            return
        }

        if (!currentEngine!!.isReady()) {
            Log.w(TAG, "TTS Engine not ready, cannot speak")
            onFirstFrame?.invoke()
            onDone?.invoke()
            return
        }

        Log.d(TAG, "Speaking with first frame callback: textLength=${text.length}")
        currentEngine?.speakWithFirstFrameCallback(text, onFirstFrame, onDone)
    }

    /**
     * 停止当前播放
     */
    fun stop() {
        currentEngine?.stop()
    }

    /**
     * 释放资源
     */
    fun shutdown() {
        currentEngine?.shutdown()
        currentEngine = null
        scope.cancel()
    }

    /**
     * 检查引擎是否就绪
     */
    fun isReady(): Boolean {
        return currentEngine?.isReady() ?: false
    }

    /**
     * 获取当前引擎类型
     */
    fun getCurrentEngineType(): String? {
        return currentEngine?.getType()
    }

    /**
     * 更新阿里云音色
     * 仅在当前引擎为 AliyunTtsEngine 时生效
     */
    fun updateAliyunVoice(voice: String) {
        val engine = currentEngine
        if (engine is AliyunTtsEngine) {
            engine.updateVoice(voice)
            Log.d(TAG, "Updated Aliyun voice configuration")
        } else {
            Log.w(TAG, "Current engine is not AliyunTtsEngine, cannot update voice")
        }
    }
}
