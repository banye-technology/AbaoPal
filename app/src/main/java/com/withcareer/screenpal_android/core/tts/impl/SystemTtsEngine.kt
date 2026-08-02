package com.withcareer.screenpal_android.core.tts.impl

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.withcareer.screenpal_android.core.tts.TtsEngine
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 系统TTS引擎实现
 * 使用Android系统原生的TextToSpeech API
 *
 */
class SystemTtsEngine : TtsEngine, TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready: Boolean = false
    private var currentLanguageStr: String = "zh"
    private val callbacks = ConcurrentHashMap<String, () -> Unit>()
    private var onReadyCallback: ((Boolean) -> Unit)? = null
    private var context: Context? = null

    override fun initialize(context: Context, onReady: (Boolean) -> Unit) {
        this.context = context
        this.onReadyCallback = onReady
        tts = TextToSpeech(context.applicationContext, this)

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                utteranceId?.let {
                    callbacks.remove(it)?.invoke()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                utteranceId?.let {
                    callbacks.remove(it)
                }
            }
        })
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            updateLanguage()
        }
        onReadyCallback?.invoke(ready)
        onReadyCallback = null
    }

    override fun speak(text: String, onDone: (() -> Unit)?) {
        if (!ready || text.isBlank()) {
            onDone?.invoke()
            return
        }
        val utteranceId = UUID.randomUUID().toString()
        if (onDone != null) {
            callbacks[utteranceId] = onDone
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    override fun speakWithFirstFrameCallback(
        text: String,
        onFirstFrame: (() -> Unit)?,
        onDone: (() -> Unit)?
    ) {
        if (!ready || text.isBlank()) {
            onFirstFrame?.invoke()
            onDone?.invoke()
            return
        }

        val currentUtteranceId = UUID.randomUUID().toString()

        // 创建带首帧回调的监听器
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // 系统TTS开始播放时立即触发首帧回调
                if (utteranceId == currentUtteranceId) {
                    onFirstFrame?.invoke()
                }
            }

            override fun onDone(utteranceId: String?) {
                utteranceId?.let {
                    if (it == currentUtteranceId) {
                        onDone?.invoke()
                    } else {
                        callbacks.remove(it)?.invoke()
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                utteranceId?.let {
                    if (it == currentUtteranceId) {
                        onFirstFrame?.invoke()
                        onDone?.invoke()
                    } else {
                        callbacks.remove(it)
                    }
                }
            }
        })

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, currentUtteranceId)
    }

    override fun setLanguage(language: String) {
        currentLanguageStr = language
        if (ready) {
            updateLanguage()
        }
    }

    private fun updateLanguage() {
        val locale = when (currentLanguageStr) {
            "en" -> Locale.US
            "zh" -> Locale.CHINA
            "system" -> Locale.getDefault()
            else -> Locale.CHINA
        }

        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // 如果不支持设定语言，尝试回退到英文
            if (locale != Locale.US) {
                tts?.language = Locale.US
            }
        }
    }

    override fun stop() {
        tts?.stop()
    }

    override fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        callbacks.clear()
        ready = false
        context = null
    }

    override fun getType(): String = "system"

    override fun isReady(): Boolean = ready
}
