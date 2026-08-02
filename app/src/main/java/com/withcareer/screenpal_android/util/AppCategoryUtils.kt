package com.withcareer.screenpal_android.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build

object AppCategoryUtils {
    fun isGame(context: Context, packageName: String?): Boolean {
        val pkg = packageName?.trim().orEmpty()
        if (pkg.isBlank()) return false

        val pm = context.packageManager
        return runCatching {
            val appInfo = pm.getApplicationInfo(pkg, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (appInfo.category == ApplicationInfo.CATEGORY_GAME) return@runCatching true
            }
            val label = runCatching { pm.getApplicationLabel(appInfo)?.toString().orEmpty() }.getOrNull().orEmpty()
            val haystack = (pkg + " " + label).lowercase()
            haystack.contains("game") || haystack.contains("games") || haystack.contains("游戏")
        }.getOrDefault(false)
    }
}
