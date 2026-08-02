/*
 */
package com.withcareer.screenpal_android.ui_v2.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.max
import kotlin.math.min

@Composable
fun V2DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    minSize: DpSize = DpSize.Unspecified,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return

    val density = LocalDensity.current
    val offsetPx = remember(offset, density) {
        with(density) {
            IntOffset(offset.x.roundToPx(), offset.y.roundToPx())
        }
    }
    val positionProvider = remember(offsetPx) {
        V2MenuPopupPositionProvider(offsetPx)
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 0.dp,
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = (if (minSize != DpSize.Unspecified) {
                    Modifier.defaultMinSize(minWidth = minSize.width, minHeight = minSize.height)
                } else {
                    Modifier
                })
                    .then(modifier)
                    .then(Modifier.widthIn(max = 260.dp))
                    .then(Modifier.wrapContentWidth(unbounded = true)),
                content = content
            )
        }
    }
}
@Immutable
private class V2MenuPopupPositionProvider(
    private val offsetPx: IntOffset
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val desiredX = anchorBounds.left + offsetPx.x
        val desiredY = anchorBounds.bottom + offsetPx.y

        val maxX = max(0, windowSize.width - popupContentSize.width)
        val maxY = max(0, windowSize.height - popupContentSize.height)

        val x = min(max(0, desiredX), maxX)
        val y = min(max(0, desiredY), maxY)

        return IntOffset(x, y)
    }
}


@Composable
fun V2DropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    contentPadding: PaddingValues? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val leadingSlot: (@Composable () -> Unit)? = if (leadingIcon != null) {
        {
            Icon(
                imageVector = leadingIcon,
                contentDescription = text,
                tint = leadingIconTint
            )
        }
    } else {
        null
    }

    val textSlot: @Composable () -> Unit = {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }

    val layoutDirection = LocalLayoutDirection.current
    val adjustedPadding = if (contentPadding != null) {
        val startPadding = contentPadding.calculateLeftPadding(layoutDirection) + 8.dp
        val rawEndPadding = contentPadding.calculateRightPadding(layoutDirection) - 4.dp
        val endPadding = if (rawEndPadding < 0.dp) 0.dp else rawEndPadding
        PaddingValues(
            start = startPadding,
            end = endPadding,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding()
        )
    } else {
        PaddingValues(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
    }

    DropdownMenuItem(
        text = textSlot,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingSlot,
        trailingIcon = trailingIcon,
        contentPadding = adjustedPadding
    )
}
