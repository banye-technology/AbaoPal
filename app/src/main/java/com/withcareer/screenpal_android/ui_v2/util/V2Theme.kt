package com.withcareer.screenpal_android.ui_v2.util

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * V2 风格的卡片颜色
 * 用于统一项目中卡片的背景色
 */
private val V2_LIGHT_CARD_COLOR = Color(0xFFEEF6FF)

/**
 * 获取 V2 风格的卡片颜色
 * 根据当前主题返回合适的卡片背景色
 */
@Composable
fun rememberV2CardColor(): Color {
    val isDarkTheme = isSystemInDarkTheme()
    return if (isDarkTheme) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
    } else {
        V2_LIGHT_CARD_COLOR
    }
}

/**
 * 获取 V2 风格的操作卡片颜色（用于抽屉等区域）
 * 根据当前主题返回合适的卡片背景色
 */
@Composable
fun rememberV2ActionCardColor(alpha: Float = 0.4f): Color {
    val isDarkTheme = isSystemInDarkTheme()
    return if (isDarkTheme) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
    } else {
        V2_LIGHT_CARD_COLOR
    }
}

/**
 * 获取 V2 风格的亮色卡片颜色
 */
@Composable
fun rememberV2LightCardColor(): Color {
    return V2_LIGHT_CARD_COLOR
}