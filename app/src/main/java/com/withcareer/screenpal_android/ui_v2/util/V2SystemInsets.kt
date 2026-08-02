package com.withcareer.screenpal_android.ui_v2.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.max

data class V2WindowInsetsState(
    val statusBarsTop: Dp,
    val navigationBarsBottom: Dp,
    val imeBottomPx: Int
)

val LocalV2WindowInsetsState = staticCompositionLocalOf {
    V2WindowInsetsState(
        statusBarsTop = 24.dp,
        navigationBarsBottom = 0.dp,
        imeBottomPx = 0
    )
}

@Composable
fun ProvideV2WindowInsets(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current

    val statusBarFallbackPx = remember(context) {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
    }

    val navigationBarFallbackPx = remember(context) {
        val resourceId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
    }

    val initialStatusTopPx = remember(view, statusBarFallbackPx) {
        val insetsTop = ViewCompat.getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.statusBars())
            ?.top ?: 0
        max(statusBarFallbackPx, insetsTop)
    }

    val initialNavigationBottomPx = remember(view) {
        ViewCompat.getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.navigationBars())
            ?.bottom ?: 0
    }.let { max(it, navigationBarFallbackPx) }

    val initialImeBottomPx = remember(view) {
        ViewCompat.getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.ime())
            ?.bottom ?: 0
    }

    var statusBarsTopPx by remember(view) { mutableStateOf(initialStatusTopPx) }
    var navigationBarsBottomPx by remember(view) { mutableStateOf(initialNavigationBottomPx) }
    var imeBottomPx by remember(view) { mutableStateOf(initialImeBottomPx) }

    DisposableEffect(view) {
        val listener = OnApplyWindowInsetsListener { _, insets ->
            val statusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navigationBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom

            statusBarsTopPx = max(statusBarFallbackPx, statusTop)
            navigationBarsBottomPx = max(navigationBarFallbackPx, navigationBottom)
            imeBottomPx = imeBottom

            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(view, listener)
        ViewCompat.requestApplyInsets(view)

        onDispose {
            ViewCompat.setOnApplyWindowInsetsListener(view, null)
        }
    }

    val providedState = V2WindowInsetsState(
        statusBarsTop = if (statusBarsTopPx <= 0) 24.dp else with(density) { statusBarsTopPx.toDp() },
        navigationBarsBottom = if (navigationBarsBottomPx <= 0) 0.dp else with(density) { navigationBarsBottomPx.toDp() },
        imeBottomPx = imeBottomPx
    )

    CompositionLocalProvider(
        LocalV2WindowInsetsState provides providedState,
        content = content
    )
}

@Composable
fun rememberV2StatusBarTopPadding(): Dp {
    return LocalV2WindowInsetsState.current.statusBarsTop
}

@Composable
fun rememberV2NavigationBarsBottomPadding(): Dp {
    return LocalV2WindowInsetsState.current.navigationBarsBottom
}
