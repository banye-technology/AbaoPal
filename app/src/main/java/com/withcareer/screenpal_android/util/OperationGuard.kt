package com.withcareer.screenpal_android.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.withcareer.screenpal_android.R
import com.withcareer.screenpal_android.data.preference.PreferencesRepository
import com.withcareer.screenpal_android.service.ScreenPalAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class OperationCheckFailure {
    API_KEY_MISSING,
    ACCESSIBILITY_NOT_READY,
    RECORD_AUDIO_DENIED,
    OVERLAY_DENIED
}

data class OperationCheckResult(
    val passed: Boolean,
    val failure: OperationCheckFailure? = null
)

/**
 * 功能操作卫士
 * 负责统一检查功能执行的前置条件（如权限、激活状态、API Key等）
 */
class OperationGuard(private val context: Context) {

    private val preferencesRepository = PreferencesRepository(context)
    private var apiKey: String? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    init {
        scope.launch {
            preferencesRepository.apiKey.collectLatest {
                apiKey = it
            }
        }
    }

    /**
     * 检查前置条件
     * @param requireApiKey 是否需要 API Key
     * @param requireRecordAudio 是否需要录音权限
     * @param requireAccessibility 是否需要无障碍权限
     * @param requireOverlay 是否需要悬浮窗权限
     * @param passedApiKey 传入的 API Key（可选，如果传入则优先使用）
     * @param action 检查通过后执行的操作
     * @return Boolean 是否检查通过
     */
    suspend fun check(
        requireApiKey: Boolean = true,
        requireRecordAudio: Boolean = false,
        requireAccessibility: Boolean = false,
        requireOverlay: Boolean = false,
        passedApiKey: String? = null,
        action: (() -> Unit)? = null
    ): Boolean {
        val result = checkWithResult(
            requireApiKey = requireApiKey,
            requireRecordAudio = requireRecordAudio,
            requireAccessibility = requireAccessibility,
            requireOverlay = requireOverlay,
            passedApiKey = passedApiKey,
            action = action
        )
        return result.passed
    }

    suspend fun checkWithResult(
        requireApiKey: Boolean = true,
        requireRecordAudio: Boolean = false,
        requireAccessibility: Boolean = false,
        requireOverlay: Boolean = false,
        passedApiKey: String? = null,
        action: (() -> Unit)? = null
    ): OperationCheckResult {
        // 1. 检查 API Key
        if (requireApiKey) {
            val apiKeyFinal = passedApiKey ?: apiKey ?: preferencesRepository.apiKey.first()

            if (apiKeyFinal.isNullOrBlank()) {
                ToastUtils.show(context, context.getString(R.string.configure_api_key_first), Toast.LENGTH_SHORT)
                return OperationCheckResult(false, OperationCheckFailure.API_KEY_MISSING)
            }
        }

        // 2. 检查无障碍权限
        if (requireAccessibility) {
            // 双重检查：状态标志 + 实例检查
            val isConnected = AccessibilityServiceHelper.isServiceConnected.value
            val hasInstance = ScreenPalAccessibilityService.getInstance() != null

            if (!isConnected || !hasInstance) {
                Log.w("OperationGuard", "Accessibility check failed: isConnected=$isConnected, hasInstance=$hasInstance")
                ToastUtils.show(context, context.getString(R.string.please_enable_accessibility), Toast.LENGTH_LONG)
                return OperationCheckResult(false, OperationCheckFailure.ACCESSIBILITY_NOT_READY)
            }
        }

        // 3. 检查录音权限
        if (requireRecordAudio) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ToastUtils.show(context, context.getString(R.string.please_grant_record_audio), Toast.LENGTH_SHORT)
                return OperationCheckResult(false, OperationCheckFailure.RECORD_AUDIO_DENIED)
            }
        }

        // 4. 检查悬浮窗权限
        if (requireOverlay) {
            if (!Settings.canDrawOverlays(context)) {
                ToastUtils.show(context, context.getString(R.string.floating_permission_request), Toast.LENGTH_SHORT)
                return OperationCheckResult(false, OperationCheckFailure.OVERLAY_DENIED)
            }
        }

        // 所有检查通过，执行操作
        action?.invoke()
        return OperationCheckResult(true)
    }
}
