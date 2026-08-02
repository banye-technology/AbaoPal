package com.withcareer.screenpal_android.util

import android.content.Context
import android.content.Intent
import com.withcareer.screenpal_android.data.model.AppInfo
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * 设备工具类
 */
object DeviceUtils {

    /**
     * 判断是否是模拟器
     */
    fun isEmulator(): Boolean {
        return (android.os.Build.FINGERPRINT.startsWith("generic")
                || android.os.Build.FINGERPRINT.startsWith("unknown")
                || android.os.Build.MODEL.contains("google_sdk")
                || android.os.Build.MODEL.contains("Emulator")
                || android.os.Build.MODEL.contains("Android SDK built for x86")
                || android.os.Build.MANUFACTURER.contains("Genymotion")
                || (android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic"))
                || "google_sdk" == android.os.Build.PRODUCT)
    }

    private data class LauncherEntry(
        val label: String,
        val packageName: String,
        val activityName: String,
        val normalizedLabel: String,
        val normalizedPackageName: String
    )

    private var cachedLauncherEntries: List<LauncherEntry>? = null
    private var lastLauncherEntriesUpdateTime: Long = 0
    private const val APP_LIST_CACHE_DURATION = 5 * 60 * 1000L

    /**
     * 获取已安装的应用列表 (仅包含可启动的应用)
     * 使用 queryIntentActivities 获取 Launcher 入口，更准确且符合用户视角
     * 增加了缓存机制，默认缓存1分钟
     */
    fun getInstalledAppNames(context: Context, forceRefresh: Boolean = false): List<String> {
        val entries = getLauncherEntries(context, forceRefresh)
        return entries.map { it.label }.distinct().sorted()
    }

    private fun getLauncherEntries(context: Context, forceRefresh: Boolean = false): List<LauncherEntry> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedLauncherEntries != null && now - lastLauncherEntriesUpdateTime < APP_LIST_CACHE_DURATION) {
            return cachedLauncherEntries!!
        }

        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val apps = pm.queryIntentActivities(intent, 0)

        val entries = apps.mapNotNull { resolveInfo ->
            val label = resolveInfo.loadLabel(pm).toString().trim()
            val pkg = resolveInfo.activityInfo.packageName?.trim().orEmpty()
            val activity = resolveInfo.activityInfo.name?.trim().orEmpty()
            if (label.isBlank() || pkg.isBlank() || activity.isBlank()) return@mapNotNull null

            LauncherEntry(
                label = label,
                packageName = pkg,
                activityName = activity,
                normalizedLabel = normalizeAppKey(label),
                normalizedPackageName = normalizeAppKey(pkg)
            )
        }

        cachedLauncherEntries = entries
        lastLauncherEntriesUpdateTime = now
        return entries
    }

    /**
     * 根据应用名称获取启动 Intent
     * 直接返回具体的 Activity Intent，解决一个包可能有多个入口的问题
     * 支持传入 Label 或 Package Name
     */
    fun getLaunchIntentByLabel(context: Context, targetLabel: String): Intent? {
        val pm = context.packageManager

        // 0. 尝试直接作为包名获取
        try {
            val intent = pm.getLaunchIntentForPackage(targetLabel)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return intent
            }
        } catch (e: Exception) {
            // Ignore
        }

        val input = targetLabel.trim()
        val normalizedInput = normalizeAppKey(input)
        val entries = getLauncherEntries(context)
        if (entries.isEmpty()) return null

        val exactLabel = entries.firstOrNull { it.label.equals(input, ignoreCase = true) }
        if (exactLabel != null) return buildLaunchIntent(exactLabel)

        if (normalizedInput.isBlank()) return null

        val exactNormalizedLabel = entries.firstOrNull { it.normalizedLabel == normalizedInput }
        if (exactNormalizedLabel != null) return buildLaunchIntent(exactNormalizedLabel)

        val exactNormalizedPackage = entries.firstOrNull { it.normalizedPackageName == normalizedInput }
        if (exactNormalizedPackage != null) return buildLaunchIntent(exactNormalizedPackage)

        val containsNormalized = entries.firstOrNull { it.normalizedLabel.contains(normalizedInput) }
        if (containsNormalized != null) return buildLaunchIntent(containsNormalized)

        val best = entries
            .asSequence()
            .map { it to scoreEntryMatch(it, normalizedInput) }
            .maxByOrNull { it.second }

        val bestEntry = best?.first
        val bestScore = best?.second ?: 0
        return if (bestEntry != null && bestScore >= 600) buildLaunchIntent(bestEntry) else null
    }

    private fun buildLaunchIntent(entry: LauncherEntry): Intent {
        return Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(entry.packageName, entry.activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun normalizeAppKey(input: String): String {
        val lower = input.lowercase(Locale.getDefault())
        return lower.replace(Regex("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsHan}]"), "")
    }

    private fun scoreEntryMatch(entry: LauncherEntry, normalizedInput: String): Int {
        val label = entry.normalizedLabel
        val pkg = entry.normalizedPackageName

        if (label == normalizedInput) return 1000
        if (pkg == normalizedInput) return 980
        if (label.isNotEmpty() && normalizedInput.isNotEmpty()) {
            if (label.contains(normalizedInput)) return 930 - (label.length - normalizedInput.length)
            if (normalizedInput.contains(label)) return 900 - (normalizedInput.length - label.length)
        }

        val labelScore = similarityScore(label, normalizedInput)
        val pkgScore = similarityScore(pkg, normalizedInput)
        return max(labelScore, pkgScore)
    }

    private fun similarityScore(a: String, b: String): Int {
        if (a.isBlank() || b.isBlank()) return 0
        val distance = levenshteinDistance(a, b)
        val maxLen = max(a.length, b.length)
        val similarity = 1.0 - (distance.toDouble() / maxLen.toDouble())
        return (similarity * 800.0).toInt()
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val n = a.length
        val m = b.length
        if (n == 0) return m
        if (m == 0) return n

        val prev = IntArray(m + 1) { it }
        val curr = IntArray(m + 1)

        for (i in 1..n) {
            curr[0] = i
            val ac = a[i - 1]
            for (j in 1..m) {
                val cost = if (ac == b[j - 1]) 0 else 1
                curr[j] = min(
                    min(curr[j - 1] + 1, prev[j] + 1),
                    prev[j - 1] + cost
                )
            }
            for (j in 0..m) prev[j] = curr[j]
        }
        return prev[m]
    }

    /**
     * 获取已安装应用的详细信息（包名、名称、图标）
     * 仅包含可启动的应用，并按名称排序
     */
    fun getInstalledAppsInfo(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        val apps = pm.queryIntentActivities(intent, 0)

        return apps.mapNotNull { resolveInfo ->
            try {
                val packageName = resolveInfo.activityInfo.packageName
                val label = resolveInfo.loadLabel(pm).toString()
                val icon = resolveInfo.loadIcon(pm)

                if (label.isNotBlank() && packageName.isNotBlank()) {
                    AppInfo(packageName, label, icon)
                } else null
            } catch (e: Exception) {
                null
            }
        }.distinctBy { it.packageName }
         .sortedBy { it.label }
    }

    /**
     * 唤醒屏幕
     */
    fun wakeUpScreen(context: Context) {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!powerManager.isInteractive) {
                @Suppress("DEPRECATION")
                val wakeLock = powerManager.newWakeLock(
                    android.os.PowerManager.FULL_WAKE_LOCK or
                    android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    android.os.PowerManager.ON_AFTER_RELEASE,
                    "ScreenPal:WakeUpScreen"
                )
                wakeLock.acquire(3000) // Keep screen on for 3 seconds
            }
        } catch (_: Exception) {
            android.util.Log.w("DeviceUtils", "Failed to open system settings")
        }
    }

    /**
     * 根据应用名称获取包名 (保留用于兼容性)
     */
    fun getPackageNameByLabel(context: Context, targetLabel: String): String? {
        return getLaunchIntentByLabel(context, targetLabel)?.component?.packageName
    }

    /**
     * 获取真实的屏幕尺寸（包含状态栏和导航栏）
     */
    fun getRealScreenSize(context: Context): Pair<Int, Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager

        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            Pair(metrics.widthPixels, metrics.heightPixels)
        }
    }

    /**
     * 获取当前屏幕旋转角度（0/90/180/270）
     */
    fun getDisplayRotation(context: Context): Int {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        @Suppress("DEPRECATION")
        val rotation = windowManager.defaultDisplay.rotation
        return when (rotation) {
            android.view.Surface.ROTATION_90 -> 90
            android.view.Surface.ROTATION_180 -> 180
            android.view.Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

}
