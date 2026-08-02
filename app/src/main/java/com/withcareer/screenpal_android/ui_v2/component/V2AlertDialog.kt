package com.withcareer.screenpal_android.ui_v2.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * V2 风格的 AlertDialog 组件
 * 统一了项目中所有对话框的样式
 */
@Composable
fun V2AlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
    confirmColor: Color = MaterialTheme.colorScheme.primary,
    dismissColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    isError: Boolean = false
) {
    V2AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmText,
                    color = if (isError) MaterialTheme.colorScheme.error else confirmColor
                )
            }
        },
        dismissButton = if (dismissText != null) {
            {
                TextButton(onClick = onDismiss ?: onDismissRequest) {
                    Text(text = dismissText, color = dismissColor)
                }
            }
        } else null
    )
}

/**
 * V2 风格的 AlertDialog 组件（自定义内容）
 */
@Composable
fun V2AlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(16.dp),
        title = title,
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton
    )
}