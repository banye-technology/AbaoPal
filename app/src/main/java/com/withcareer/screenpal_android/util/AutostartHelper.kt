package com.withcareer.screenpal_android.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * 自启动权限帮助类
 * 用于跳转到不同厂商的自启动权限设置页面
 */
object AutostartHelper {
    private const val TAG = "AutostartHelper"

    /**
     * 获取设备制造商
     */
    private fun getManufacturer(): String {
        return Build.MANUFACTURER.lowercase()
    }

    /**
     * 跳转到自启动权限设置页面
     */
    fun openAutostartSettings(context: Context): Boolean {
        val manufacturer = getManufacturer()
        Log.d(TAG, "Resolving device-specific autostart settings")

        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                openXiaomiAutostartSettings(context)
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                openHuaweiAutostartSettings(context)
            }
            manufacturer.contains("oppo") -> {
                openOppoAutostartSettings(context)
            }
            manufacturer.contains("vivo") -> {
                openVivoAutostartSettings(context)
            }
            manufacturer.contains("meizu") -> {
                openMeizuAutostartSettings(context)
            }
            manufacturer.contains("samsung") -> {
                openSamsungAutostartSettings(context)
            }
            manufacturer.contains("oneplus") -> {
                openOnePlusAutostartSettings(context)
            }
            manufacturer.contains("letv") -> {
                openLetvAutostartSettings(context)
            }
            manufacturer.contains("asus") -> {
                openAsusAutostartSettings(context)
            }
            else -> {
                // 尝试打开通用的应用详情页面
                openAppDetailsSettings(context)
            }
        }
    }

    /**
     * 小米/红米自启动设置
     */
    private fun openXiaomiAutostartSettings(context: Context): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Xiaomi autostart settings", e)
            openAppDetailsSettings(context)
        }
    }

    /**
     * 华为/荣耀自启动设置
     */
    private fun openHuaweiAutostartSettings(context: Context): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Huawei autostart settings", e)
            // 尝试备用方案
            try {
                val intent2 = Intent().apply {
                    component = ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent2)
                true
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to open Huawei autostart settings (backup)", e2)
                openAppDetailsSettings(context)
            }
        }
    }

    /**
     * OPPO自启动设置
     */
    private fun openOppoAutostartSettings(context: Context): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open OPPO autostart settings", e)
            // 尝试备用方案
            try {
                val intent2 = Intent().apply {
                    component = ComponentName(
                        "com.oppo.safe",
                        "com.oppo.safe.permission.startup.StartupAppListActivity"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent2)
                true
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to open OPPO autostart settings (backup)", e2)
                openAppDetailsSettings(context)
            }
        }
    }

    /**
     * VIVO自启动设置
     */
    private fun openVivoAutostartSettings(context: Context): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open VIVO autostart settings", e)
            // 尝试备用方案
            try {
                val intent2 = Intent().apply {
                    component = ComponentName(
                        "com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent2)
                true
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to open VIVO autostart settings (backup)", e2)
                openAppDetailsSettings(context)
            }
        }
    }

    /**
     * 魅族自启动设置
     */
    private fun openMeizuAutostartSettings(context: Context): Boolean {
        return try {
            val intent = Intent("com.meizu.safe.security.SHOW_APPSEC").apply {
                putExtra("packageName", context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Meizu autostart settings", e)
            openAppDetailsSettings(context)
        }
    }

    /**
     * 三星自启动设置
     */
    private fun openSamsungAutostartSettings(context: Context): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Samsung battery settings", e)
            openAppDetailsSettings(context)
        }
    }

    /**
     * OnePlus自启动设置
     */
    private fun openOnePlusAutostartSettings(context: Context): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open OnePlus autostart settings", e)
            openAppDetailsSettings(context)
        }
    }

    /**
     * 乐视自启动设置
     */
    private fun openLetvAutostartSettings(context: Context): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.letv.android.letvsafe",
                    "com.letv.android.letvsafe.AutobootManageActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Letv autostart settings", e)
            openAppDetailsSettings(context)
        }
    }

    /**
     * 华硕自启动设置
     */
    private fun openAsusAutostartSettings(context: Context): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.asus.mobilemanager",
                    "com.asus.mobilemanager.powersaver.PowerSaverSettings"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Asus autostart settings", e)
            openAppDetailsSettings(context)
        }
    }

    /**
     * 打开应用详情设置页面（通用方案）
     */
    private fun openAppDetailsSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app details settings", e)
            false
        }
    }

    /**
     * 检查是否能够跳转到自启动设置页面
     * 注意：这个方法不能完全准确地判断自启动权限是否已授予，
     * 仅用于判断设备是否支持跳转到自启动设置
     */
    fun isAutostartSettingsAvailable(context: Context): Boolean {
        val manufacturer = getManufacturer()

        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                isIntentAvailable(context, ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                ))
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                isIntentAvailable(context, ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                ))
            }
            manufacturer.contains("oppo") -> {
                isIntentAvailable(context, ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                ))
            }
            manufacturer.contains("vivo") -> {
                isIntentAvailable(context, ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                ))
            }
            else -> {
                // 对于其他厂商，默认返回 true，因为可以打开应用详情页面
                true
            }
        }
    }

    /**
     * 检查Intent是否可用
     */
    private fun isIntentAvailable(context: Context, component: ComponentName): Boolean {
        return try {
            val intent = Intent().apply {
                this.component = component
            }
            val packageManager = context.packageManager
            val list = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            list.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking intent availability", e)
            false
        }
    }

    /**
     * 获取当前设备的厂商名称（用于显示）
     */
    fun getManufacturerDisplayName(): String {
        val manufacturer = getManufacturer()
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> "小米/红米"
            manufacturer.contains("huawei") -> "华为"
            manufacturer.contains("honor") -> "荣耀"
            manufacturer.contains("oppo") -> "OPPO"
            manufacturer.contains("vivo") -> "VIVO"
            manufacturer.contains("meizu") -> "魅族"
            manufacturer.contains("samsung") -> "三星"
            manufacturer.contains("oneplus") -> "一加"
            manufacturer.contains("letv") -> "乐视"
            manufacturer.contains("asus") -> "华硕"
            else -> Build.MANUFACTURER
        }
    }
}
