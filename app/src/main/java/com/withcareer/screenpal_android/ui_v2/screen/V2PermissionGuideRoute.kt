package com.withcareer.screenpal_android.ui_v2.screen

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.withcareer.screenpal_android.ui_v2.util.rememberV2StatusBarTopPadding
import com.withcareer.screenpal_android.util.AccessibilityServiceHelper
import com.withcareer.screenpal_android.util.AutostartHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V2PermissionGuideRoute(
    onDone: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    val statusBarPadding = rememberV2StatusBarTopPadding()

    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isAccessibilityEnabled by remember { mutableStateOf(AccessibilityServiceHelper.isAccessibilityServiceEnabled(context)) }
    var hasRecordAudioPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val requestOverlayPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        canDrawOverlays = Settings.canDrawOverlays(context)
    }

    val requestAudioPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        hasRecordAudioPermission =
            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    val openOverlayPermissionSettings: () -> Unit = {
        runCatching {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            requestOverlayPermission.launch(intent)
        }.onFailure {
            runCatching {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
        Unit
    }

    val openAccessibilitySettings: () -> Unit = {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }.onFailure {
            runCatching {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
        Unit
    }

    val openAutostartSettings: () -> Unit = {
        AutostartHelper.openAutostartSettings(context)
        Unit
    }

    fun refreshStates() {
        canDrawOverlays = Settings.canDrawOverlays(context)
        isAccessibilityEnabled = AccessibilityServiceHelper.isAccessibilityServiceEnabled(context)
        hasRecordAudioPermission =
            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refreshStates()
    }

    LaunchedEffect(Unit) {
        refreshStates()
    }

    val canEnterApp = canDrawOverlays && isAccessibilityEnabled && hasRecordAudioPermission

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.padding(top = statusBarPadding),
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        text = "权限开启引导",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    TextButton(onClick = onSkip) {
                        Text("跳过")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "为了更稳定地帮你完成手机操作，请先开启以下权限。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PermissionItemCard(
                title = "悬浮窗权限",
                subtitle = if (canDrawOverlays) "已开启" else "未开启，建议开启以使用悬浮助手",
                enabled = canDrawOverlays,
                onAction = openOverlayPermissionSettings,
                actionText = if (canDrawOverlays) "已开启" else "去开启"
            )

            PermissionItemCard(
                title = "无障碍服务",
                subtitle = if (isAccessibilityEnabled) "已开启" else "未开启，执行操作需要开启无障碍服务",
                enabled = isAccessibilityEnabled,
                onAction = openAccessibilitySettings,
                actionText = if (isAccessibilityEnabled) "已开启" else "去开启"
            )

            PermissionItemCard(
                title = "录音权限",
                subtitle = if (hasRecordAudioPermission) "已授权" else "未授权，语音输入需要录音权限",
                enabled = hasRecordAudioPermission,
                onAction = { requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO) },
                actionText = if (hasRecordAudioPermission) "已授权" else "去授权"
            )

            PermissionItemCard(
                title = "自启动设置",
                subtitle = "不同机型入口不同，建议设置允许自启动/后台运行",
                enabled = false,
                onAction = openAutostartSettings,
                actionText = "去设置"
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = canEnterApp,
                onClick = onDone
            ) {
                Text("进入应用")
            }

            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onSkip,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("稍后再说")
            }
        }
    }
}

@Composable
private fun PermissionItemCard(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onAction: () -> Unit,
    actionText: String
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Button(
                    onClick = onAction,
                    enabled = !enabled
                ) {
                    Text(actionText)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
