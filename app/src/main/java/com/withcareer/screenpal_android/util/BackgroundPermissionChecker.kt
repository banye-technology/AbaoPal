package com.withcareer.screenpal_android.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * 后台运行权限检测工具
 * 提供多种间接方式来判断应用是否具有后台持续运行的能力
 */
object BackgroundPermissionChecker {
    private const val TAG = "BackgroundPermissionChecker"

    /**
     * 检查应用是否在电池优化白名单中
     * 这是最接近"自启动权限"的系统级检测
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isIgnoring = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            Log.d(TAG, "Battery optimization ignored: $isIgnoring")
            isIgnoring
        } else {
            // Android 6.0 以下没有电池优化限制
            true
        }
    }

    /**
     * 请求忽略电池优化
     */
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request battery optimization", e)
            openBatteryOptimizationSettings(context)
        }
    }

    /**
     * 打开电池优化设置页面
     */
    fun openBatteryOptimizationSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open battery optimization settings", e)
            false
        }
    }

    /**
     * 检查后台限制状态（Android 7.0+）
     */
    fun isBackgroundRestricted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val isRestricted = activityManager.isBackgroundRestricted
            Log.d(TAG, "Background restricted: $isRestricted")
            isRestricted
        } else {
            false
        }
    }

    /**
     * 综合评估：应用是否具有良好的后台运行能力
     */
    fun hasGoodBackgroundCapability(context: Context): BackgroundCapability {
        val batteryOptimized = isIgnoringBatteryOptimizations(context)
        val backgroundRestricted = isBackgroundRestricted(context)
        val autostartAvailable = AutostartHelper.isAutostartSettingsAvailable(context)

        return BackgroundCapability(
            isIgnoringBatteryOptimizations = batteryOptimized,
            isBackgroundRestricted = backgroundRestricted,
            hasAutostartSettings = autostartAvailable,
            overallScore = calculateScore(batteryOptimized, backgroundRestricted)
        )
    }

    private fun calculateScore(batteryOptimized: Boolean, backgroundRestricted: Boolean): Int {
        var score = 0
        if (batteryOptimized) score += 50
        if (!backgroundRestricted) score += 50
        return score
    }

    /**
     * 后台运行能力评估结果
     */
    data class BackgroundCapability(
        val isIgnoringBatteryOptimizations: Boolean,
        val isBackgroundRestricted: Boolean,
        val hasAutostartSettings: Boolean,
        val overallScore: Int
    ) {
        /**
         * 是否推荐用户优化后台设置
         */
        fun shouldOptimize(): Boolean {
            return overallScore < 100
        }

        /**
         * 获取需要优化的项目列表
         */
        fun getOptimizationSuggestions(): List<OptimizationItem> {
            val suggestions = mutableListOf<OptimizationItem>()

            if (!isIgnoringBatteryOptimizations) {
                suggestions.add(OptimizationItem.BATTERY_OPTIMIZATION)
            }

            if (isBackgroundRestricted) {
                suggestions.add(OptimizationItem.BACKGROUND_RESTRICTION)
            }

            if (hasAutostartSettings) {
                suggestions.add(OptimizationItem.AUTOSTART)
            }

            return suggestions
        }
    }

    /**
     * 需要优化的项目
     */
    enum class OptimizationItem {
        BATTERY_OPTIMIZATION,
        BACKGROUND_RESTRICTION,
        AUTOSTART
    }
}
