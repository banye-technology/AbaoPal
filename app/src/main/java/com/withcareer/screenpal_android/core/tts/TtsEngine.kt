package com.withcareer.screenpal_android.core.tts

import android.content.Context

/**
 * TTS引擎接口
 * 定义所有TTS引擎的通用行为
 *
 */
interface TtsEngine {
    /**
     * 初始化TTS引擎
     * @param context Android上下文
     * @param onReady 初始化完成回调，参数表示是否成功
     */
    fun initialize(context: Context, onReady: (Boolean) -> Unit)

    /**
     * 语音播报
     * @param text 要播报的文本
     * @param onDone 播报完成回调（可选）
     */
    fun speak(text: String, onDone: (() -> Unit)? = null)

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
    )

    /**
     * 设置语言
     * @param language 语言代码（如 "zh", "en", "system"）
     */
    fun setLanguage(language: String)

    /**
     * 停止当前播放
     */
    fun stop()

    /**
     * 释放资源
     */
    fun shutdown()

    /**
     * 获取引擎类型
     * @return 引擎类型标识
     */
    fun getType(): String

    /**
     * 检查引擎是否就绪
     * @return 是否就绪
     */
    fun isReady(): Boolean
}
