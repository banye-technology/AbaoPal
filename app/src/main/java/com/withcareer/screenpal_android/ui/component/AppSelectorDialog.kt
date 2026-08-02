package com.withcareer.screenpal_android.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.withcareer.screenpal_android.R
import com.withcareer.screenpal_android.data.model.AppInfo
import com.withcareer.screenpal_android.util.DeviceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用选择器对话框
 * 允许用户从已安装应用中选择目标应用（支持多选）
 */
@Composable
fun AppSelectorDialog(
    preSelectedPackages: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>, Map<String, String>) -> Unit
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedPackages by remember { mutableStateOf(preSelectedPackages.toSet()) }
    var searchQuery by remember { mutableStateOf("") }

    // 加载应用列表
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            apps = DeviceUtils.getInstalledAppsInfo(context)
            isLoading = false
        }
    }

    // 过滤应用
    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) {
            apps
        } else {
            apps.filter {
                it.label.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        shape = RoundedCornerShape(16.dp),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_selector_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 搜索框
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(stringResource(R.string.app_selector_search_label)) },
                    placeholder = { Text(stringResource(R.string.app_selector_search_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 加载状态
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    // 应用列表
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                    ) {
                        if (filteredApps.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        stringResource(R.string.app_selector_empty_result),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(filteredApps) { app ->
                                AppItem(
                                    app = app,
                                    isSelected = app.packageName in selectedPackages,
                                    onToggle = {
                                        selectedPackages = if (app.packageName in selectedPackages) {
                                            selectedPackages - app.packageName
                                        } else {
                                            selectedPackages + app.packageName
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // 从完整的应用列表中获取所有选中包名的应用名
                    // 注意：这里使用 apps（完整列表），而不是 filteredApps
                    val appNamesMap = apps
                        .filter { it.packageName in selectedPackages }
                        .associate { it.packageName to it.label }
                    onConfirm(selectedPackages.toList(), appNamesMap)
                },
                enabled = selectedPackages.isNotEmpty() && !isLoading
            ) {
                Text(stringResource(R.string.app_selector_confirm_format, selectedPackages.size))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * 单个应用项
 */
@Composable
private fun AppItem(
    app: AppInfo,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() }
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 应用图标
        val iconBitmap = remember(app.icon) {
            try {
                app.icon?.toBitmap()?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }

        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
        } else {
            Box(modifier = Modifier.size(40.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
