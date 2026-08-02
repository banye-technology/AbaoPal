package com.withcareer.screenpal_android.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.withcareer.screenpal_android.data.preference.PreferencesRepository
import com.withcareer.screenpal_android.util.AccessibilityServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import android.util.Log
import com.withcareer.screenpal_android.config.AppConfig
import com.withcareer.screenpal_android.config.AiProvider
import com.withcareer.screenpal_android.config.TtsProvider
import com.withcareer.screenpal_android.core.tts.TtsManager
import com.withcareer.screenpal_android.core.llm.ModelClient
import com.withcareer.screenpal_android.core.llm.ModelConfigResolver
import com.withcareer.screenpal_android.data.model.ModelProfile
import com.withcareer.screenpal_android.R

data class SettingsUiState(
    val apiKey: String = "",
    val baseUrl: String = "",
    val modelName: String = "",
    val maxContextRounds: Int = 5,
    val maxExecutionSteps: Int = Int.MAX_VALUE,
    val asrType: String = AppConfig.DEFAULT_ASR_PROVIDER,
    val asrUrl: String = "",
    val asrApiKey: String = "",
    val volcengineApiKey: String = "",
    val asrModel: String = "",
    val aiProvider: String = AppConfig.DEFAULT_AI_PROVIDER,
    val     isAccessibilityEnabled: Boolean = false,
    val isRecordAudioGranted: Boolean = false,
    val isIgnoringBatteryOptimizations: Boolean = false,
    val isTtsEnabled: Boolean = false,
    val ttsProvider: String = AppConfig.DEFAULT_TTS_PROVIDER,
    val aliyunVoice: String = AppConfig.DEFAULT_ALIYUN_VOICE,
    val isWakeupEnabled: Boolean = true,
    val wakeWord: String = "你好助手",
    val isFloatingAssistantEnabled: Boolean = false,
    val floatingBallSize: Int = 1,
    val isLoading: Boolean = false,
    val saveSuccess: Boolean? = null,
    val error: String? = null,
    val infoMessage: String? = null,
    val language: String = "zh",
    val voiceprintEnabled: Boolean = false,
    val isVoiceprintEnrolled: Boolean = false,
    val voiceprintThreshold: Float = 0.6f,
    val isUiTreeFirstEnabled: Boolean = false,
    // 多套模型配置
    val modelProfiles: List<ModelProfile> = emptyList(),
    val activeProfileId: String? = null,
    // 模型列表拉取
    val isLoadingModels: Boolean = false,
    val modelOptions: List<String> = emptyList(),
    val modelFetchError: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesRepository = PreferencesRepository(application)
    private val voiceprintManager = com.withcareer.screenpal_android.core.voice.VoiceprintManager(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    init {
        loadSettings()
        checkBatteryOptimization()

        viewModelScope.launch {
            AccessibilityServiceHelper.isServiceConnected.collect {
                checkAccessibilityService()
            }
        }
    }



    private fun loadSettings() {
        viewModelScope.launch {
            preferencesRepository.apiKey.collect { apiKey ->
                _uiState.value = _uiState.value.copy(apiKey = apiKey ?: "")
            }
        }
        viewModelScope.launch {
            preferencesRepository.baseUrl.collect { baseUrl ->
                _uiState.value = _uiState.value.copy(baseUrl = baseUrl ?: "")
            }
        }
        viewModelScope.launch {
            preferencesRepository.modelName.collect { modelName ->
                _uiState.value = _uiState.value.copy(modelName = modelName ?: "")
            }
        }
        viewModelScope.launch {
            preferencesRepository.aiProvider.collect { provider ->
                _uiState.value = _uiState.value.copy(aiProvider = provider)
            }
        }
        viewModelScope.launch {
            preferencesRepository.maxContextRounds.collect { rounds ->
                _uiState.value = _uiState.value.copy(maxContextRounds = rounds)
            }
        }
        viewModelScope.launch {
            preferencesRepository.maxExecutionSteps.collect { steps ->
                _uiState.value = _uiState.value.copy(maxExecutionSteps = steps)
            }
        }
        viewModelScope.launch {
            preferencesRepository.asrType.collect { type ->
                _uiState.value = _uiState.value.copy(asrType = type)
            }
        }
        viewModelScope.launch {
            preferencesRepository.asrUrl.collect { url ->
                _uiState.value = _uiState.value.copy(asrUrl = url ?: "")
            }
        }
        viewModelScope.launch {
            preferencesRepository.asrApiKey.collect { key ->
                _uiState.value = _uiState.value.copy(asrApiKey = key ?: "")
            }
        }
        viewModelScope.launch {
            preferencesRepository.volcengineApiKey.collect { key ->
                _uiState.value = _uiState.value.copy(volcengineApiKey = key ?: "")
            }
        }
        viewModelScope.launch {
            preferencesRepository.asrModel.collect { model ->
                _uiState.value = _uiState.value.copy(asrModel = model)
            }
        }
        viewModelScope.launch {
            preferencesRepository.ttsEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(isTtsEnabled = enabled)
            }
        }
        viewModelScope.launch {
            preferencesRepository.wakeupEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(isWakeupEnabled = enabled)
            }
        }
        viewModelScope.launch {
            preferencesRepository.wakeWord.collect { word ->
                _uiState.value = _uiState.value.copy(wakeWord = word)
            }
        }
        viewModelScope.launch {
            preferencesRepository.uiTreeFirstEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(isUiTreeFirstEnabled = enabled)
            }
        }
        viewModelScope.launch {
            preferencesRepository.floatingAssistantEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(isFloatingAssistantEnabled = enabled)
            }
        }
        viewModelScope.launch {
            preferencesRepository.floatingBallSize.collect { size ->
                _uiState.value = _uiState.value.copy(floatingBallSize = size)
            }
        }
        viewModelScope.launch {
            preferencesRepository.language.collect { language ->
                _uiState.value = _uiState.value.copy(language = language)
            }
        }
        viewModelScope.launch {
            preferencesRepository.ttsProvider.collect { provider ->
                _uiState.value = _uiState.value.copy(ttsProvider = provider)
            }
        }
        viewModelScope.launch {
            preferencesRepository.aliyunVoice.collect { voice ->
                _uiState.value = _uiState.value.copy(aliyunVoice = voice)
            }
        }
        viewModelScope.launch {
            preferencesRepository.voiceprintEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(
                    voiceprintEnabled = enabled,
                    isVoiceprintEnrolled = voiceprintManager.isEnrolled()
                )
            }
        }
        viewModelScope.launch {
            preferencesRepository.voiceprintThreshold.collect { threshold ->
                _uiState.value = _uiState.value.copy(voiceprintThreshold = threshold)
            }
        }
        viewModelScope.launch {
            preferencesRepository.modelProfiles.collect { profiles ->
                _uiState.value = _uiState.value.copy(modelProfiles = profiles)
            }
        }
        viewModelScope.launch {
            preferencesRepository.activeProfileId.collect { id ->
                _uiState.value = _uiState.value.copy(activeProfileId = id)
            }
        }
    }

    // ==================== 多套模型配置 ====================

    /**
     * 切换当前使用的模型配置（null 表示默认配置）
     */
    fun switchModelProfile(id: String?) {
        viewModelScope.launch {
            preferencesRepository.saveActiveProfileId(id)
        }
    }

    /**
     * 新建模型配置档案
     */
    fun createModelProfile(name: String) {
        viewModelScope.launch {
            val profiles = preferencesRepository.getModelProfilesSync().toMutableList()
            val id = java.util.UUID.randomUUID().toString()
            val current = ModelConfigResolver.resolveExecutor(getApplication())
            profiles.add(
                ModelProfile(
                    id = id,
                    name = name.trim(),
                    provider = _uiState.value.aiProvider,
                    baseUrl = "",
                    modelName = "",
                    apiKey = current?.apiKey.orEmpty()
                )
            )
            preferencesRepository.saveModelProfiles(profiles)
            preferencesRepository.saveActiveProfileId(id)
        }
    }

    /**
     * 删除模型配置档案
     */
    fun deleteModelProfile(id: String) {
        viewModelScope.launch {
            val profiles = preferencesRepository.getModelProfilesSync().filterNot { it.id == id }
            preferencesRepository.saveModelProfiles(profiles)
            if (preferencesRepository.getActiveProfileIdSync() == id) {
                preferencesRepository.saveActiveProfileId(null)
            }
        }
    }

    /**
     * 保存当前模型配置（自定义配置或默认配置）
     * @param provider 服务商
     * @param apiKey API Key
     * @param baseUrl 自定义 Base URL（可空）
     * @param modelName 自定义模型名（可空）
     */
    fun saveActiveModelProfile(provider: String, apiKey: String, baseUrl: String, modelName: String) {
        viewModelScope.launch {
            val activeId = preferencesRepository.getActiveProfileIdSync()
            if (activeId.isNullOrBlank()) {
                // 默认配置：保存到本地字段（保持向后兼容）
                preferencesRepository.saveAiProvider(provider)
                if (provider == AiProvider.VOLCENGINE.value) {
                    preferencesRepository.saveVolcengineApiKey(apiKey)
                    preferencesRepository.saveApiKey(apiKey)
                } else {
                    preferencesRepository.saveAsrApiKey(apiKey)
                    preferencesRepository.saveApiKey(apiKey)
                }
                if (baseUrl.isNotBlank()) preferencesRepository.saveBaseUrl(baseUrl)
                if (modelName.isNotBlank()) preferencesRepository.saveModelName(modelName)
            } else {
                // 自定义配置：更新档案
                val profiles = preferencesRepository.getModelProfilesSync().map { profile ->
                    if (profile.id == activeId) {
                        profile.copy(
                            provider = provider,
                            apiKey = apiKey,
                            baseUrl = baseUrl,
                            modelName = modelName
                        )
                    } else {
                        profile
                    }
                }
                preferencesRepository.saveModelProfiles(profiles)
            }

            // 保存成功后，若已填写 API Key，自动拉取可用模型列表
            if (apiKey.isNotBlank()) {
                fetchModelOptions(provider, apiKey, baseUrl)
            }
        }
    }

    /**
     * 拉取可用模型列表（OpenAI 兼容协议 GET /models）
     * @param provider 服务商
     * @param apiKey API Key
     * @param baseUrl 自定义 Base URL（为空时使用服务商默认地址）
     */
    fun fetchModelOptions(provider: String, apiKey: String, baseUrl: String) {
        if (apiKey.isBlank()) {
            _uiState.value = _uiState.value.copy(modelOptions = emptyList(), modelFetchError = null)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingModels = true, modelFetchError = null)
            try {
                val effectiveBaseUrl = baseUrl.ifBlank { ModelConfigResolver.defaultBaseUrl(provider) }
                if (effectiveBaseUrl.isBlank()) {
                    // "其他"平台未填地址，无法拉取
                    _uiState.value = _uiState.value.copy(
                        isLoadingModels = false,
                        modelOptions = emptyList()
                    )
                    return@launch
                }
                val client = ModelClient(effectiveBaseUrl)
                val models = client.listModels(apiKey)
                _uiState.value = _uiState.value.copy(
                    isLoadingModels = false,
                    modelOptions = models,
                    modelFetchError = if (models.isEmpty()) getApplication<Application>()
                        .getString(R.string.model_list_fetch_failed) else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingModels = false,
                    modelOptions = emptyList(),
                    modelFetchError = getApplication<Application>().getString(R.string.model_list_fetch_failed)
                )
            }
        }
    }

    fun checkAccessibilityService() {
        // 优先使用系统 API 检查，更准确
        val serviceInfo = AccessibilityServiceHelper.getServiceInfo(getApplication())
        val enabledInSettings = AccessibilityServiceHelper.isAccessibilityServiceEnabled(getApplication())

        // 如果能获取到服务信息，说明服务已启用
        // 否则检查系统设置（兼容服务刚启动的情况）
        val enabled = serviceInfo != null || enabledInSettings

        _uiState.value = _uiState.value.copy(isAccessibilityEnabled = enabled)

        Log.d(TAG, "checkAccessibilityService: serviceFound=${serviceInfo != null}, enabledInSettings=$enabledInSettings, enabled=$enabled")
    }

    /**
     * 检查电池优化状态（作为自启动权限的间接判断）
     */
    fun checkBatteryOptimization() {
        val isIgnoring = com.withcareer.screenpal_android.util.BackgroundPermissionChecker
            .isIgnoringBatteryOptimizations(getApplication())
        _uiState.value = _uiState.value.copy(isIgnoringBatteryOptimizations = isIgnoring)
    }

    fun saveApiKey(apiKey: String) {
        viewModelScope.launch {
            try {
                // 1. 总是保存到 ASR Key (作为阿里云 Key 的备份)
                preferencesRepository.saveAsrApiKey(apiKey)

                // 2. 如果当前 Provider 是阿里云，则同时更新主 apiKey
                if (_uiState.value.aiProvider == AiProvider.DASHSCOPE.value) {
                    _uiState.value = _uiState.value.copy(apiKey = apiKey)
                    preferencesRepository.saveApiKey(apiKey)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = getApplication<Application>().getString(R.string.save_failed, e.message))
            }
        }
    }

    fun saveVolcengineApiKey(key: String) {
        viewModelScope.launch {
            try {
                preferencesRepository.saveVolcengineApiKey(key)

                // 如果当前 Provider 是火山引擎，则同时更新主 apiKey
                if (_uiState.value.aiProvider == AiProvider.VOLCENGINE.value) {
                    _uiState.value = _uiState.value.copy(apiKey = key)
                    preferencesRepository.saveApiKey(key)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = getApplication<Application>().getString(R.string.save_failed, e.message))
            }
        }
    }

    fun saveAiProvider(provider: String) {
        viewModelScope.launch {
            try {
                preferencesRepository.saveAiProvider(provider)

                // Switch Logic
                if (provider == AiProvider.DASHSCOPE.value) {
                    // Restore Aliyun Key
                    val aliyunKey = _uiState.value.asrApiKey
                    preferencesRepository.saveApiKey(aliyunKey)
                    _uiState.value = _uiState.value.copy(apiKey = aliyunKey)
                } else if (provider == AiProvider.VOLCENGINE.value) {
                    // Switch to Volcengine Key
                    val volcKey = _uiState.value.volcengineApiKey
                    preferencesRepository.saveApiKey(volcKey)
                    _uiState.value = _uiState.value.copy(apiKey = volcKey)
                }

                // 语音包自动切换逻辑：切换到非百炼时，如果使用阿里云语音包则自动切回系统语音包
                if (provider != AiProvider.DASHSCOPE.value) {
                    val currentTtsProvider = _uiState.value.ttsProvider
                    if (currentTtsProvider == TtsProvider.ALIYUN.value) {
                        Log.d(TAG, "AI provider changed; auto-switching TTS to system")
                        preferencesRepository.saveTtsProvider(TtsProvider.SYSTEM.value)
                        _uiState.value = _uiState.value.copy(
                            ttsProvider = TtsProvider.SYSTEM.value,
                            infoMessage = getApplication<Application>().getString(R.string.voice_package_auto_switched)
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = getApplication<Application>().getString(R.string.save_failed, e.message))
            }
        }
    }

    fun checkRecordAudioPermission() {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            getApplication(),
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        _uiState.value = _uiState.value.copy(isRecordAudioGranted = granted)
    }

    fun updateTtsEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isTtsEnabled = enabled)
        viewModelScope.launch {
            try {
                preferencesRepository.saveTtsEnabled(enabled)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = getApplication<Application>().getString(R.string.save_failed, e.message))
            }
        }
    }

    fun saveLanguage(language: String) {
        _uiState.value = _uiState.value.copy(language = language)
        viewModelScope.launch {
            try {
                preferencesRepository.saveLanguage(language)

                val localeList = when (language) {
                    "zh" -> androidx.core.os.LocaleListCompat.forLanguageTags("zh-CN")
                    "en" -> androidx.core.os.LocaleListCompat.forLanguageTags("en-US")
                    else -> androidx.core.os.LocaleListCompat.getEmptyLocaleList()
                }
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(localeList)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = getApplication<Application>().getString(R.string.save_failed, e.message))
            }
        }
    }

    fun updateWakeupEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isWakeupEnabled = enabled)
        viewModelScope.launch {
            try {
                preferencesRepository.saveWakeupEnabled(enabled)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = getApplication<Application>().getString(R.string.save_failed, e.message))
            }
        }
    }

    fun updateWakeWord(word: String) {
        // 记录当前的注册状态，用于判断是否需要清理
        val wasEnrolled = _uiState.value.isVoiceprintEnrolled

        _uiState.value = _uiState.value.copy(wakeWord = word)

        viewModelScope.launch {
            try {
                preferencesRepository.saveWakeWord(word)

                // 如果之前已注册声纹，则进行清理
                if (wasEnrolled) {
                    voiceprintManager.deleteVoiceprint()
                    preferencesRepository.saveVoiceprintEnabled(false)

                    _uiState.value = _uiState.value.copy(
                        isVoiceprintEnrolled = false,
                        voiceprintEnabled = false,
                        infoMessage = getApplication<Application>().getString(R.string.wakeword_changed_re_register_voiceprint)
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = getApplication<Application>().getString(R.string.save_failed, e.message))
            }
        }
    }

    fun updateFloatingAssistantEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isFloatingAssistantEnabled = enabled)
        viewModelScope.launch {
            try {
                preferencesRepository.saveFloatingAssistantEnabled(enabled)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = getApplication<Application>().getString(R.string.save_failed, e.message))
            }
        }
    }

    fun updateUiTreeFirstEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isUiTreeFirstEnabled = enabled)
        viewModelScope.launch {
            try {
                preferencesRepository.saveUiTreeFirstEnabled(enabled)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = getApplication<Application>().getString(R.string.save_failed, e.message))
            }
        }
    }

    fun updateFloatingBallSize(size: Int) {
        _uiState.value = _uiState.value.copy(floatingBallSize = size)
        viewModelScope.launch {
            try {
                preferencesRepository.saveFloatingBallSize(size)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = getApplication<Application>().getString(R.string.save_failed, e.message))
            }
        }
    }

    fun saveTtsProvider(provider: String) {
        _uiState.value = _uiState.value.copy(ttsProvider = provider)
        viewModelScope.launch {
            try {
                preferencesRepository.saveTtsProvider(provider)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = getApplication<Application>().getString(R.string.save_failed, e.message))
            }
        }
    }

    fun saveAliyunVoice(voice: String) {
        _uiState.value = _uiState.value.copy(aliyunVoice = voice)
        viewModelScope.launch {
            try {
                preferencesRepository.saveAliyunVoice(voice)
                // 通知 TtsManager 更新音色
                val ttsManager = TtsManager.getInstance(getApplication())
                ttsManager.updateAliyunVoice(voice)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = getApplication<Application>().getString(R.string.save_failed, e.message))
            }
        }
    }

    fun canSwitchToAliyunVoice(): Boolean {
        // 检查本地存储的 API Key
        return !_uiState.value.apiKey.isNullOrBlank()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null, saveSuccess = false)
    }

    fun clearInfoMessage() {
        _uiState.value = _uiState.value.copy(infoMessage = null)
    }

    /**
     * 保存音色验证开关状态
     */
    fun saveVoiceprintEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                preferencesRepository.saveVoiceprintEnabled(enabled)
                // 如果关闭音色验证，同时更新 UI 状态
                if (!enabled) {
                    _uiState.value = _uiState.value.copy(voiceprintEnabled = false)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = getApplication<Application>().getString(R.string.save_failed, e.message)
                )
            }
        }
    }

    /**
     * 删除音色
     */
    fun deleteVoiceprint() {
        viewModelScope.launch {
            try {
                voiceprintManager.deleteVoiceprint()
                preferencesRepository.saveVoiceprintEnabled(false)
                _uiState.value = _uiState.value.copy(isVoiceprintEnrolled = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = getApplication<Application>().getString(R.string.save_failed, e.message)
                )
            }
        }
    }

    /**
     * 完成音色注册
     */
    fun onVoiceprintEnrolled() {
        viewModelScope.launch {
            try {
                preferencesRepository.saveVoiceprintEnabled(true)
                _uiState.value = _uiState.value.copy(isVoiceprintEnrolled = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = getApplication<Application>().getString(R.string.save_failed, e.message)
                )
            }
        }
    }

    /**
     * 获取VoiceprintManager实例
     */
    fun getVoiceprintManager() = voiceprintManager
}
