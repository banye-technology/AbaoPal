package com.withcareer.screenpal_android.data.preference

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import android.util.Log
import com.withcareer.screenpal_android.config.AppConfig
import com.withcareer.screenpal_android.config.AliyunVoice
import com.withcareer.screenpal_android.data.model.ModelProfile

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object PreferenceKeys {
    val MAX_CONTEXT_ROUNDS = intPreferencesKey("max_context_rounds")
    val API_KEY = stringPreferencesKey("api_key")
    val BASE_URL = stringPreferencesKey("base_url")
    val MODEL_NAME = stringPreferencesKey("model_name")
    val TTS_ENABLED = booleanPreferencesKey("tts_enabled")
    val FLOATING_ASSISTANT_ENABLED = booleanPreferencesKey("floating_assistant_enabled")
    val FLOATING_BALL_SIZE = intPreferencesKey("floating_ball_size")
    val ASR_URL = stringPreferencesKey("asr_url")
    val ASR_TYPE = stringPreferencesKey("asr_type")
    val ASR_API_KEY = stringPreferencesKey("asr_api_key")
    val MAX_EXECUTION_STEPS = intPreferencesKey("max_execution_steps")
    val AI_PROVIDER = stringPreferencesKey("ai_provider")
    val ASR_MODEL = stringPreferencesKey("asr_model")
    val LANGUAGE = stringPreferencesKey("language")
    val FLOATING_BALL_X = intPreferencesKey("floating_ball_x")
    val FLOATING_BALL_Y = intPreferencesKey("floating_ball_y")
    val WAKEUP_ENABLED = booleanPreferencesKey("wakeup_enabled")
    val WAKE_WORD = stringPreferencesKey("wake_word")
    val VOLCENGINE_API_KEY = stringPreferencesKey("volcengine_api_key")
    val TTS_PROVIDER = stringPreferencesKey("tts_provider")
    val ALIYUN_VOICE = stringPreferencesKey("aliyun_voice")

    // 多套模型配置（JSON 存储）
    val MODEL_PROFILES = stringPreferencesKey("model_profiles")
    val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")

    // 音色相关配置
    val VOICEPRINT_ENABLED = booleanPreferencesKey("voiceprint_enabled")
    val VOICEPRINT_THRESHOLD = stringPreferencesKey("voiceprint_threshold") // 存储为String

    val DISABLED_SKILL_IDS = stringPreferencesKey("disabled_skill_ids")
    val DISABLED_MCP_TOOLS = stringPreferencesKey("disabled_mcp_tools")
    val UI_TREE_FIRST_ENABLED = booleanPreferencesKey("ui_tree_first_enabled")
    val PERMISSION_GUIDE_SHOWN = booleanPreferencesKey("permission_guide_shown")
}


/**
 * 偏好设置仓库
 * 管理应用的所有持久化配置
 */
class PreferencesRepository(private val context: Context) {
    private val gson = Gson()


    val apiKey: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.API_KEY]
    }

    val baseUrl: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.BASE_URL] ?: ""
    }

    val modelName: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.MODEL_NAME] ?: AppConfig.DEFAULT_MODEL_NAME
    }

    val ttsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.TTS_ENABLED] ?: false
    }

    val floatingAssistantEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.FLOATING_ASSISTANT_ENABLED] ?: true
    }

    val floatingBallSize: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.FLOATING_BALL_SIZE] ?: 1 // 0: Small, 1: Medium, 2: Large
    }

    val asrUrl: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.ASR_URL] ?: ""
    }

    val asrType: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.ASR_TYPE] ?: AppConfig.DEFAULT_ASR_PROVIDER
    }

    val asrApiKey: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.ASR_API_KEY]
    }

    val asrModel: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.ASR_MODEL] ?: AppConfig.DEFAULT_ASR_MODEL
    }

    val maxExecutionSteps: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.MAX_EXECUTION_STEPS] ?: Int.MAX_VALUE
    }

    val aiProvider: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.AI_PROVIDER] ?: AppConfig.DEFAULT_AI_PROVIDER
    }

    val maxContextRounds: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.MAX_CONTEXT_ROUNDS] ?: 6
    }

    val language: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.LANGUAGE] ?: "zh"
    }

    val floatingBallX: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.FLOATING_BALL_X] ?: 0
    }

    val floatingBallY: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.FLOATING_BALL_Y] ?: 200
    }

    val wakeupEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.WAKEUP_ENABLED] ?: true
    }

    val wakeWord: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.WAKE_WORD] ?: "阿宝同学"
    }

    val permissionGuideShown: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.PERMISSION_GUIDE_SHOWN] ?: false
    }

    val volcengineApiKey: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.VOLCENGINE_API_KEY]
    }

    val ttsProvider: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.TTS_PROVIDER] ?: AppConfig.DEFAULT_TTS_PROVIDER
    }

    val aliyunVoice: Flow<String> = context.dataStore.data.map { preferences ->
        val savedVoice = preferences[PreferenceKeys.ALIYUN_VOICE] ?: AppConfig.DEFAULT_ALIYUN_VOICE
        // 验证音色是否有效，如果无效则使用默认音色
        try {
            AliyunVoice.fromValue(savedVoice)
            savedVoice
        } catch (e: Exception) {
            Log.w("PreferencesRepository", "Invalid saved voice configuration; using default")
            AppConfig.DEFAULT_ALIYUN_VOICE
        }
    }

    val voiceprintEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.VOICEPRINT_ENABLED] ?: false
    }

    val voiceprintThreshold: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.VOICEPRINT_THRESHOLD]?.toFloatOrNull() ?: 0.6f
    }

    val uiTreeFirstEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.UI_TREE_FIRST_ENABLED] ?: true
    }

    val disabledSkillIds: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        parseStringSet(preferences[PreferenceKeys.DISABLED_SKILL_IDS])
    }

    val disabledMcpTools: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        parseStringSet(preferences[PreferenceKeys.DISABLED_MCP_TOOLS])
    }

    suspend fun saveApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.API_KEY] = apiKey
        }
    }

    suspend fun saveBaseUrl(baseUrl: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.BASE_URL] = baseUrl
        }
    }

    suspend fun saveModelName(modelName: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.MODEL_NAME] = modelName
        }
    }

    suspend fun saveWakeupEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.WAKEUP_ENABLED] = enabled
        }
    }

    suspend fun saveWakeWord(word: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.WAKE_WORD] = word
        }
    }

    suspend fun savePermissionGuideShown(shown: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.PERMISSION_GUIDE_SHOWN] = shown
        }
    }

    suspend fun saveVolcengineApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.VOLCENGINE_API_KEY] = key
        }
    }

    suspend fun saveTtsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.TTS_ENABLED] = enabled
        }
    }

    suspend fun saveFloatingAssistantEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.FLOATING_ASSISTANT_ENABLED] = enabled
        }
    }

    suspend fun saveFloatingBallSize(size: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.FLOATING_BALL_SIZE] = size
        }
    }

    suspend fun saveAsrUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.ASR_URL] = url
        }
    }

    suspend fun saveAsrType(type: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.ASR_TYPE] = type
        }
    }

    suspend fun saveAsrApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.ASR_API_KEY] = key
        }
    }

    suspend fun saveAsrModel(model: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.ASR_MODEL] = model
        }
    }

    suspend fun saveAiProvider(provider: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.AI_PROVIDER] = provider
        }
    }

    suspend fun saveMaxContextRounds(rounds: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.MAX_CONTEXT_ROUNDS] = rounds
        }
    }

    suspend fun saveLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.LANGUAGE] = language
        }
    }

    suspend fun saveFloatingBallPosition(x: Int, y: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.FLOATING_BALL_X] = x
            preferences[PreferenceKeys.FLOATING_BALL_Y] = y
        }
    }

    suspend fun saveMaxExecutionSteps(steps: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.MAX_EXECUTION_STEPS] = steps
        }
    }

    suspend fun getApiKeySync(): String? {
        return context.dataStore.data.map {
            it[PreferenceKeys.API_KEY]
        }.firstOrNull()
    }

    suspend fun getBaseUrlSync(): String {
        return context.dataStore.data.map {
            it[PreferenceKeys.BASE_URL] ?: ""
        }.firstOrNull() ?: ""
    }

    suspend fun getModelNameSync(): String {
        return context.dataStore.data.map {
            it[PreferenceKeys.MODEL_NAME] ?: AppConfig.DEFAULT_MODEL_NAME
        }.firstOrNull() ?: AppConfig.DEFAULT_MODEL_NAME
    }

    suspend fun getFloatingAssistantEnabledSync(): Boolean {
        return context.dataStore.data.map { it[PreferenceKeys.FLOATING_ASSISTANT_ENABLED] ?: true }.firstOrNull() ?: true
    }

    suspend fun getAsrUrlSync(): String {
        return context.dataStore.data.map {
            it[PreferenceKeys.ASR_URL] ?: ""
        }.firstOrNull() ?: ""
    }

    suspend fun getAsrTypeSync(): String {
        return context.dataStore.data.map {
            it[PreferenceKeys.ASR_TYPE] ?: AppConfig.DEFAULT_ASR_PROVIDER
        }.firstOrNull() ?: AppConfig.DEFAULT_ASR_PROVIDER
    }

    suspend fun getAsrApiKeySync(): String? {
        return context.dataStore.data.map {
            it[PreferenceKeys.ASR_API_KEY]
        }.firstOrNull()
    }

    suspend fun getAsrModelSync(): String {
        return context.dataStore.data.map {
            it[PreferenceKeys.ASR_MODEL] ?: AppConfig.DEFAULT_ASR_MODEL
        }.firstOrNull() ?: AppConfig.DEFAULT_ASR_MODEL
    }

    suspend fun getMaxContextRoundsSync(): Int {
        return context.dataStore.data.map {
            it[PreferenceKeys.MAX_CONTEXT_ROUNDS] ?: 6
        }.firstOrNull() ?: 6
    }

    suspend fun getMaxExecutionStepsSync(): Int {
        return context.dataStore.data.map {
            it[PreferenceKeys.MAX_EXECUTION_STEPS] ?: Int.MAX_VALUE
        }.firstOrNull() ?: Int.MAX_VALUE
    }

    suspend fun getAiProviderSync(): String {
        return context.dataStore.data.map {
            it[PreferenceKeys.AI_PROVIDER] ?: AppConfig.DEFAULT_AI_PROVIDER
        }.firstOrNull() ?: AppConfig.DEFAULT_AI_PROVIDER
    }

    suspend fun getLanguageSync(): String {
        return context.dataStore.data.map {
            it[PreferenceKeys.LANGUAGE] ?: "zh"
        }.firstOrNull() ?: "zh"
    }

    suspend fun getWakeupEnabledSync(): Boolean {
        return context.dataStore.data.map {
            it[PreferenceKeys.WAKEUP_ENABLED] ?: true
        }.firstOrNull() ?: true
    }

    suspend fun getPermissionGuideShownSync(): Boolean {
        return context.dataStore.data.map {
            it[PreferenceKeys.PERMISSION_GUIDE_SHOWN] ?: false
        }.firstOrNull() ?: false
    }

    suspend fun getVolcengineApiKeySync(): String? {
        return context.dataStore.data.map {
            it[PreferenceKeys.VOLCENGINE_API_KEY]
        }.firstOrNull()
    }

    suspend fun saveTtsProvider(provider: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.TTS_PROVIDER] = provider
        }
    }

    suspend fun saveAliyunVoice(voice: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.ALIYUN_VOICE] = voice
        }
    }

    suspend fun getTtsProviderSync(): String {
        return context.dataStore.data.map {
            it[PreferenceKeys.TTS_PROVIDER] ?: AppConfig.DEFAULT_TTS_PROVIDER
        }.firstOrNull() ?: AppConfig.DEFAULT_TTS_PROVIDER
    }

    suspend fun getAliyunVoiceSync(): String {
        val savedVoice = context.dataStore.data.map {
            it[PreferenceKeys.ALIYUN_VOICE] ?: AppConfig.DEFAULT_ALIYUN_VOICE
        }.firstOrNull() ?: AppConfig.DEFAULT_ALIYUN_VOICE

        // 验证音色是否有效，如果无效则使用默认音色
        return try {
            AliyunVoice.fromValue(savedVoice)
            savedVoice
        } catch (e: Exception) {
            Log.w("PreferencesRepository", "Invalid saved voice configuration; using default")
            AppConfig.DEFAULT_ALIYUN_VOICE
        }
    }

    suspend fun saveVoiceprintEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.VOICEPRINT_ENABLED] = enabled
        }
    }

    suspend fun saveVoiceprintThreshold(threshold: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.VOICEPRINT_THRESHOLD] = threshold.toString()
        }
    }

    suspend fun saveUiTreeFirstEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.UI_TREE_FIRST_ENABLED] = enabled
        }
    }

    suspend fun getVoiceprintEnabledSync(): Boolean {
        return context.dataStore.data.map {
            it[PreferenceKeys.VOICEPRINT_ENABLED] ?: false
        }.firstOrNull() ?: false
    }

    suspend fun getVoiceprintThresholdSync(): Float {
        return context.dataStore.data.map {
            it[PreferenceKeys.VOICEPRINT_THRESHOLD]?.toFloatOrNull() ?: 0.6f
        }.firstOrNull() ?: 0.6f
    }

    suspend fun getUiTreeFirstEnabledSync(): Boolean {
        return context.dataStore.data.map {
            it[PreferenceKeys.UI_TREE_FIRST_ENABLED] ?: true
        }.firstOrNull() ?: true
    }

    suspend fun getDisabledSkillIdsSync(): Set<String> {
        return context.dataStore.data.map { parseStringSet(it[PreferenceKeys.DISABLED_SKILL_IDS]) }.firstOrNull() ?: emptySet()
    }

    suspend fun getDisabledMcpToolsSync(): Set<String> {
        return context.dataStore.data.map { parseStringSet(it[PreferenceKeys.DISABLED_MCP_TOOLS]) }.firstOrNull() ?: emptySet()
    }

    suspend fun setSkillEnabled(skillId: String, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            val current = parseStringSet(preferences[PreferenceKeys.DISABLED_SKILL_IDS]).toMutableSet()
            if (enabled) current.remove(skillId) else current.add(skillId)
            preferences[PreferenceKeys.DISABLED_SKILL_IDS] = gson.toJson(current.toList())
        }
    }

    suspend fun setMcpToolEnabled(toolName: String, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            val current = parseStringSet(preferences[PreferenceKeys.DISABLED_MCP_TOOLS]).toMutableSet()
            if (enabled) current.remove(toolName) else current.add(toolName)
            preferences[PreferenceKeys.DISABLED_MCP_TOOLS] = gson.toJson(current.toList())
        }
    }

    // ==================== 多套模型配置 ====================

    val modelProfiles: Flow<List<ModelProfile>> = context.dataStore.data.map { preferences ->
        parseModelProfiles(preferences[PreferenceKeys.MODEL_PROFILES])
    }

    val activeProfileId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.ACTIVE_PROFILE_ID]
    }

    suspend fun getModelProfilesSync(): List<ModelProfile> {
        return context.dataStore.data.map {
            parseModelProfiles(it[PreferenceKeys.MODEL_PROFILES])
        }.firstOrNull() ?: emptyList()
    }

    suspend fun getActiveProfileIdSync(): String? {
        return context.dataStore.data.map {
            it[PreferenceKeys.ACTIVE_PROFILE_ID]
        }.firstOrNull()
    }

    suspend fun saveModelProfiles(profiles: List<ModelProfile>) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.MODEL_PROFILES] = gson.toJson(profiles)
        }
    }

    suspend fun saveActiveProfileId(id: String?) {
        context.dataStore.edit { preferences ->
            if (id.isNullOrBlank()) {
                preferences.remove(PreferenceKeys.ACTIVE_PROFILE_ID)
            } else {
                preferences[PreferenceKeys.ACTIVE_PROFILE_ID] = id
            }
        }
    }

    private fun parseModelProfiles(json: String?): List<ModelProfile> {
        if (json.isNullOrBlank() || json == "[]") return emptyList()
        return try {
            val type = object : TypeToken<List<ModelProfile>>() {}.type
            gson.fromJson<List<ModelProfile>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseStringSet(json: String?): Set<String> {
        if (json.isNullOrBlank()) return emptySet()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            (gson.fromJson<List<String>>(json, type) ?: emptyList()).filter { it.isNotBlank() }.toSet()
        } catch (_: Exception) {
            json.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        }
    }
}
