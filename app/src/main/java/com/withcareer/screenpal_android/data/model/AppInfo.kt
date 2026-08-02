package com.withcareer.screenpal_android.data.model

import android.graphics.drawable.Drawable

/**
 * 应用信息数据类
 * 用于在应用选择器中展示已安装应用的信息
 */
data class AppInfo(
    val packageName: String,      // 包名：com.tencent.mm
    val label: String,             // 显示名称：微信
    val icon: Drawable? = null    // 应用图标
)
