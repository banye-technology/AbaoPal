package com.withcareer.screenpal_android.core.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import android.media.audiofx.NoiseSuppressor
import com.google.gson.Gson
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.sqrt

/**
 * 声纹管理器
 * 负责声纹注册、验证和存储管理
 *
 */
class VoiceprintManager(private val context: Context) {

    companion object {
        private const val TAG = "VoiceprintManager"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val RECORDING_DURATION_MS = 2000L // 每次录制2秒

        // 声纹模型目录
        private const val MODEL_DIR_NAME = "speaker_recognition"

        // 声纹数据文件
        private const val VOICEPRINT_FILE_NAME = "voiceprint_data.json"

        // 默认阈值（宽松模式）
        const val DEFAULT_THRESHOLD = 0.5f

        // VAD（语音活动检测）参数
        private const val VAD_ENERGY_THRESHOLD = 0.01f // 能量阈值
        private const val VAD_MIN_SPEECH_DURATION = 0.3f // 最小语音段时长（秒）
        private const val VAD_FRAME_SIZE = 160 // 10ms @ 16kHz

        // 音量差异检测参数
        private const val MAX_VOLUME_DIFFERENCE_RATIO = 0.4f // 最大音量差异比例（40%）
    }

    private var embeddingExtractor: SpeakerEmbeddingExtractor? = null
    private val gson = Gson()

    @Volatile
    private var isInitialized = false

    // 记录每次录音的平均音量（用于音量差异检测）
    private val recordedVolumes = mutableListOf<Float>()

    // 声纹数据文件路径
    private val voiceprintFile: File
        get() = File(context.filesDir, VOICEPRINT_FILE_NAME)

    init {
        // 在后台线程初始化模型
        Log.d(TAG, "VoiceprintManager initialization started")
        try {
            initSpeakerIdModel()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize speaker ID model", e)
        }
    }

    /**
     * 初始化说话人识别模型
     */
    private fun initSpeakerIdModel() {
        val startTime = System.currentTimeMillis()
        try {
            // 模型文件在 assets/speaker_recognition 目录下
            val modelPath = "speaker_recognition/3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx"

            Log.i(TAG, "========== 开始初始化说话人识别模型 ==========")
            Log.i(TAG, "正在加载声纹模型")

            val modelFilePath = assetFilePath(modelPath)
            val modelFile = File(modelFilePath)
            if (modelFile.exists()) {
                Log.i(TAG, "模型文件存在，大小: ${modelFile.length() / 1024 / 1024}MB (${modelFile.length()} bytes)")
            } else {
                Log.e(TAG, "声纹模型文件不存在")
                return
            }

            Log.d(TAG, "创建模型配置: numThreads=2, debug=false, provider=cpu")
            val config = SpeakerEmbeddingExtractorConfig(
                model = modelFilePath,
                numThreads = 2,
                debug = false,
                provider = "cpu"
            )

            Log.d(TAG, "开始加载 SpeakerEmbeddingExtractor...")
            embeddingExtractor = SpeakerEmbeddingExtractor(config = config)
            isInitialized = true

            val dim = embeddingExtractor?.dim() ?: 0
            val elapsedTime = System.currentTimeMillis() - startTime
            Log.i(TAG, "✅ 模型初始化成功！特征维度: $dim, 耗时: ${elapsedTime}ms")
            Log.i(TAG, "========== 说话人识别模型初始化完成 ==========")
        } catch (e: Exception) {
            val elapsedTime = System.currentTimeMillis() - startTime
            Log.e(TAG, "❌ 模型初始化失败 (耗时: ${elapsedTime}ms)", e)
            Log.e(TAG, "模型初始化失败类型: ${e.javaClass.simpleName}")
            isInitialized = false
        }
    }

    /**
     * 获取 asset 文件路径
     * Sherpa-ONNX 可以直接读取 assets 文件
     */
    private fun assetFilePath(assetFileName: String): String {
        val file = File(context.filesDir, assetFileName.replace("/", "_"))

        Log.d(TAG, "检查声纹模型资源")

        if (file.exists()) {
            Log.d(TAG, "文件已存在，大小: ${file.length() / 1024 / 1024}MB")
            return file.absolutePath
        }

        Log.d(TAG, "文件不存在，开始从 assets 复制...")
        val startTime = System.currentTimeMillis()

        try {
            context.assets.open(assetFileName).use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var totalBytes = 0L
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                        totalBytes += read
                    }

                    val elapsedTime = System.currentTimeMillis() - startTime
                    Log.d(TAG, "✅ Asset 文件复制成功")
                    Log.d(TAG, "   - 声纹模型资源已复制")
                    Log.d(TAG, "   - 文件大小: ${totalBytes / 1024 / 1024}MB (${totalBytes} bytes)")
                    Log.d(TAG, "   - 复制耗时: ${elapsedTime}ms")
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "❌ 声纹模型资源复制失败", e)
            Log.e(TAG, "Asset 文件复制失败类型: ${e.javaClass.simpleName}")
        }

        return file.absolutePath
    }

    /**
     * 开始录制声纹（用于注册）
     * @param attemptIndex 录制次数索引（0, 1, 2）
     * @param onAmplitudeUpdate 音量更新回调（返回0-1的归一化音量值）
     * @return Result包含提取的特征向量
     */
    @SuppressLint("MissingPermission")
    suspend fun startEnrollmentRecording(
        attemptIndex: Int,
        onAmplitudeUpdate: ((Float) -> Unit)? = null
    ): Result<FloatArray> = withContext(Dispatchers.IO) {
        Log.i(TAG, "开始第 ${attemptIndex + 1}/3 次声纹录制")

        if (!isInitialized || embeddingExtractor == null) {
            Log.e(TAG, "模型未初始化")
            return@withContext Result.failure(Exception("Speaker ID model not initialized"))
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "录音权限未授予")
            return@withContext Result.failure(Exception("No record audio permission"))
        }

        try {
            val audioSamples = recordAudio(RECORDING_DURATION_MS, onAmplitudeUpdate)

            if (audioSamples.isEmpty()) {
                Log.e(TAG, "录音失败")
                return@withContext Result.failure(Exception("Failed to record audio"))
            }

            // 计算音频统计信息
            val maxAmplitude = audioSamples.maxOrNull() ?: 0f
            val avgAmplitude = audioSamples.map { kotlin.math.abs(it) }.average().toFloat()
            val audioLengthSec = audioSamples.size.toFloat() / SAMPLE_RATE

            // 音频质量检测
            val MIN_AMPLITUDE_THRESHOLD = 0.01f
            if (avgAmplitude < MIN_AMPLITUDE_THRESHOLD) {
                return@withContext Result.failure(Exception("录音音量太小，请靠近麦克风并提高音量"))
            }

            val MIN_AUDIO_LENGTH = 0.5f
            if (audioLengthSec < MIN_AUDIO_LENGTH) {
                return@withContext Result.failure(Exception("录音时长太短，请完整说出唤醒词"))
            }

            val peakCount = audioSamples.count { kotlin.math.abs(it) > 0.1f }
            val peakRatio = peakCount.toFloat() / audioSamples.size
            val MIN_PEAK_RATIO = 0.03f

            if (peakRatio < MIN_PEAK_RATIO) {
                return@withContext Result.failure(Exception("未检测到清晰的语音，请在安静环境下清晰地说出唤醒词"))
            }

            val MIN_DYNAMIC_RANGE = 0.05f
            if (maxAmplitude < MIN_DYNAMIC_RANGE) {
                return@withContext Result.failure(Exception("录音质量较差，请靠近麦克风并清晰说话"))
            }

            // 音量差异检测
            if (attemptIndex == 0) {
                recordedVolumes.clear()
                recordedVolumes.add(avgAmplitude)
            } else {
                val previousVolume = recordedVolumes.last()
                val volumeDifference = kotlin.math.abs(avgAmplitude - previousVolume) / previousVolume

                if (volumeDifference > MAX_VOLUME_DIFFERENCE_RATIO) {
                    Log.w(TAG, "音量差异较大，建议保持录音音量一致")
                }

                recordedVolumes.add(avgAmplitude)
            }

            // 音频预处理（降噪、VAD、归一化）
            val processedAudio = preprocessAudio(audioSamples)

            // 提取特征向量
            val embedding = extractEmbedding(processedAudio)

            if (embedding == null || embedding.isEmpty()) {
                Log.e(TAG, "特征提取失败")
                return@withContext Result.failure(Exception("Failed to extract embedding"))
            }

            Log.i(TAG, "第 ${attemptIndex + 1}/3 次录制完成")
            Result.success(embedding)
        } catch (e: Exception) {
            Log.e(TAG, "录制过程出错", e)
            Result.failure(e)
        }
    }

    /**
     * 录制音频
     * @param durationMs 录制时长（毫秒）
     * @param onAmplitudeUpdate 音量更新回调（返回0-1的归一化音量值）
     * @return 音频采样数组（Float）
     */
    @SuppressLint("MissingPermission")
    private fun recordAudio(
        durationMs: Long,
        onAmplitudeUpdate: ((Float) -> Unit)? = null
    ): FloatArray {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = minBufferSize * 2

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord 初始化失败")
            return floatArrayOf()
        }

        var noiseSuppressor: NoiseSuppressor? = null
        if (NoiseSuppressor.isAvailable()) {
            try {
                noiseSuppressor = NoiseSuppressor.create(audioRecord.audioSessionId)
                if (noiseSuppressor != null) {
                    noiseSuppressor.enabled = true
                    Log.i(TAG, "NoiseSuppressor enabled for enrollment")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enable NoiseSuppressor", e)
            }
        }

        audioRecord.startRecording()
        val totalSamples = ((SAMPLE_RATE * durationMs) / 1000).toInt()
        val samples = mutableListOf<Float>()
        val shortBuffer = ShortArray(bufferSize / 2)

        try {
            var samplesRead = 0
            while (samplesRead < totalSamples) {
                val read = audioRecord.read(shortBuffer, 0, shortBuffer.size)

                if (read > 0) {
                    var frameAmplitude = 0f
                    for (i in 0 until read) {
                        val sample = shortBuffer[i] / 32768.0f
                        samples.add(sample)
                        frameAmplitude += kotlin.math.abs(sample)
                        samplesRead++
                        if (samplesRead >= totalSamples) break
                    }

                    // 实时振幅回调
                    if (read > 0 && onAmplitudeUpdate != null) {
                        val avgAmplitude = frameAmplitude / read
                        val normalizedAmplitude = (avgAmplitude * 10f).coerceIn(0f, 1f)
                        onAmplitudeUpdate(normalizedAmplitude)
                    }
                } else if (read < 0) {
                    Log.w(TAG, "AudioRecord.read() 错误: $read")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取音频数据出错", e)
        } finally {
            try {
                if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop()
                }
                audioRecord.release()
                noiseSuppressor?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing recorder", e)
            }
        }

        return samples.toFloatArray()
    }

    /**
     * 提取声纹特征向量
     */
    private fun extractEmbedding(audioSamples: FloatArray): FloatArray? {
        val extractor = embeddingExtractor ?: run {
            Log.e(TAG, "embeddingExtractor 未初始化")
            return null
        }

        return try {
            val stream = extractor.createStream()

            try {
                stream.acceptWaveform(samples = audioSamples, sampleRate = SAMPLE_RATE)
                stream.inputFinished()

                if (!extractor.isReady(stream)) {
                    Log.w(TAG, "音频流未就绪")
                    return null
                }

                val embedding = extractor.compute(stream)

                if (embedding.isEmpty()) {
                    Log.w(TAG, "提取的特征向量为空")
                    null
                } else {
                    embedding
                }
            } finally {
                stream.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "特征提取出错", e)
            null
        }
    }

    /**
     * 完成注册：保存3次录音的平均特征
     * @param features 3次录音的特征向量列表
     */
    suspend fun completeEnrollment(features: List<FloatArray>, wakeWord: String): Result<Unit> = withContext(Dispatchers.IO) {
        Log.i(TAG, "完成声纹注册")

        if (features.size != 3) {
            Log.e(TAG, "特征向量数量不正确: ${features.size}")
            return@withContext Result.failure(Exception("Need exactly 3 enrollment recordings"))
        }

        if (features.any { it.isEmpty() }) {
            Log.e(TAG, "存在空的特征向量")
            return@withContext Result.failure(Exception("Invalid feature vectors"))
        }

        // 计算3次录音之间的相似度（用于评估录音一致性）
        if (features.size == 3) {
            val sim12 = cosineSimilarity(features[0], features[1])
            val sim13 = cosineSimilarity(features[0], features[2])
            val sim23 = cosineSimilarity(features[1], features[2])
            val avgSimilarity = (sim12 + sim13 + sim23) / 3

            when {
                avgSimilarity < 0.4f -> {
                    Log.w(TAG, "3次录音相似度过低，建议重新注册")
                }
                avgSimilarity < 0.7f -> {
                    Log.w(TAG, "3次录音相似度较低")
                }
                else -> {
                    Log.i(TAG, "3次录音一致性良好")
                }
            }
        }

        try {
            // 计算平均特征向量
            val avgEmbedding = averageEmbeddings(features)

            // 保存到文件
            val voiceprintData = VoiceprintData(
                features = avgEmbedding.toList(),
                enrollmentDate = System.currentTimeMillis(),
                wakeWord = wakeWord
            )

            val json = gson.toJson(voiceprintData)
            voiceprintFile.writeText(json)

            if (voiceprintFile.exists()) {
                Log.i(TAG, "声纹数据保存成功")
            } else {
                Log.e(TAG, "文件保存失败")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "完成注册出错", e)
            Log.e(TAG, "错误类型: ${e.javaClass.simpleName}")
            Log.e(TAG, "操作失败类型: ${e.javaClass.simpleName}")
            Result.failure(e)
        }
    }

    /**
     * 计算多个特征向量的平均值
     */
    private fun averageEmbeddings(embeddings: List<FloatArray>): FloatArray {
        val size = embeddings[0].size
        return FloatArray(size) { i ->
            embeddings.map { it[i] }.average().toFloat()
        }
    }

    /**
     * 验证声纹（在唤醒时调用）
     * @param audioSamples 音频采样数组
     * @param threshold 相似度阈值（默认0.6，宽松模式）
     * @return Result包含是否通过验证
     */
    suspend fun verifyVoiceprint(audioSamples: FloatArray, threshold: Float = DEFAULT_THRESHOLD): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!isInitialized || embeddingExtractor == null) {
            return@withContext Result.success(true)
        }

        if (!isEnrolled()) {
            return@withContext Result.success(true)
        }

        if (audioSamples.size < SAMPLE_RATE) {
            return@withContext Result.success(false)
        }

        try {
            // 音频预处理（降噪、VAD、归一化）
            val processedAudio = preprocessAudio(audioSamples)

            val currentEmbedding = extractEmbedding(processedAudio)

            if (currentEmbedding == null || currentEmbedding.isEmpty()) {
                return@withContext Result.success(false)
            }

            val enrolledData = loadVoiceprintData()
            if (enrolledData == null) {
                return@withContext Result.success(true)
            }

            val enrolledEmbedding = enrolledData.features.toFloatArray()

            if (currentEmbedding.size != enrolledEmbedding.size) {
                return@withContext Result.success(true)
            }

            val similarity = cosineSimilarity(currentEmbedding, enrolledEmbedding)
            val verified = similarity >= threshold

            Log.i(TAG, "声纹验证完成: verified=$verified")

            Result.success(verified)
        } catch (e: Exception) {
            Log.e(TAG, "验证异常", e)
            Result.success(true)
        }
    }

    /**
     * 计算余弦相似度
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f

        val dotProduct = a.zip(b).sumOf { (x, y) -> (x * y).toDouble() }
        val normA = sqrt(a.sumOf { (it * it).toDouble() })
        val normB = sqrt(b.sumOf { (it * it).toDouble() })

        return if (normA > 0 && normB > 0) {
            (dotProduct / (normA * normB)).toFloat()
        } else {
            0f
        }
    }

    /**
     * 音频归一化处理
     * 将音频幅度归一化到 [-1, 1] 范围
     */
    private fun normalizeAudio(audioSamples: FloatArray): FloatArray {
        val maxAmp = audioSamples.maxOfOrNull { kotlin.math.abs(it) } ?: 0f
        if (maxAmp <= 0f) return audioSamples

        val normalized = FloatArray(audioSamples.size)
        for (i in audioSamples.indices) {
            normalized[i] = audioSamples[i] / maxAmp
        }
        return normalized
    }

    /**
     * 简单降噪处理（高通滤波器，去除低频噪音）
     */
    private fun denoiseAudio(audioSamples: FloatArray): FloatArray {
        if (audioSamples.isEmpty()) return audioSamples

        val alpha = 0.97f
        val filtered = FloatArray(audioSamples.size)
        filtered[0] = audioSamples[0]

        for (i in 1 until audioSamples.size) {
            filtered[i] = alpha * filtered[i - 1] + alpha * (audioSamples[i] - audioSamples[i - 1])
        }
        return filtered
    }

    /**
     * VAD（语音活动检测）
     * 提取有语音的部分，去除静音段
     * @return 提取后的音频，如果没有检测到语音则返回原音频
     */
    private fun applyVAD(audioSamples: FloatArray): FloatArray {
        if (audioSamples.size < VAD_FRAME_SIZE) return audioSamples

        // 计算每帧的能量
        val frameCount = audioSamples.size / VAD_FRAME_SIZE
        val frameEnergies = FloatArray(frameCount)

        for (i in 0 until frameCount) {
            val startIdx = i * VAD_FRAME_SIZE
            val endIdx = minOf(startIdx + VAD_FRAME_SIZE, audioSamples.size)

            var energy = 0f
            for (j in startIdx until endIdx) {
                energy += audioSamples[j] * audioSamples[j]
            }
            frameEnergies[i] = energy / (endIdx - startIdx)
        }

        // 动态阈值计算
        // 1. 估计底噪（取能量最低的10%帧的平均值，或者直接取排序后的第10%分位值）
        val sortedEnergies = frameEnergies.clone().sortedArray()
        val noiseFloorIndex = (frameCount * 0.1).toInt().coerceAtMost(frameCount - 1)
        val noiseFloor = sortedEnergies[noiseFloorIndex]

        // 2. 设定阈值：底噪的3倍（约5dB）或者默认阈值，取较大者
        // 这样在安静环境下使用默认阈值，在嘈杂环境下提高阈值
        val dynamicThreshold = noiseFloor * 3.0f
        val threshold = maxOf(VAD_ENERGY_THRESHOLD, dynamicThreshold)

        Log.d(TAG, "VAD threshold updated")

        // 标记语音帧
        val isSpeech = BooleanArray(frameCount) { frameEnergies[it] > threshold }

        // 找到连续的语音段
        val speechSegments = mutableListOf<Pair<Int, Int>>()
        var segmentStart = -1

        for (i in isSpeech.indices) {
            if (isSpeech[i] && segmentStart == -1) {
                segmentStart = i
            } else if (!isSpeech[i] && segmentStart != -1) {
                val duration = (i - segmentStart) * VAD_FRAME_SIZE.toFloat() / SAMPLE_RATE
                if (duration >= VAD_MIN_SPEECH_DURATION) {
                    speechSegments.add(Pair(segmentStart, i))
                }
                segmentStart = -1
            }
        }

        // 处理最后一个段
        if (segmentStart != -1) {
            val duration = (frameCount - segmentStart) * VAD_FRAME_SIZE.toFloat() / SAMPLE_RATE
            if (duration >= VAD_MIN_SPEECH_DURATION) {
                speechSegments.add(Pair(segmentStart, frameCount))
            }
        }

        if (speechSegments.isEmpty()) return audioSamples

        // 合并所有语音段
        val speechSamples = mutableListOf<Float>()
        for ((startFrame, endFrame) in speechSegments) {
            val startSample = startFrame * VAD_FRAME_SIZE
            val endSample = minOf(endFrame * VAD_FRAME_SIZE, audioSamples.size)

            for (i in startSample until endSample) {
                speechSamples.add(audioSamples[i])
            }
        }

        return speechSamples.toFloatArray()
    }

    /**
     * 完整的音频预处理流程：降噪、VAD、归一化
     */
    private fun preprocessAudio(audioSamples: FloatArray): FloatArray {
        var processed = denoiseAudio(audioSamples)
        processed = applyVAD(processed)
        processed = normalizeAudio(processed)
        return processed
    }

    /**
     * 删除已注册的声纹
     */
    suspend fun deleteVoiceprint(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (voiceprintFile.exists()) {
                voiceprintFile.delete()
                Log.i(TAG, "声纹文件删除成功")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 删除声纹时出错", e)
            Log.e(TAG, "错误类型: ${e.javaClass.simpleName}")
            Log.e(TAG, "操作失败类型: ${e.javaClass.simpleName}")
            Result.failure(e)
        }
    }

    /**
     * 检查是否已注册声纹
     */
    fun isEnrolled(): Boolean {
        return voiceprintFile.exists() && voiceprintFile.length() > 0
    }

    /**
     * 获取已注册的声纹信息
     */
    fun getEnrolledInfo(): VoiceprintData? {
        return loadVoiceprintData()
    }

    /**
     * 从文件加载声纹数据
     */
    private fun loadVoiceprintData(): VoiceprintData? {
        return try {
            if (!voiceprintFile.exists()) {
                null
            } else {
                val json = voiceprintFile.readText()
                gson.fromJson(json, VoiceprintData::class.java)
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载声纹数据失败: ${e.javaClass.simpleName}")
            null
        }
    }

    /**
     * 检查模型是否已初始化
     */
    fun isModelInitialized(): Boolean = isInitialized

    /**
     * 释放资源
     */
    fun release() {
        Log.i(TAG, "========== 释放 VoiceprintManager ==========")

        try {
            if (embeddingExtractor != null) {
                Log.d(TAG, "释放 SpeakerEmbeddingExtractor...")
                embeddingExtractor?.release()
                embeddingExtractor = null
                Log.d(TAG, "✅ SpeakerEmbeddingExtractor 已释放")
            } else {
                Log.d(TAG, "SpeakerEmbeddingExtractor 为 null，无需释放")
            }

            isInitialized = false
            Log.i(TAG, "✅ VoiceprintManager 已完全释放")
            Log.i(TAG, "========================================")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 释放 VoiceprintManager 时出错", e)
            Log.e(TAG, "错误类型: ${e.javaClass.simpleName}")
            Log.e(TAG, "操作失败类型: ${e.javaClass.simpleName}")
        }
    }

    /**
     * 获取注册信息摘要（用于调试）
     */
    fun getEnrollmentSummary(): String? {
        val data = getEnrolledInfo() ?: return null
        return "注册时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(data.enrollmentDate))}, 唤醒词: ${data.wakeWord}"
    }
}

/**
 * 声纹数据结构
 */
data class VoiceprintData(
    val features: List<Float>,  // 平均特征向量
    val enrollmentDate: Long,   // 注册时间戳
    val wakeWord: String        // 注册时使用的唤醒词
)
