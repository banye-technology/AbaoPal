package com.withcareer.screenpal_android.core.tts.impl

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisAudioFormat
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer
import com.alibaba.dashscope.audio.tts.SpeechSynthesisResult
import com.alibaba.dashscope.common.ResultCallback
import com.withcareer.screenpal_android.config.AppConfig
import com.withcareer.screenpal_android.core.tts.TtsEngine
import com.withcareer.screenpal_android.data.preference.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull
import java.nio.ByteBuffer

/**
 * 阿里云TTS引擎实现
 * 使用DashScope SDK的流式语音合成API (CosyVoice-v3-Flash模型)
 *
 * 特性：
 * - 流式合成：实时接收音频帧并播放，降低首包延迟
 * - 连接复用：预创建synthesizer实例，避免重复建立WebSocket连接
 * - 动态音色：支持运行时切换16种v3音色
 *
 */
class AliyunTtsEngine : TtsEngine {
    companion object {
        private const val TAG = "AliyunTtsEngine"
        private const val MODEL = "cosyvoice-v3-flash"
        private const val SAMPLE_RATE = 22050
        private const val CHANNEL_COUNT = 1
        private const val BYTES_PER_SAMPLE = 2
    }

    private var synthesizer: SpeechSynthesizer? = null
    private var ready: Boolean = false
    private var currentLanguage: String = "zh"
    private var apiKey: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var audioTrack: AudioTrack? = null
    private var context: Context? = null

    // 流式TTS状态管理
    private var param: SpeechSynthesisParam? = null
    private var currentCallback: (() -> Unit)? = null
    private var firstFrameCallback: (() -> Unit)? = null
    @Volatile
    private var isSpeaking = false
    private var synthesisStartTime: Long = 0
    @Volatile
    private var isFirstFrame = true
    private var currentVoice: String = AppConfig.DEFAULT_ALIYUN_VOICE

    /**
     * 创建流式回调处理器
     * 用于实时接收和播放音频帧
     */
    private fun createCallback(): ResultCallback<SpeechSynthesisResult> {
        return object : ResultCallback<SpeechSynthesisResult>() {
            override fun onEvent(result: SpeechSynthesisResult?) {
                result?.audioFrame?.let { frame ->
                    if (isFirstFrame) {
                        isFirstFrame = false
                        val delay = System.currentTimeMillis() - synthesisStartTime
                        Log.d(TAG, "First audio frame received in ${delay}ms")
                        audioTrack?.play()

                        // 触发首帧回调
                        firstFrameCallback?.invoke()
                        firstFrameCallback = null
                    }
                    playAudioFrame(frame)
                }
            }

            override fun onComplete() {
                val totalTime = System.currentTimeMillis() - synthesisStartTime
                Log.d(TAG, "Synthesis completed in ${totalTime}ms")
                isSpeaking = false
                audioTrack?.stop()
                audioTrack?.flush()
                currentCallback?.invoke()
                currentCallback = null
            }

            override fun onError(e: Exception?) {
                Log.e(TAG, "Synthesis error: ${e?.javaClass?.simpleName ?: "unknown"}")
                isSpeaking = false
                audioTrack?.stop()
                audioTrack?.flush()
                currentCallback?.invoke()
                currentCallback = null
            }
        }
    }

    override fun initialize(context: Context, onReady: (Boolean) -> Unit) {
        this.context = context
        val preferencesRepository = PreferencesRepository(context)

        scope.launch {
            try {
                // 优先使用本地存储的 API key
                val localApiKey = preferencesRepository.apiKey.firstOrNull()

                apiKey = localApiKey
                val finalApiKey = apiKey
                if (finalApiKey.isNullOrBlank()) {
                    Log.w(TAG, "API Key not configured")
                    ready = false
                    onReady(false)
                    return@launch
                }

                // 初始化AudioTrack
                initAudioTrack()

                // 获取并验证音色配置
                val savedVoice = preferencesRepository.getAliyunVoiceSync()
                currentVoice = try {
                    com.withcareer.screenpal_android.config.AliyunVoice.fromValue(savedVoice)
                    savedVoice
                } catch (e: Exception) {
                    Log.w(TAG, "Invalid saved voice configuration; using default")
                    AppConfig.DEFAULT_ALIYUN_VOICE
                }

                Log.d(TAG, "Creating SpeechSynthesizer")

                // 创建并预热synthesizer（用于连接复用）
                param = SpeechSynthesisParam.builder()
                    .apiKey(finalApiKey)
                    .model(MODEL)
                    .voice(currentVoice)
                    .format(SpeechSynthesisAudioFormat.PCM_22050HZ_MONO_16BIT)
                    .build()

                synthesizer = SpeechSynthesizer(param, createCallback())

                ready = true
                Log.d(TAG, "SpeechSynthesizer initialized successfully")
                onReady(true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize", e)
                ready = false
                onReady(false)
            }
        }
    }

    /**
     * 初始化AudioTrack用于音频播放
     */
    private fun initAudioTrack() {
        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        Log.d(TAG, "AudioTrack initialized (buffer: $bufferSize bytes)")
    }

    override fun speak(text: String, onDone: (() -> Unit)?) {
        if (!ready || text.isBlank()) {
            Log.w(TAG, "Cannot speak - ready: $ready, text blank: ${text.isBlank()}")
            onDone?.invoke()
            return
        }

        if (synthesizer == null) {
            Log.e(TAG, "Synthesizer not initialized")
            onDone?.invoke()
            return
        }

        // 停止当前播放
        val wasSpeaking = isSpeaking
        if (wasSpeaking) {
            Log.d(TAG, "Stopping previous synthesis")
            stop()
        }

        Log.d(TAG, "Speaking: ${text.length} chars")

        scope.launch {
            try {
                // 如果刚停止了上一个合成，给WebSocket连接50ms时间清理状态
                if (wasSpeaking) {
                    kotlinx.coroutines.delay(50)
                }

                isSpeaking = true
                currentCallback = onDone
                synthesisStartTime = System.currentTimeMillis()
                isFirstFrame = true

                // 准备AudioTrack
                audioTrack?.stop()
                audioTrack?.flush()

                // 调用流式合成（非阻塞，通过callback接收音频帧）
                synthesizer?.call(text)

            } catch (e: Exception) {
                Log.e(TAG, "Exception in speak()", e)
                isSpeaking = false
                currentCallback?.invoke()
                currentCallback = null
            }
        }
    }

    override fun speakWithFirstFrameCallback(
        text: String,
        onFirstFrame: (() -> Unit)?,
        onDone: (() -> Unit)?
    ) {
        if (!ready || text.isBlank()) {
            Log.w(TAG, "Cannot speak - ready: $ready, text blank: ${text.isBlank()}")
            onFirstFrame?.invoke()
            onDone?.invoke()
            return
        }

        if (synthesizer == null) {
            Log.e(TAG, "Synthesizer not initialized")
            onFirstFrame?.invoke()
            onDone?.invoke()
            return
        }

        val wasSpeaking = isSpeaking
        if (wasSpeaking) {
            Log.d(TAG, "Stopping previous synthesis")
            stop()
        }

        Log.d(TAG, "Speaking with first frame callback: ${text.length} chars")

        scope.launch {
            try {
                if (wasSpeaking) {
                    kotlinx.coroutines.delay(50)
                }

                isSpeaking = true
                firstFrameCallback = onFirstFrame
                currentCallback = onDone
                synthesisStartTime = System.currentTimeMillis()
                isFirstFrame = true

                audioTrack?.stop()
                audioTrack?.flush()

                synthesizer?.call(text)

            } catch (e: Exception) {
                Log.e(TAG, "Exception in speakWithFirstFrameCallback()", e)
                isSpeaking = false
                firstFrameCallback?.invoke()
                firstFrameCallback = null
                currentCallback?.invoke()
                currentCallback = null
            }
        }
    }

    /**
     * 播放单个音频帧
     */
    private fun playAudioFrame(frame: ByteBuffer) {
        try {
            if (!frame.hasRemaining()) {
                return
            }

            val audioData = ByteArray(frame.remaining())
            frame.get(audioData)

            val bytesWritten = audioTrack?.write(audioData, 0, audioData.size) ?: 0
            if (bytesWritten < audioData.size) {
                Log.w(TAG, "Partial write: $bytesWritten/${audioData.size} bytes")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play audio frame", e)
        }
    }

    override fun setLanguage(language: String) {
        currentLanguage = language
        Log.d(TAG, "TTS language configuration updated")
    }

    /**
     * 更新音色
     * 会重新创建synthesizer实例以应用新音色
     */
    fun updateVoice(voice: String) {
        if (!ready || apiKey.isNullOrBlank()) {
            Log.w(TAG, "Cannot update voice - engine not ready")
            return
        }

        // 验证音色有效性
        currentVoice = try {
            com.withcareer.screenpal_android.config.AliyunVoice.fromValue(voice)
            voice
        } catch (e: Exception) {
            Log.w(TAG, "Invalid voice configuration; using default")
            AppConfig.DEFAULT_ALIYUN_VOICE
        }

        Log.d(TAG, "Updating TTS voice configuration")

        scope.launch {
            try {
                // 停止当前播放
                if (isSpeaking) {
                    stop()
                }

                // 关闭旧的synthesizer
                try {
                    synthesizer?.duplexApi?.close(1000, "voice_update")
                } catch (e: Exception) {
                    // Ignore cleanup errors
                }

                // 重新创建synthesizer
                param = SpeechSynthesisParam.builder()
                    .apiKey(apiKey)
                    .model(MODEL)
                    .voice(currentVoice)
                    .format(SpeechSynthesisAudioFormat.PCM_22050HZ_MONO_16BIT)
                    .build()

                synthesizer = SpeechSynthesizer(param, createCallback())
                Log.d(TAG, "Voice updated successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update voice", e)
            }
        }
    }

    override fun stop() {
        try {
            isSpeaking = false
            audioTrack?.stop()
            audioTrack?.flush()
            currentCallback?.invoke()
            currentCallback = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop", e)
        }
    }

    override fun shutdown() {
        try {
            Log.d(TAG, "Shutting down")

            isSpeaking = false
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null

            currentCallback?.invoke()
            currentCallback = null

            // 关闭synthesizer
            try {
                synthesizer?.duplexApi?.close(1000, "shutdown")
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
            synthesizer = null
            param = null

            scope.cancel()
            ready = false
            context = null

            Log.d(TAG, "Shutdown completed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to shutdown", e)
        }
    }

    override fun getType(): String = "aliyun"

    override fun isReady(): Boolean = ready
}
