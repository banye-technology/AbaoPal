package com.withcareer.screenpal_android.config


import com.withcareer.screenpal_android.R
/**
 * 应用配置和常量定义
 *
 */
object AppConfig {
    /**
     * 默认 AI 提供商
     */
    const val DEFAULT_AI_PROVIDER = "volcengine"

    /**
     * 默认语音识别提供商
     */
    const val DEFAULT_ASR_PROVIDER = "dashscope"

    /**
     * 百炼基础 URL
     */
    const val BAILIAN_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"

    /**
     * 火山引擎基础 URL
     */
    const val VOLCENGINE_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3"

    /**
     * 硅基流动基础 URL
     */
    const val SILICONFLOW_BASE_URL = "https://api.siliconflow.cn/v1"

    /**
     * 默认多模态模型名称
     */
    val DEFAULT_MODEL_NAME = LlmModel.QWEN3_VL_FLASH.value

    /**
     * 规划模型名称 (固定配置)
     */
    val PLANNING_MODEL_NAME = LlmModel.QWEN_MAX.value

    /**
     * 豆包模型名称
     */
    const val DOUBAO_MODEL_NAME = "doubao-seed-2-0-mini-260215"

    /**
     * 默认语音识别模型
     */
    val DEFAULT_ASR_MODEL = AsrModel.PARAFORMER_REALTIME_V2.value

    /**
     * 默认 Embedding 模型 (百炼)
     */
    const val DEFAULT_EMBEDDING_MODEL = "text-embedding-v4"

    /**
     * 默认 TTS 提供商（阿里云语音包）
     */
    const val DEFAULT_TTS_PROVIDER = "aliyun"

    /**
     * 默认阿里云音色
     */
    const val DEFAULT_ALIYUN_VOICE = "longhuhu_v3"
}

/**
 * 大语言模型枚举
 *
 */
enum class LlmModel(val value: String) {
    GUI_PLUS("gui-plus"),
    QWEN_PLUS("qwen-plus"),
    QWEN_TURBO("qwen-turbo"),
    QWEN_MAX("qwen-max"),
    QWEN_VL_MAX("qwen-vl-max"),
    QWEN3_MAX("qwen3-max"),
    QWEN3_VL_PLUS("qwen3-vl-plus"),
    QWEN3_VL_FLASH("qwen3-vl-flash"),
    QWEN_FLASH("qwen-flash");

    companion object {
        fun fromValue(value: String): LlmModel? = entries.find { it.value == value }
    }
}

/**
 * AI 提供商枚举
 *
 */
enum class AiProvider(val value: String, val labelResId: Int) {
    /**
     * 阿里云百炼
     */
    DASHSCOPE("dashscope", R.string.provider_dashscope),

    /**
     * 火山引擎
     */
    VOLCENGINE("volcengine", R.string.provider_volcengine),

    /**
     * 硅基流动
     */
    SILICONFLOW("siliconflow", R.string.provider_siliconflow),

    /**
     * 其他（自定义地址）
     */
    CUSTOM("custom", R.string.provider_custom);

    companion object {
        /**
         * 根据值获取枚举
         */
        fun fromValue(value: String): AiProvider = entries.find { it.value == value } ?: DASHSCOPE
    }
}

/**
 * 语音识别提供商枚举
 *
 */
enum class AsrProvider(val value: String, val labelResId: Int) {
    /**
     * 阿里云 DashScope
     */
    DASHSCOPE("dashscope", R.string.provider_dashscope);

    companion object {
        /**
         * 根据值获取枚举
         */
        fun fromValue(value: String): AsrProvider = entries.find { it.value == value } ?: DASHSCOPE
    }
}

/**
 * 语音识别模型枚举
 *
 */
enum class AsrModel(val value: String, val labelResId: Int) {
    PARAFORMER_REALTIME_V2("paraformer-realtime-v2", R.string.model_paraformer_realtime_v2),
    PARAFORMER_REALTIME_V1("paraformer-realtime-v1", R.string.model_paraformer_realtime_v1),
    PARAFORMER_REALTIME_8K_V2("paraformer-realtime-8k-v2", R.string.model_paraformer_realtime_8k_v2),
    PARAFORMER_REALTIME_8K_V1("paraformer-realtime-8k-v1", R.string.model_paraformer_realtime_8k_v1);

    companion object {
        fun fromValue(value: String): AsrModel = entries.find { it.value == value } ?: PARAFORMER_REALTIME_V2
    }
}

/**
 * TTS 提供商枚举
 *
 */
enum class TtsProvider(val value: String, val labelResId: Int) {
    /**
     * 系统语音包
     */
    SYSTEM("system", R.string.voice_package_system),

    /**
     * 阿里云语音包
     */
    ALIYUN("aliyun", R.string.voice_package_aliyun);

    companion object {
        /**
         * 根据值获取枚举
         */
        fun fromValue(value: String): TtsProvider = entries.find { it.value == value } ?: SYSTEM
    }
}

/**
 * 阿里云TTS音色枚举 (CosyVoice-v3-Flash)
 *
 */
enum class AliyunVoice(
    val value: String,
    val displayName: String,
    val characteristic: String,
    val gender: String
) {
    LONGHUHU_V3("longhuhu_v3", "龙胡胡", "温暖治愈", "男"),
    LONGANQIN_V3("longanqin_v3", "龙安亲", "亲和活泼", "女"),
    LONGANYA_V3("longanya_v3", "龙安雅", "高雅气质", "女"),
    LONGANLING_V3("longanling_v3", "龙安灵", "思维灵动", "女"),
    LONGANZHI_V3("longanzhi_v3", "龙安智", "睿智轻熟", "男"),
    LONGANROU_V3("longanrou_v3", "龙安柔", "温柔闺蜜", "女"),
    LONGHAN_V3("longhan_v3", "龙寒", "温暖痴情", "男"),
    LONGXING_V3("longxing_v3", "龙星", "温婉邻家", "女"),
    LONGWAN_V3("longwan_v3", "龙婉", "细腻柔声", "女"),
    LONGQIANG_V3("longqiang_v3", "龙嫱", "浪漫风情", "女"),
    LONGCHENG_V3("longcheng_v3", "龙橙", "智慧青年", "男"),
    LONGFEIFEI_V3("longfeifei_v3", "龙菲菲", "甜美娇气", "女"),
    LONGZHE_V3("longzhe_v3", "龙哲", "呆板大暖男", "男"),
    LONGYAN_V3("longyan_v3", "龙颜", "温暖春风", "女"),
    LONGTIAN_V3("longtian_v3", "龙天", "磁性理智", "男"),
    LONGZE_V3("longze_v3", "龙泽", "温暖元气", "男"),
    LONGHAO_V3("longhao_v3", "龙浩", "多情忧郁", "男");

    companion object {
        /**
         * 根据值获取枚举
         */
        fun fromValue(value: String): AliyunVoice =
            entries.find { it.value == value } ?: LONGHUHU_V3
    }

    /**
     * 获取显示标签：名称 - 特质（性别）
     */
    fun getLabel(): String = "$displayName - $characteristic（$gender）"
}
