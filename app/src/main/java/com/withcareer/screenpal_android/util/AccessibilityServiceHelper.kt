package com.withcareer.screenpal_android.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.withcareer.screenpal_android.service.ScreenPalAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AccessibilityServiceHelper {

    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

    fun setServiceConnected(connected: Boolean) {
        _isServiceConnected.value = connected
    }

    /**
     * 检查无障碍服务是否已启用（通过系统设置检查）
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        android.util.Log.d("AccessibilityHelper", "=== isAccessibilityServiceEnabled START ===")

        if (context.getSystemService(Context.ACCESSIBILITY_SERVICE) !is AccessibilityManager) {
            android.util.Log.e("AccessibilityHelper", "AccessibilityManager not available")
            return false
        }

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        if (enabledServices == null) {
            android.util.Log.w("AccessibilityHelper", "No enabled accessibility services found")
            return false
        }

        val expectedComponent = ComponentName(
            context,
            ScreenPalAccessibilityService::class.java
        )
        val result = enabledServices
            .split(':')
            .mapNotNull(ComponentName::unflattenFromString)
            .any { it == expectedComponent }
        android.util.Log.d("AccessibilityHelper", "Service enabled in settings: $result")
        android.util.Log.d("AccessibilityHelper", "=== isAccessibilityServiceEnabled END ===")

        return result
    }

    /**
     * 检查无障碍服务是否正在运行（通过实例检查）
     */
    fun isServiceRunning(): Boolean {
        val result = ScreenPalAccessibilityService.isServiceEnabled()
        android.util.Log.d("AccessibilityHelper", "isServiceRunning (instance check): $result")
        return result
    }

    /**
     * 获取无障碍服务信息
     */
    fun getServiceInfo(context: Context): AccessibilityServiceInfo? {
        android.util.Log.d("AccessibilityHelper", "=== getServiceInfo START ===")

        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        if (accessibilityManager == null) {
            android.util.Log.e("AccessibilityHelper", "AccessibilityManager is null")
            return null
        }

        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        android.util.Log.d("AccessibilityHelper", "Total enabled services: ${enabledServices.size}")

        val serviceName = ScreenPalAccessibilityService::class.java.name

        val result = enabledServices.firstOrNull { serviceInfo ->
            serviceInfo.resolveInfo.serviceInfo.name == serviceName
        }

        android.util.Log.d("AccessibilityHelper", "Service found: ${result != null}")
        android.util.Log.d("AccessibilityHelper", "=== getServiceInfo END ===")

        return result
    }

    fun goToAccessibilitySettings(context: Context) {
        val intent = android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
