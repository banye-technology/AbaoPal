package com.withcareer.screenpal_android.core.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import com.withcareer.screenpal_android.R
import com.withcareer.screenpal_android.config.AsrModel
import com.withcareer.screenpal_android.data.preference.PreferencesRepository
import android.provider.Settings
import android.widget.Toast
import com.withcareer.screenpal_android.util.ToastUtils

import android.annotation.SuppressLint
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.media.audiofx.NoiseSuppressor
import com.alibaba.dashscope.audio.asr.recognition.Recognition
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam
import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantLock
import kotlinx.coroutines.flow.collectLatest
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * 语音控制器
 * 使用本地录音 + 实时流式语音识别API (DashScope SDK) + 本地唤醒 (Sherpa-onnx)
 *
 */
class VoiceController private constructor(private val context: Context) {
    companion object {
        private const val TAG = "VoiceController"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        private const val MAX_START_SILENCE = 5000L
        private const val MAX_TRAILING_SILENCE = 1500L

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: VoiceController? = null

        fun getInstance(context: Context): VoiceController {
            return instance ?: synchronized(this) {
                instance ?: VoiceController(context.applicationContext).also { instance = it }
            }
        }
    }

    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var recordingJob: Job? = null

    @Volatile
    private var lastVoiceTime = 0L
    @Volatile
    private var hasVoiceStarted = false
    private var silenceCheckJob: Job? = null

    private val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2

    private var audioRecord: AudioRecord? = null
    @Volatile
    private var isRecording = false

    private val preferencesRepository = PreferencesRepository(context)
    private val gson = Gson()
    private val voiceprintManager = VoiceprintManager(context)

    private var keywordSpotter: KeywordSpotter? = null
    @Volatile
    private var isKwsInitialized = false
    @Volatile
    private var isWakeupEnabledPref = false
    @Volatile
    private var isActivatedPref = false
    @Volatile
    private var hasApiKeyPref = false
    @Volatile
    private var isWakeupRunning = false
    private var wakeupJob: Job? = null

    // Callback for wakeup
    private val wakeupListeners = CopyOnWriteArrayList<() -> Boolean>()

    // Concurrency control for wakeup
    private val activeWakeupJobs = CopyOnWriteArrayList<Job>()
    private val wakeupRecordLock = Any()
    private var wakeupAudioRecord: AudioRecord? = null
    private var wakeupNoiseSuppressor: NoiseSuppressor? = null
    private val kwsLock = ReentrantLock()

    // 麦克风占用状态
    @Volatile
    private var isMicrophoneOccupiedByOther = false

    private val audioManager by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    // 音频焦点管理
    private var audioFocusRequest: AudioFocusRequest? = null // Android 8.0+
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.i(TAG, "Audio focus lost")
                // 失去焦点时停止录音
                if (isRecording) {
                    stopListening()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.i(TAG, "Audio focus lost transient")
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.i(TAG, "Audio focus gained")
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        Log.d(TAG, "Requesting audio focus...")
        val res: Int
        val playbackAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(playbackAttributes)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .build()

        audioFocusRequest = request
        res = audioManager.requestAudioFocus(request)

        val granted = res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.i(TAG, "Audio focus request granted=$granted")
        return granted
    }

    private fun abandonAudioFocus() {
        Log.d(TAG, "Abandoning audio focus...")
        val res: Int
        val request = audioFocusRequest
        if (request != null) {
            res = audioManager.abandonAudioFocusRequest(request)
        } else {
            Log.w(TAG, "abandonAudioFocus: audioFocusRequest is null, skipping.")
            return
        }

        val granted = res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.i(TAG, "Audio focus abandon succeeded=$granted")
        audioFocusRequest = null
    }

    private val audioRecordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
            super.onRecordingConfigChanged(configs)

            var occupied = false

            // 获取当前活跃的 Session ID
            val myAudioSessionId = synchronized(this@VoiceController) {
                if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) audioRecord?.audioSessionId else null
            }
            val myWakeupSessionId = synchronized(wakeupRecordLock) {
                if (wakeupAudioRecord?.state == AudioRecord.STATE_INITIALIZED) wakeupAudioRecord?.audioSessionId else null
            }

            for (config in configs) {
                val id = config.clientAudioSessionId
                // 排除我们自己的 Session
                if ((myAudioSessionId != null && id == myAudioSessionId) ||
                    (myWakeupSessionId != null && id == myWakeupSessionId)) {
                    continue
                }

                // 发现其他应用正在录音
                occupied = true
                Log.d(TAG, "Found another active recording session")
                break
            }

            if (isMicrophoneOccupiedByOther != occupied) {
                isMicrophoneOccupiedByOther = occupied
                Log.i(TAG, "Microphone occupation changed: occupied=$occupied")
                updateWakeupState()
            }
        }
    }

    fun addWakeupListener(listener: () -> Boolean) {
        if (!wakeupListeners.contains(listener)) {
            wakeupListeners.add(listener)
            updateWakeupState()
        }
    }

    fun removeWakeupListener(listener: () -> Boolean) {
        wakeupListeners.remove(listener)
        updateWakeupState()
    }

    private fun updateWakeupState() {
        scope.launch(Dispatchers.Main) {
            val hasOverlayPermission = Settings.canDrawOverlays(context)

            // 如果开启了唤醒功能，且未被其他应用占用麦克风，且有监听器，且当前未在录音，且拥有悬浮窗权限，则启动唤醒
            val shouldStart = isWakeupEnabledPref &&
                            isActivatedPref &&
                            hasApiKeyPref &&
                            wakeupListeners.isNotEmpty() &&
                            !isRecording &&
                            !isMicrophoneOccupiedByOther &&
                            hasOverlayPermission

            if (shouldStart) {
                startWakeup()
            } else {
                if (isWakeupRunning) {
                    Log.i(TAG, "Stopping wakeup. Reasons: enabled=$isWakeupEnabledPref, activated=$isActivatedPref, hasKey=$hasApiKeyPref, listeners=${wakeupListeners.size}, recording=$isRecording, occupied=$isMicrophoneOccupiedByOther, overlay=$hasOverlayPermission")
                }
                stopWakeup()
            }
        }
    }

    private fun notifyWakeup(): Boolean {
        // Iterate backwards (LIFO) so latest added listener gets first chance
        // Create a copy to avoid ConcurrentModificationException
        val listenersCopy = ArrayList(wakeupListeners)
        val iterator = listenersCopy.listIterator(listenersCopy.size)

        while (iterator.hasPrevious()) {
            val listener = iterator.previous()

            try {
                if (listener.invoke()) {
                    Log.d(TAG, "唤醒事件被监听器处理")
                    return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "监听器执行异常", e)
            }
        }

        return false
    }

    // Lock for initialization
    private val initLock = Any()
    @Volatile
    private var currentWakeWord: String = ""

    init {
        // Initialize KWS in background
        scope.launch(Dispatchers.IO) {
            initKws()
        }

        // 监听唤醒配置及前置条件
        scope.launch {
            try {
                audioManager.registerAudioRecordingCallback(audioRecordingCallback, null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register audio recording callback", e)
            }

            combine(
                preferencesRepository.wakeupEnabled,
                preferencesRepository.apiKey
            ) { enabled, apiKey ->
                // 检查本地存储的 API key
                val hasLocalKey = !apiKey.isNullOrBlank()
                Triple(enabled, false, hasLocalKey)
            }.collect { (enabled, _, hasKey) ->
                isWakeupEnabledPref = enabled
                isActivatedPref = true
                hasApiKeyPref = hasKey

                if (enabled && hasKey) {
                    // Check listeners and other conditions via updateWakeupState
                    updateWakeupState()
                } else {
                    stopWakeup()
                }
            }
        }

        scope.launch {
            preferencesRepository.wakeWord.collectLatest { word ->
                currentWakeWord = word
                Log.i(TAG, "Wake word configuration updated")
            }
        }
    }

    private suspend fun initKws() {
        Log.i(TAG, "initKws: starting initialization")

        val spotter = createKeywordSpotter()
        if (spotter == null) {
            Log.e(TAG, "initKws: failed to create spotter")
            return
        }

        val jobsToJoin = synchronized(initLock) {
            var jobs = activeWakeupJobs.toList()

            if (isWakeupRunning) {
                Log.i(TAG, "initKws: wakeup is running, stopping it")
                stopWakeup()

                jobs = activeWakeupJobs.toList()
            }
            jobs
        }

        if (jobsToJoin.isNotEmpty()) {
            Log.i(TAG, "initKws: waiting for ${jobsToJoin.size} jobs to finish")
            jobsToJoin.forEach { job ->
                try {
                    job.join()
                } catch (e: Exception) {
                    Log.e(TAG, "initKws: error waiting for job", e)
                }
            }
        }

        synchronized(initLock) {
            kwsLock.lock()
            try {
                val old = keywordSpotter
                keywordSpotter = spotter
                isKwsInitialized = true

                Log.i(TAG, "initKws: swapped spotter, releasing old")

                if (old != null) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            kotlinx.coroutines.delay(3000)
                            old.release()
                            Log.i(TAG, "Released old KWS spotter safely after delay")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error releasing previous KWS in delayed task", e)
                        }
                    }
                }
            } finally {
                kwsLock.unlock()
            }

            Log.i(TAG, "initKws: checking wakeup state")
            updateWakeupState()
        }
    }

    private fun createKeywordSpotter(): KeywordSpotter? {
        try {
            val modelDir = File(context.filesDir, "sherpa-onnx-kws")
            Log.i(TAG, "Creating KWS from local model directory")
            if (!modelDir.exists()) {
                modelDir.mkdirs()
            }

            val destKeywordsFile = File(modelDir, "keywords.txt")
            if (destKeywordsFile.exists()) {
                destKeywordsFile.delete()
            }

            val assetFolder = "sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01"
            copyAssets(assetFolder, modelDir.absolutePath)

            val files = modelDir.list()
            Log.d(TAG, "KWS model directory fileCount=${files?.size ?: 0}")

            val allFiles = files?.toList() ?: emptyList()

            // Update UI state with supported keywords (even if models are missing)
            val keywordsFile = File(modelDir, "keywords.txt")
            val supportedKeywords = if (keywordsFile.exists()) {
                try {
                    keywordsFile.readLines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .mapNotNull { line ->
                            val parts = line.split("@")
                            if (parts.size >= 2) {
                                val content = parts[1].trim()
                                val colonIndex = content.indexOf(':')
                                if (colonIndex != -1) {
                                    content.substring(0, colonIndex).trim()
                                } else {
                                    content
                                }
                            } else null
                        }
                        .toSet()
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading keywords file", e)
                    emptySet()
                }
            } else {
                emptySet()
            }

            _uiState.update { it.copy(supportedWakeWords = supportedKeywords) }

            val encoderFile = allFiles.find { it.startsWith("encoder") && it.endsWith("int8.onnx") }
                ?: allFiles.find { it.startsWith("encoder") && it.endsWith(".onnx") }

            if (encoderFile == null) {
                Log.e(TAG, "No encoder model found")
                return null
            }

            val suffix = encoderFile.removePrefix("encoder")
            val expectedDecoder = "decoder$suffix"
            val expectedJoiner = "joiner$suffix"

            val decoderFile = allFiles.find { it == expectedDecoder }
            val joinerFile = allFiles.find { it == expectedJoiner }
            val tokensFile = File(modelDir, "tokens.txt")

            if (decoderFile == null || joinerFile == null || !tokensFile.exists()) {
                Log.e(TAG, "Matching KWS model files not found")
                return null
            }

            Log.i(TAG, "Selected matching KWS model files")

            val config = KeywordSpotterConfig()
            config.featConfig.sampleRate = 16000
            config.featConfig.featureDim = 80
            config.modelConfig.transducer.encoder = File(modelDir, encoderFile).absolutePath
            config.modelConfig.transducer.decoder = File(modelDir, decoderFile).absolutePath
            config.modelConfig.transducer.joiner = File(modelDir, joinerFile).absolutePath
            config.modelConfig.tokens = tokensFile.absolutePath
            config.modelConfig.numThreads = 4
            config.modelConfig.debug = true
            // Attempt to enable hardware acceleration features if available in the library version
            // config.modelConfig.provider = "cpu"
            // config.modelConfig.enableNeon = true


            val defaultKeywordsFile = File(modelDir, "keywords.txt")
            if (defaultKeywordsFile.exists()) {
                config.keywordsFile = defaultKeywordsFile.absolutePath
            }

            try {
                if (File(config.keywordsFile).exists()) {
                    Log.i(TAG, "Using local keywords file")
                }
            } catch (_: Exception) {}

            Log.i(TAG, "Initializing KeywordSpotter native object...")
            val spotter = KeywordSpotter(config = config)
            Log.e(TAG, "KWS initialized successfully")
            return spotter
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing KWS", e)
            return null
        }
    }

    private fun copyAssets(assetPath: String, destPath: String) {
        val assets = context.assets.list(assetPath)
        if (assets.isNullOrEmpty()) {
            // It's a file or empty directory, try to copy as file
            copyFile(assetPath, destPath)
        } else {
            // It's a directory
            val dir = File(destPath)
            if (!dir.exists()) dir.mkdirs()
            for (asset in assets) {
                copyAssets("$assetPath/$asset", "$destPath/$asset")
            }
        }
    }

    private fun copyFile(filename: String, destPath: String) {
        try {
            val outFile = File(destPath)
            if (outFile.exists()) return // 避免重复复制

            context.assets.open(filename).use { inputStream ->
                FileOutputStream(outFile).use { outputStream ->
                    val buffer = ByteArray(1024)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy KWS asset file", e)
        }
    }

    /**
     * 开启语音唤醒监听
     */
    private fun startWakeup() {
        synchronized(initLock) {
            // Check again inside lock
            if (isWakeupRunning) {
                Log.w(TAG, "Wakeup already running, skipping start")
                return
            }
            if (!isKwsInitialized) {
                 Log.w(TAG, "KWS not initialized, skipping start")
                 return
            }

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "No record audio permission for wakeup")
                return
            }

            Log.i(TAG, "Starting wakeup loop...")
            isWakeupRunning = true

            // Clean up old jobs first
            activeWakeupJobs.removeAll { !it.isActive }

            val newJob = scope.launch(Dispatchers.IO) {
                runWakeupLoop()
            }
            activeWakeupJobs.add(newJob)
            wakeupJob = newJob
        }
    }

    @SuppressLint("MissingPermission", "SuspiciousIndentation")
    private suspend fun runWakeupLoop() {
        Log.i(TAG, "Starting wakeup loop")
        val localKeywordSpotter = keywordSpotter
        if (localKeywordSpotter == null) {
            Log.e(TAG, "localKeywordSpotter is null, aborting wakeup loop")
            return
        }

        var shouldStartListening = false

        try {
            val bufferSizeInBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2
            if (bufferSizeInBytes < 0) {
                 Log.e(TAG, "Invalid buffer size: $bufferSizeInBytes")
                 return
            }

            synchronized(wakeupRecordLock) {
                if (wakeupAudioRecord != null) {
                    try {
                        wakeupAudioRecord?.release()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error releasing previous wakeup record", e)
                    }
                    wakeupAudioRecord = null
                }

                wakeupAudioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSizeInBytes)

                if (wakeupAudioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord not initialized. State: ${wakeupAudioRecord?.state}")
                    return
                }

                // 启用降噪
                if (NoiseSuppressor.isAvailable()) {
                    try {
                        val sessionId = wakeupAudioRecord!!.audioSessionId
                        wakeupNoiseSuppressor = NoiseSuppressor.create(sessionId)
                        if (wakeupNoiseSuppressor != null) {
                            wakeupNoiseSuppressor?.enabled = true
                            Log.i(TAG, "Wakeup NoiseSuppressor enabled")
                        }
                    } catch (e: Exception) {
                         Log.e(TAG, "Failed to enable Wakeup NoiseSuppressor", e)
                    }
                }

                // 开始录音
                wakeupAudioRecord?.startRecording()
                Log.e(TAG, "AudioRecord recording started. RecordingState: ${wakeupAudioRecord?.recordingState}")

                if (wakeupAudioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                     Log.e(TAG, "AudioRecord failed to start recording.")
                     return
                }
            }

            val stream: OnlineStream = localKeywordSpotter.createStream()

            Log.e(TAG, "KWS stream created")

            try {
                val shortBuffer = ShortArray(bufferSizeInBytes / 2)
                val floatSamples = FloatArray(shortBuffer.size)

                var loopCount = 0
                var lastHeartbeatTime = System.currentTimeMillis()

                var detectedKeyword: String? = null
                var audioSamplesForVerification: FloatArray? = null

                // 音频缓冲区：保存最近2秒的音频用于声纹验证（与注册时长保持一致）
                val audioBufferDuration = 2 // 秒
                val audioBufferSize = SAMPLE_RATE * audioBufferDuration
                val audioBuffer = FloatArray(audioBufferSize)
                var audioBufferIndex = 0
                var audioBufferFilled = false

                while (isWakeupRunning && currentCoroutineContext().isActive) {
                    // Heartbeat to prevent model sleep (Silent Warm-up)
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastHeartbeatTime > 30000) { // 30 seconds
                        try {
                            // Send 0.1s of silence
                            val silentFrame = FloatArray(1600)
                            stream.acceptWaveform(silentFrame, sampleRate = SAMPLE_RATE)
                            lastHeartbeatTime = currentTime
                            Log.d(TAG, "Sent heartbeat silent frame to KWS")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error sending heartbeat", e)
                        }
                    }
                    // 检查录音是否有效，如果被外部释放（如 stopWakeup），则退出
                    if (wakeupAudioRecord == null || wakeupAudioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                        Log.e(TAG, "AudioRecord invalid state during loop")
                        break
                    }

                    // Read 可能会阻塞，如果 wakeupAudioRecord 被释放，这里应该会抛出异常或返回错误
                    val read = wakeupAudioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: -1

                    if (read > 0) {
                        for (i in 0 until read) {
                            floatSamples[i] = shortBuffer[i] / 32768.0f
                        }

                        // 将音频数据添加到环形缓冲区
                        for (i in 0 until read) {
                            audioBuffer[audioBufferIndex] = floatSamples[i]
                            audioBufferIndex = (audioBufferIndex + 1) % audioBufferSize
                            if (audioBufferIndex == 0) {
                                audioBufferFilled = true
                            }
                        }

                        kwsLock.lock()
                        try {
                            stream.acceptWaveform(floatSamples, SAMPLE_RATE)
                            while (localKeywordSpotter.isReady(stream)) {
                                localKeywordSpotter.decode(stream)
                                val result = localKeywordSpotter.getResult(stream)
                                    if (result.keyword.isNotEmpty()) {
                                        Log.e(TAG, "========== 检测到唤醒词 ==========")
                                        val detected = result.keyword.trim()

                                        if (currentWakeWord.isEmpty() || detected.contains(currentWakeWord) || currentWakeWord.contains(detected)) {
                                            Log.i(TAG, "✅ 唤醒词匹配成功")

                                            // 从环形缓冲区中提取音频用于验证
                                            detectedKeyword = detected

                                            // 如果缓冲区已满，提取完整的2秒音频；否则提取已有的音频
                                            val samplesCount = if (audioBufferFilled) audioBufferSize else audioBufferIndex
                                            audioSamplesForVerification = FloatArray(samplesCount)

                                            if (audioBufferFilled) {
                                                val oldestIndex = audioBufferIndex
                                                for (i in 0 until audioBufferSize) {
                                                    audioSamplesForVerification[i] = audioBuffer[(oldestIndex + i) % audioBufferSize]
                                                }
                                            } else {
                                                System.arraycopy(audioBuffer, 0, audioSamplesForVerification, 0, samplesCount)
                                            }

                                            // 不立即设置 isWakeupRunning = false
                                            // 等待音色验证结果后再决定
                                            break
                                        } else {
                                            Log.w(TAG, "❌ 唤醒词不匹配")
                                            Log.w(TAG, "忽略此次唤醒")
                                        }
                                    }
                                }
                        } finally {
                            kwsLock.unlock()
                        }

                        // 在锁外进行音色验证（避免在持有锁时调用 suspend 函数）
                        if (detectedKeyword != null && audioSamplesForVerification != null) {
                            Log.i(TAG, "========== 开始音色验证流程 ==========")

                            try {
                                // 检查是否需要音色验证
                                val voiceprintEnabled = preferencesRepository.getVoiceprintEnabledSync()
                                val isEnrolled = voiceprintManager.isEnrolled()

                                Log.i(TAG, "音色验证配置已检查")

                                if (voiceprintEnabled && isEnrolled) {
                                    // 验证音色
                                    val threshold = preferencesRepository.getVoiceprintThresholdSync()
                                    val verified = voiceprintManager.verifyVoiceprint(audioSamplesForVerification, threshold)

                                    if (verified.isSuccess && verified.getOrDefault(false)) {
                                        Log.i(TAG, "✅ 音色验证通过")
                                        shouldStartListening = true
                                    } else {
                                        Log.w(TAG, "❌ 音色验证失败，继续监听唤醒词")
                                        withContext(Dispatchers.Main) {
                                            ToastUtils.show(context, context.getString(R.string.voiceprint_verification_failed), Toast.LENGTH_SHORT)
                                        }
                                        // 验证失败，继续循环（不设置shouldStartListening）
                                    }
                                } else {
                                    // 未启用音色验证，直接唤醒
                                    shouldStartListening = true
                                }

                                if (shouldStartListening) {
                                    Log.i(TAG, "========== 允许唤醒 ==========")
                                    isWakeupRunning = false // 只有在允许唤醒时才退出循环
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ 音色验证流程异常，允许唤醒", e)
                                shouldStartListening = true
                                isWakeupRunning = false
                            }

                            // 清空变量
                            detectedKeyword = null
                            audioSamplesForVerification = null
                        }

                        if (shouldStartListening) {
                            break
                        }

                    } else {
                         Log.e(TAG, "AudioRecord read returned error: $read")
                         if (read < 0) break
                    }

                    if (loopCount % 200 == 0) {
                         Log.e(TAG, "Wakeup loop running. Read $read samples. Loop: $loopCount")
                    }
                    loopCount++
                }
            } finally {
                kwsLock.lock()
                try {
                    stream.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing stream", e)
                } finally {
                    kwsLock.unlock()
                }
                Log.e(TAG, "KWS stream released")
            }
            Log.i(TAG, "唤醒循环结束, shouldStartListening=$shouldStartListening")
        } catch (e: Exception) {
            Log.e(TAG, "Error in wakeup loop", e)
        } finally {
            synchronized(wakeupRecordLock) {
                try {
                    // Release NoiseSuppressor
                    if (wakeupNoiseSuppressor != null) {
                        wakeupNoiseSuppressor?.release()
                        wakeupNoiseSuppressor = null
                    }

                    if (wakeupAudioRecord != null) {
                        wakeupAudioRecord?.stop()
                        wakeupAudioRecord?.release()
                        wakeupAudioRecord = null
                    } else {

                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing recorder", e)
                }
            }

            if (shouldStartListening) {
                Log.i(TAG, "准备启动语音监听...")

                scope.launch(Dispatchers.Main) {
                    val handled = notifyWakeup()

                    if (!handled) {
                        Log.w(TAG, "没有监听器处理唤醒事件")
                        if (!Settings.canDrawOverlays(context)) {
                            ToastUtils.show(context, context.getString(R.string.floating_permission_request), Toast.LENGTH_LONG)
                        }
                        updateWakeupState()
                    }
                }
            } else {
                 Log.e(TAG, "Wakeup loop finalized without detection.")
            }
            isWakeupRunning = false
        }
    }

    /**
     * 停止唤醒监听
     */
    private fun stopWakeup() {
        if (isWakeupRunning) {
            isWakeupRunning = false
            Log.i(TAG, "Stopping wakeup...")

            activeWakeupJobs.forEach { it.cancel() }

            wakeupJob?.cancel()

            synchronized(wakeupRecordLock) {
                if (wakeupAudioRecord != null) {
                    try {
                        wakeupAudioRecord?.stop()
                        wakeupAudioRecord?.release()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error releasing wakeup audio record", e)
                    }
                    wakeupAudioRecord = null
                }
            }

            scope.launch(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(isWakeupActive = false)
            }
        }
    }

    /**
     * 重启唤醒监听 (用于外部在处理完唤醒事件后，决定不进入录音状态时手动恢复)
     */
    fun restartWakeup() {
        if (isWakeupEnabledPref && isActivatedPref && hasApiKeyPref) {
            updateWakeupState()
        }
    }

    /**
     * 检查麦克风是否可用
     */
    fun isAvailable(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
    }

    private var accumulatedText = ""

    /**
     * 开始监听（录音并实时识别）
     */
    fun startListening() {
        Log.i(TAG, "========== 开始语音识别 ==========")

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
             Log.e(TAG, "缺少录音权限")
             _uiState.update { it.copy(error = context.getString(R.string.missing_record_audio_permission)) }
             return
        }

        // Request audio focus to pause other media
        requestAudioFocus()
        stopWakeup()

        if (isRecording) {
            Log.w(TAG, "已在录音中，忽略重复调用")
            return
        }

        isRecording = true

        accumulatedText = ""

        _uiState.update {
            it.copy(
                isListening = true,
                isProcessing = false,
                error = null,
                finalText = null
            )
        }

        lastVoiceTime = System.currentTimeMillis()
        hasVoiceStarted = false

        silenceCheckJob?.cancel()
        silenceCheckJob = scope.launch(Dispatchers.IO) {
            while (isActive && isRecording) {
                val now = System.currentTimeMillis()
                val silenceDuration = now - lastVoiceTime

                if (!hasVoiceStarted) {
                    if (silenceDuration > MAX_START_SILENCE) {
                        Log.i(TAG, "VAD: Max start silence ($MAX_START_SILENCE ms) reached, stopping.")
                        stopListening()
                        break
                    }
                } else {
                    if (silenceDuration > MAX_TRAILING_SILENCE) {
                        Log.i(TAG, "VAD: Max trailing silence ($MAX_TRAILING_SILENCE ms) reached, stopping.")
                        stopListening()
                        break
                    }
                }
                kotlinx.coroutines.delay(200)
            }
        }

        recordingJob = scope.launch(Dispatchers.IO) {
            try {
                startStreamingRecognition()
            } catch (e: Exception) {
                Log.e(TAG, "录音过程出错", e)
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isListening = false,
                            error = context.getString(R.string.recording_failed_prefix, e.message)
                        )
                    }
                }
                stopListening()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun startStreamingRecognition() {
        // 使用本地存储的 API key
        val apiKey = preferencesRepository.getAsrApiKeySync()
            ?: preferencesRepository.getApiKeySync()
            ?: ""

        if (apiKey.isBlank()) {
            throw Exception("未配置 DashScope API Key")
        }

        val storedModel = preferencesRepository.getAsrModelSync()
        val asrModel = AsrModel.fromValue(storedModel).value

        Log.i(TAG, "Starting streaming recognition")

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
             throw SecurityException("Permission denied")
        }

        synchronized(this) {
            audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize)

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord not initialized")
            }

            audioRecord?.startRecording()
        }

        val audioFlowable = Flowable.create({ emitter ->
            val buffer = ByteArray(3200)

            try {
                while (isRecording && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING && !emitter.isCancelled) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        emitter.onNext(ByteBuffer.wrap(buffer, 0, read))

                        val rms = calculateRms(buffer, read)
                        scope.launch(Dispatchers.Main) {
                             _uiState.update { it.copy(rmsDb = rms) }
                        }

                    } else if (read < 0) {
                        emitter.onError(Exception("AudioRecord read error: $read"))
                        return@create
                    }
                }
                emitter.onComplete()
            } catch (e: Exception) {
                if (!emitter.isCancelled) {
                    emitter.onError(e)
                }
            }
        }, BackpressureStrategy.BUFFER)

        try {
            val param = RecognitionParam.builder()
                .model(asrModel)
                .format("pcm")
                .sampleRate(SAMPLE_RATE)
                .apiKey(apiKey)
                .build()

            val recognition = Recognition()

            recognition.streamCall(param, audioFlowable)
                .blockingForEach { result ->
                    handleRecognitionResult(result)
                }

            Log.e(TAG, "Recognition completed")
            stopListening()
            scope.launch(Dispatchers.Main) {
                 _uiState.update { it.copy(isListening = false, isProcessing = false) }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Recognition error", e)
            if (isRecording) {
                 scope.launch(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isListening = false,
                            error = context.getString(R.string.recognition_failed_prefix, e.message)
                        )
                    }
                }
            }
        } finally {
            stopListening()
        }
    }

    private fun handleRecognitionResult(result: RecognitionResult) {
        val (sentence, isFinal) = result.getSentenceTextAndStatus()

        // Update VAD timer only if valid text detected
        if (!sentence.isNullOrBlank()) {
            lastVoiceTime = System.currentTimeMillis()
            hasVoiceStarted = true
        }

        val fullText = accumulatedText + (sentence ?: "")

        if (fullText.isNotBlank()) {
             scope.launch(Dispatchers.Main) {
                 _uiState.update { it.copy(
                     finalText = fullText,
                     isFinalResult = isFinal
                 ) }
             }
        }

        if (isFinal && !sentence.isNullOrBlank()) {
            accumulatedText += sentence
        }
    }

    private fun RecognitionResult.getSentenceTextAndStatus(): Pair<String?, Boolean> {
        try {
             val json = gson.toJson(this)
             Log.e(TAG, "DashScope response parsing failed")

             val dashResult = gson.fromJson(json, DashScopeResult::class.java)

             var text: String? = null
             var isFinal = false

             if (dashResult.sentence != null) {
                 text = dashResult.sentence.text
                 if (dashResult.sentence.sentenceEnd == true || dashResult.sentence.isSentenceEnd == true) {
                     isFinal = true
                 }
             }

             if (text == null && !dashResult.sentences.isNullOrEmpty()) {
                val sb = StringBuilder()
                for (item in dashResult.sentences) {
                    if (!item.text.isNullOrBlank()) {
                        sb.append(item.text)
                    }
                    if (item.isSentenceEnd == true || item.sentenceEnd == true) {
                        isFinal = true
                    }
                }
                if (sb.isNotEmpty()) text = sb.toString()
             }

             if (text == null && dashResult.output?.sentence?.text != null) {
                 text = dashResult.output.sentence.text
                 if (dashResult.output.sentence.isSentenceEnd == true || dashResult.output.sentence.sentenceEnd == true) {
                     isFinal = true
                 }
             }

             if (text == null && !dashResult.text.isNullOrBlank()) {
                 text = dashResult.text
             }

             Log.d(TAG, "ASR text parsed: length=${text?.length ?: 0}, isFinal=$isFinal")
             return Pair(text, isFinal)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing result", e)
            return Pair(null, false)
        }
    }

    fun stopListening() {
        // Abandon audio focus
        abandonAudioFocus()

        if (!isRecording) return
        isRecording = false
        silenceCheckJob?.cancel()

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio record", e)
        }

        recordingJob?.cancel()
        recordingJob = null

        _uiState.update { it.copy(isListening = false) }

        restartWakeup()
    }

    /**
     * 取消监听
     */
    fun cancel() {
        Log.d(TAG, "cancel called")
        stopListening()
        recordingJob?.cancel()
        // 清空所有状态，包括 finalText，防止其他监听者读取残留内容
        _uiState.value = VoiceUiState(
            isListening = false,
            isProcessing = false,
            finalText = null,  // 关键：清空识别结果
            error = null,
            isFinalResult = false,
            rmsDb = -100f
        )
    }

    fun destroy() {
        cancel()
        try {
            audioManager.unregisterAudioRecordingCallback(audioRecordingCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister audio recording callback", e)
        }
    }

    private fun calculateRms(buffer: ByteArray, read: Int): Float {
        var sum = 0.0
        for (i in 0 until read step 2) {
            if (i + 1 >= read) break
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val normalized = sample.toShort() / 32768.0
            sum += normalized * normalized
        }
        val rms = sqrt(sum / (read / 2))
        return if (rms > 0) (20 * log10(rms)).toFloat() else -100f
    }

    private data class DashScopeResult(
        val sentences: List<Sentence>?,
        val output: Output?,
        val text: String?,
        val sentence: Sentence?
    )

    private data class Sentence(
        val text: String?,
        @SerializedName("is_sentence_end")
        val isSentenceEnd: Boolean? = false,
        @SerializedName("sentence_end")
        val sentenceEnd: Boolean? = false
    )

    private data class Output(
        val sentence: Sentence?
    )
}

/**
 * UI 状态
 */
data class VoiceUiState(
    val isListening: Boolean = false,
    val isProcessing: Boolean = false,
    val error: String? = null,
    val finalText: String? = null,
    val isFinalResult: Boolean = false,
    val rmsDb: Float = -100f,
    val isWakeupActive: Boolean = false,
    val supportedWakeWords: Set<String> = emptySet()
)
