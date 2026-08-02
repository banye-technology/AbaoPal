package com.withcareer.screenpal_android.core.llm

import android.content.Context
import com.withcareer.screenpal_android.config.AiProvider
import com.withcareer.screenpal_android.config.AppConfig
import com.withcareer.screenpal_android.config.LlmModel
import com.withcareer.screenpal_android.data.preference.PreferencesRepository

/**
 * 解析后的执行模型配置
 */
data class ResolvedModelConfig(
    val provider: String,
    val baseUrl: String,
    val modelName: String,
    val apiKey: String
)

/**
 * 执行模型配置解析器
 *
 * 解析优先级：
 * 1. 用户激活的自定义模型配置（ModelProfile）
 * 2. 默认配置（本地保存的 API Key + 服务商默认地址/多模态模型）
 *
 * 注意：执行模型需要支持视觉多模态能力（App 会发送截图）
 */
object ModelConfigResolver {

    /**
     * 服务商默认 Base URL（"其他"平台无默认地址，返回空）
     */
    fun defaultBaseUrl(provider: String): String = when (provider) {
        AiProvider.DASHSCOPE.value -> AppConfig.BAILIAN_BASE_URL
        AiProvider.VOLCENGINE.value -> AppConfig.VOLCENGINE_BASE_URL
        AiProvider.SILICONFLOW.value -> AppConfig.SILICONFLOW_BASE_URL
        else -> ""
    }

    /**
     * 服务商默认执行模型（均为多模态模型；"其他"平台无默认模型，返回空）
     */
    fun defaultModelName(provider: String): String = when (provider) {
        AiProvider.VOLCENGINE.value -> AppConfig.DOUBAO_MODEL_NAME
        AiProvider.DASHSCOPE.value, AiProvider.SILICONFLOW.value -> LlmModel.QWEN3_VL_FLASH.value
        else -> ""
    }

    /**
     * 解析当前生效的执行模型配置
     * @return 配置信息；未配置 API Key（或"其他"平台缺少地址/模型名）时返回 null
     */
    suspend fun resolveExecutor(context: Context): ResolvedModelConfig? {
        val repo = PreferencesRepository(context)
        val activeId = repo.getActiveProfileIdSync()

        if (!activeId.isNullOrBlank()) {
            val profile = repo.getModelProfilesSync().find { it.id == activeId }
            if (profile != null) {
                // 自定义配置：优先使用档案中的 Key，其次回退本地 Key
                val apiKey = profile.apiKey.ifBlank {
                    if (profile.provider == AiProvider.VOLCENGINE.value) {
                        repo.getVolcengineApiKeySync().orEmpty()
                    } else {
                        repo.getAsrApiKeySync().orEmpty()
                    }
                }
                if (apiKey.isBlank()) return null
                val baseUrl = profile.baseUrl.ifBlank { defaultBaseUrl(profile.provider) }
                val modelName = profile.modelName.ifBlank { defaultModelName(profile.provider) }
                // "其他"平台必须显式填写地址与模型名
                if (profile.provider == AiProvider.CUSTOM.value && (baseUrl.isBlank() || modelName.isBlank())) {
                    return null
                }
                return ResolvedModelConfig(
                    provider = profile.provider,
                    baseUrl = baseUrl,
                    modelName = modelName,
                    apiKey = apiKey
                )
            }
        }

        // 默认配置：使用本地字段
        val aiProvider = repo.getAiProviderSync()
        val apiKey = if (aiProvider == AiProvider.VOLCENGINE.value) {
            repo.getVolcengineApiKeySync()
        } else {
            repo.getAsrApiKeySync()
        }
        if (apiKey.isNullOrBlank()) return null
        val baseUrl = repo.getBaseUrlSync().ifBlank { defaultBaseUrl(aiProvider) }
        val modelName = repo.getModelNameSync().ifBlank { defaultModelName(aiProvider) }
        // "其他"平台必须显式填写地址与模型名
        if (aiProvider == AiProvider.CUSTOM.value && (baseUrl.isBlank() || modelName.isBlank())) {
            return null
        }
        return ResolvedModelConfig(
            provider = aiProvider,
            baseUrl = baseUrl,
            modelName = modelName,
            apiKey = apiKey
        )
    }
}
