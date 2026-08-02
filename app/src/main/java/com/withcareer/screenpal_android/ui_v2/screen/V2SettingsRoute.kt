package com.withcareer.screenpal_android.ui_v2.screen

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.withcareer.screenpal_android.R
import com.withcareer.screenpal_android.config.AiProvider
import com.withcareer.screenpal_android.config.AliyunVoice
import com.withcareer.screenpal_android.config.TtsProvider
import com.withcareer.screenpal_android.core.voice.VoiceController
import com.withcareer.screenpal_android.core.voice.VoiceprintManager
import com.withcareer.screenpal_android.overlay.FloatingAssistantService
import com.withcareer.screenpal_android.ui.icons.AppIcons
import com.withcareer.screenpal_android.ui_v2.component.V2DropdownMenuItem
import com.withcareer.screenpal_android.ui_v2.component.V2DropdownMenu
import com.withcareer.screenpal_android.ui_v2.util.rememberV2StatusBarTopPadding
import com.withcareer.screenpal_android.ui.viewmodel.SettingsViewModel
import com.withcareer.screenpal_android.util.AutostartHelper
import com.withcareer.screenpal_android.util.BackgroundPermissionChecker
import com.withcareer.screenpal_android.util.OperationGuard
import com.withcareer.screenpal_android.util.ToastUtils
import dev.jeziellago.compose.markdowntext.MarkdownText
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun V2SettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    BackHandler { onBack() }
    V2SettingsScreen(
        viewModel = viewModel,
        onBack = onBack,
        modifier = Modifier.fillMaxSize()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V2SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val statusBarPadding = rememberV2StatusBarTopPadding()
    remember { VoiceController.getInstance(context) }
    val scope = rememberCoroutineScope()
    val operationGuard = remember { OperationGuard(context) }
    val canDrawOverlays = remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    val openAccessibilitySettings: () -> Unit = {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }.onFailure {
            runCatching {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            }
        }
        Unit
    }

    val requestOverlayPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        canDrawOverlays.value = Settings.canDrawOverlays(context)
        val shouldStart = canDrawOverlays.value && uiState.isFloatingAssistantEnabled
        if (shouldStart) FloatingAssistantService.start(context)
    }

    val requestAudioPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.checkRecordAudioPermission()
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
                context.startActivity(intent)
            }
        }
        Unit
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.checkAccessibilityService()
        viewModel.checkRecordAudioPermission()
        viewModel.checkBatteryOptimization()
        canDrawOverlays.value = Settings.canDrawOverlays(context)
    }

    LaunchedEffect(Unit) {
        viewModel.checkAccessibilityService()
        viewModel.checkRecordAudioPermission()
        viewModel.checkBatteryOptimization()
        canDrawOverlays.value = Settings.canDrawOverlays(context)
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            ToastUtils.show(context, error, Toast.LENGTH_LONG)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.infoMessage) {
        uiState.infoMessage?.let { message ->
            ToastUtils.show(context, message, Toast.LENGTH_SHORT)
            viewModel.clearInfoMessage()
        }
    }

    var showWakeWordConfirmDialog by remember { mutableStateOf(false) }
    val pendingWakeWord by remember { mutableStateOf("") }

    if (showWakeWordConfirmDialog) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            onDismissRequest = { showWakeWordConfirmDialog = false },
            title = { Text(stringResource(R.string.wakeword_change_confirm_title)) },
            text = { Text(stringResource(R.string.wakeword_change_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateWakeWord(pendingWakeWord)
                        showWakeWordConfirmDialog = false
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showWakeWordConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    modifier = Modifier.padding(top = statusBarPadding),
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    title = {
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = stringResource(R.string.back),
                                modifier = Modifier.size(32.dp)
                            )
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
                    .imePadding()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingsCard(title = stringResource(R.string.permission_management)) {
                    PermissionItem(
                        title = stringResource(R.string.display_over_other_apps),
                        subtitle = stringResource(R.string.display_overlay_subtitle),
                        isGranted = canDrawOverlays.value,
                        onClick = openOverlayPermissionSettings
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        thickness = 0.5.dp
                    )

                    PermissionItem(
                        title = stringResource(R.string.accessibility_service),
                        subtitle = stringResource(R.string.accessibility_subtitle),
                        isGranted = uiState.isAccessibilityEnabled,
                        onClick = openAccessibilitySettings
                    )

                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.autostart_permission),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.autostart_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        TextButton(
                            onClick = { AutostartHelper.openAutostartSettings(context) }
                        ) {
                            Text(
                                text = stringResource(R.string.go_settings),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    PermissionItem(
                        title = stringResource(R.string.microphone_permission),
                        subtitle = stringResource(R.string.microphone_subtitle),
                        isGranted = uiState.isRecordAudioGranted,
                        onClick = { requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO) }
                    )

                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    PermissionItem(
                        title = "忽略电池优化",
                        subtitle = "建议加入白名单以提升后台稳定性",
                        isGranted = uiState.isIgnoringBatteryOptimizations,
                        onClick = { BackgroundPermissionChecker.requestIgnoreBatteryOptimizations(context) }
                    )
                }

                SettingsCard(title = stringResource(R.string.function_settings)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.language),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        LanguageSelector(
                            currentLanguage = uiState.language,
                            onLanguageSelected = { viewModel.saveLanguage(it) }
                        )
                    }
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )

                    SwitchItem(
                        title = stringResource(R.string.voice_broadcast),
                        subtitle = stringResource(R.string.voice_broadcast_subtitle),
                        checked = uiState.isTtsEnabled,
                        onCheckedChange = { viewModel.updateTtsEnabled(it) }
                    )

                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )

                    // 检查 API Key 是否可用
                    val hasApiKey = uiState.apiKey.isNotBlank()
                    val isWakeupReady = hasApiKey && canDrawOverlays.value
                    val wakeupSubtitle = if (isWakeupReady) {
                        stringResource(R.string.voice_wakeup_subtitle, uiState.wakeWord)
                    } else {
                        if (!hasApiKey) stringResource(R.string.api_key_not_set)
                        else stringResource(R.string.floating_permission_request)
                    }

                    SwitchItem(
                        title = stringResource(R.string.voice_wakeup),
                        subtitle = wakeupSubtitle,
                        checked = uiState.isWakeupEnabled && isWakeupReady,
                        onCheckedChange = { checked ->
                            if (checked) {
                                scope.launch {
                                    if (operationGuard.check(
                                        requireApiKey = true,
                                        passedApiKey = uiState.apiKey
                                    )) {
                                        viewModel.updateWakeupEnabled(true)
                                    }
                                }
                            } else {
                                viewModel.updateWakeupEnabled(false)
                            }
                        }
                    )

                    if (uiState.isWakeupEnabled && isWakeupReady) {
                        InfoItem(
                            label = stringResource(R.string.wake_word),
                            value = "阿宝同学"
                        )
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        // 音色识别功能
                        SwitchItem(
                            title = stringResource(R.string.voiceprint_verification),
                            subtitle = stringResource(R.string.voiceprint_verification_subtitle),
                            checked = uiState.voiceprintEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.saveVoiceprintEnabled(enabled)
                            }
                        )

                        if (uiState.voiceprintEnabled) {
                            var showVoiceprintDialog by remember { mutableStateOf(false) }

                            VoiceprintSettingItem(
                                isEnrolled = uiState.isVoiceprintEnrolled,
                                onEnroll = { showVoiceprintDialog = true },
                                onDelete = { viewModel.deleteVoiceprint() }
                            )

                            if (showVoiceprintDialog) {
                                VoiceprintEnrollmentDialog(
                                    wakeWord = uiState.wakeWord,
                                    voiceprintManager = viewModel.getVoiceprintManager(),
                                    onDismiss = { showVoiceprintDialog = false },
                                    onComplete = {
                                        showVoiceprintDialog = false
                                        viewModel.onVoiceprintEnrolled()
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )

                    SwitchItem(
                        title = stringResource(R.string.floating_assistant),
                        subtitle = stringResource(R.string.floating_assistant_subtitle),
                        checked = uiState.isFloatingAssistantEnabled && canDrawOverlays.value,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                val allowed = Settings.canDrawOverlays(context)
                                canDrawOverlays.value = allowed
                                if (!allowed) {
                                    ToastUtils.show(context, context.getString(R.string.floating_permission_request), Toast.LENGTH_SHORT)
                                } else {
                                    viewModel.updateFloatingAssistantEnabled(true)
                                    FloatingAssistantService.start(context)
                                }
                            } else {
                                viewModel.updateFloatingAssistantEnabled(false)
                                FloatingAssistantService.stop(context)
                            }
                        }
                    )

                    if (uiState.isFloatingAssistantEnabled && canDrawOverlays.value) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                text = stringResource(R.string.floating_ball_size),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Slider(
                                value = uiState.floatingBallSize.toFloat(),
                                onValueChange = { viewModel.updateFloatingBallSize(it.toInt()) },
                                valueRange = 0f..3f,
                                steps = 2
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.size_small), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.size_medium), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.size_large), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.size_extra_large), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )

                    // UI 树优先：作为独立开关，开启后仅注入剪枝 UI 树，不附带截图，节省 Token
                    SwitchItem(
                        title = "启动UI树优先",
                        subtitle = "开启后仅注入剪枝UI树，不附带截图，节省 Token",
                        checked = uiState.isUiTreeFirstEnabled,
                        onCheckedChange = { viewModel.updateUiTreeFirstEnabled(it) }
                    )
                }

                SettingsCard(title = stringResource(R.string.model_config)) {
                    val profiles = uiState.modelProfiles
                    val activeId = uiState.activeProfileId
                    val activeProfile = profiles.find { it.id == activeId }
                    val isDefaultProfile = activeProfile == null
                    val providers = listOf(
                        AiProvider.DASHSCOPE,
                        AiProvider.VOLCENGINE,
                        AiProvider.SILICONFLOW,
                        AiProvider.CUSTOM
                    )

                    // 当前编辑值（随激活配置变化重置）
                    var selectedProvider by remember(activeId, profiles.size) {
                        mutableStateOf(activeProfile?.provider ?: uiState.aiProvider)
                    }
                    var apiKeyInput by remember(activeId, profiles.size) {
                        mutableStateOf(activeProfile?.apiKey ?: uiState.apiKey)
                    }
                    var baseUrlInput by remember(activeId, profiles.size) {
                        mutableStateOf(activeProfile?.baseUrl ?: uiState.baseUrl)
                    }
                    var modelNameInput by remember(activeId, profiles.size) {
                        mutableStateOf(activeProfile?.modelName ?: uiState.modelName)
                    }
                    var showNewProfileDialog by remember { mutableStateOf(false) }

                    // API Key 掩码/编辑状态：已保存且未进入编辑时显示掩码
                    var apiKeyEditing by remember(activeId, profiles.size) { mutableStateOf(false) }
                    val apiKeyFocusRequester = remember { FocusRequester() }
                    val apiKeyMasked = !apiKeyEditing && apiKeyInput.isNotBlank()

                    // 进入编辑时滚动到输入框、请求焦点并弹出键盘，避免被输入法遮挡
                    LaunchedEffect(apiKeyEditing) {
                        if (apiKeyEditing) {
                            runCatching { scrollState.animateScrollTo(scrollState.maxValue) }
                            runCatching { apiKeyFocusRequester.requestFocus() }
                            keyboardController?.show()
                        }
                    }

                    // 默认配置下跟随本地字段变化
                    LaunchedEffect(uiState.apiKey, uiState.aiProvider, uiState.baseUrl, uiState.modelName) {
                        if (activeProfile == null) {
                            selectedProvider = uiState.aiProvider
                            apiKeyInput = uiState.apiKey
                            baseUrlInput = uiState.baseUrl
                            modelNameInput = uiState.modelName
                        }
                    }

                    Text(
                        text = stringResource(R.string.multimodal_model_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // 配置切换行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isDefaultProfile) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .clickable { viewModel.switchModelProfile(null) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.profile_default),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isDefaultProfile) FontWeight.Bold else FontWeight.Normal,
                                color = if (isDefaultProfile) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        profiles.forEach { profile ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (profile.id == activeId) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .clickable { viewModel.switchModelProfile(profile.id) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (profile.id == activeId) FontWeight.Bold else FontWeight.Normal,
                                    color = if (profile.id == activeId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { showNewProfileDialog = true }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 服务商选择
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        providers.forEach { provider ->
                            val isSelected = selectedProvider == provider.value
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .clickable { selectedProvider = provider.value }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(provider.labelResId),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 输入区："其他"平台需依次填写 Base URL、API Key、模型名称；默认平台仅填写 API Key
                    if (selectedProvider == AiProvider.CUSTOM.value) {
                        OutlinedTextField(
                            value = baseUrlInput,
                            onValueChange = { baseUrlInput = it },
                            label = { Text(stringResource(R.string.base_url_label)) },
                            placeholder = { Text("https://api.example.com/v1") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // API Key：已保存显示掩码，点击进入明文编辑
                        ApiKeyField(
                            value = apiKeyInput,
                            masked = apiKeyMasked,
                            onValueChange = { apiKeyInput = it },
                            onEnterEdit = { apiKeyEditing = true },
                            focusRequester = apiKeyFocusRequester,
                            onDone = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 模型名称：下拉选择 + 手动输入
                        ModelNameField(
                            value = modelNameInput,
                            onValueChange = { modelNameInput = it },
                            options = uiState.modelOptions,
                            isLoading = uiState.isLoadingModels,
                            fetchError = uiState.modelFetchError,
                            onRefresh = {
                                val effBaseUrl = baseUrlInput.ifBlank { com.withcareer.screenpal_android.core.llm.ModelConfigResolver.defaultBaseUrl(selectedProvider) }
                                viewModel.fetchModelOptions(selectedProvider, apiKeyInput, effBaseUrl)
                            },
                            scrollState = scrollState
                        )
                    } else {
                        // API Key：已保存显示掩码，点击进入明文编辑
                        ApiKeyField(
                            value = apiKeyInput,
                            masked = apiKeyMasked,
                            onValueChange = { apiKeyInput = it },
                            onEnterEdit = { apiKeyEditing = true },
                            focusRequester = apiKeyFocusRequester,
                            onDone = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 模型名称：下拉选择 + 手动输入
                        ModelNameField(
                            value = modelNameInput,
                            onValueChange = { modelNameInput = it },
                            options = uiState.modelOptions,
                            isLoading = uiState.isLoadingModels,
                            fetchError = uiState.modelFetchError,
                            onRefresh = {
                                val effBaseUrl = baseUrlInput.ifBlank { com.withcareer.screenpal_android.core.llm.ModelConfigResolver.defaultBaseUrl(selectedProvider) }
                                viewModel.fetchModelOptions(selectedProvider, apiKeyInput, effBaseUrl)
                            },
                            scrollState = scrollState
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            apiKeyEditing = false
                            viewModel.saveActiveModelProfile(selectedProvider, apiKeyInput, baseUrlInput, modelNameInput)
                            ToastUtils.show(context, context.getString(R.string.saved), Toast.LENGTH_SHORT)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.save_api_key))
                    }

                    if (!isDefaultProfile) {
                        TextButton(
                            onClick = { viewModel.deleteModelProfile(activeId!!) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(stringResource(R.string.delete_profile))
                        }
                    }

                    // 新建配置对话框
                    if (showNewProfileDialog) {
                        var newProfileName by remember { mutableStateOf("") }
                        AlertDialog(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                            shape = RoundedCornerShape(16.dp),
                            onDismissRequest = { showNewProfileDialog = false },
                            title = { Text(stringResource(R.string.new_profile)) },
                            text = {
                                OutlinedTextField(
                                    value = newProfileName,
                                    onValueChange = { newProfileName = it },
                                    label = { Text(stringResource(R.string.profile_name_hint)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        if (newProfileName.isNotBlank()) {
                                            viewModel.createModelProfile(newProfileName)
                                            showNewProfileDialog = false
                                        }
                                    },
                                    enabled = newProfileName.isNotBlank()
                                ) {
                                    Text(stringResource(R.string.create))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showNewProfileDialog = false }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        )
                    }
                }
            }
        }

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

/**
 * 将 API Key 掩码为 "••••••••1234" 形式（保留末 4 位）
 */
private fun maskApiKey(key: String): String {
    if (key.length <= 4) return "••••"
    return "••••••••" + key.takeLast(4)
}

/**
 * API Key 输入框：已保存时显示掩码，获得焦点即进入明文编辑；Done 收起键盘
 */
@Composable
private fun ApiKeyField(
    value: String,
    masked: Boolean,
    onValueChange: (String) -> Unit,
    onEnterEdit: () -> Unit,
    focusRequester: FocusRequester,
    onDone: () -> Unit
) {
    // 单个 TextField：掩码态 readOnly 展示掩码值，获焦即进入明文编辑态
    OutlinedTextField(
        value = if (masked) maskApiKey(value) else value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.enter_api_key)) },
        placeholder = { if (masked) Text(stringResource(R.string.api_key_masked_hint)) else null },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                // 获得焦点时进入编辑（明文）；失焦交由调用方在 Done/保存时处理
                if (focusState.isFocused && masked) {
                    onEnterEdit()
                }
            },
        singleLine = true,
        readOnly = masked,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() })
    )
}

/**
 * 模型名称：下拉选择 + 手动输入，带刷新按钮与加载/失败状态
 */
@Composable
private fun ModelNameField(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    isLoading: Boolean,
    fetchError: String?,
    onRefresh: () -> Unit,
    scrollState: androidx.compose.foundation.ScrollState
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text(stringResource(R.string.model_select_or_input)) },
                    placeholder = { Text("qwen-vl-max") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = if (options.isNotEmpty()) {
                        {
                            IconButton(onClick = { expanded = !expanded }) {
                                Icon(
                                    imageVector = if (expanded) AppIcons.KeyboardArrowDown else AppIcons.KeyboardArrowRight,
                                    contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else null
                )
                if (expanded && options.isNotEmpty()) {
                    V2DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.heightIn(max = 260.dp)
                    ) {
                        options.forEach { option ->
                            V2DropdownMenuItem(
                                text = option,
                                onClick = {
                                    onValueChange(option)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            // 刷新按钮
            IconButton(onClick = onRefresh, enabled = !isLoading) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.fetch_models),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (fetchError != null) {
            Text(
                text = fetchError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        } else if (isLoading) {
            Text(
                text = stringResource(R.string.model_list_loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }
    }
}

@Composable
fun SettingsCard(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val containerColor = if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    } else {
        Color(0xFFEEF6FF)
    }
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = containerColor
        ),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                action?.invoke()
            }
            content()
        }
    }
}

@Composable
fun InfoItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    canCopy: Boolean = false
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (canCopy) {
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText(label, value)
                        clipboard.setPrimaryClip(clip)
                        ToastUtils.show(context, context.getString(R.string.copied))
                    },
                    modifier = Modifier.size(24.dp).padding(start = 8.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.ContentCopy,
                        contentDescription = stringResource(R.string.copy),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun LinkItem(
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Icon(
            imageVector = AppIcons.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PermissionItem(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TextButton(
            onClick = onClick,
            enabled = !isGranted
        ) {
            Text(
                text = if (isGranted) stringResource(R.string.granted) else stringResource(R.string.go_grant),
                color = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun CollapsiblePermissionItem(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onClick: () -> Unit,
    hasSubItems: Boolean = false,
    subItemContent: @Composable (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (hasSubItems && subItemContent != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (expanded) AppIcons.KeyboardArrowDown else AppIcons.KeyboardArrowRight,
                            contentDescription = if (expanded) "收起" else "展开",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { expanded = !expanded }
                        )
                    }
                }

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TextButton(
                onClick = onClick,
                enabled = !isGranted
            ) {
                Text(
                    text = if (isGranted) stringResource(R.string.granted) else stringResource(R.string.go_grant),
                    color = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }

        if (hasSubItems && subItemContent != null) {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Column(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                    ) {
                        subItemContent()
                    }
                }
            }
        }
    }
}

@Composable
fun SubPermissionQuickEntry(
    title: String,
    subtitle: String,
    hint: String? = null,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        if (hint != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = AppIcons.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TextButton(onClick = onClick) {
                Text(
                    text = stringResource(R.string.go_settings),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun SwitchItem(
    title: String,
    subtitle: String = "",
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
            modifier = Modifier.scale(0.8f)
        )
    }
}

@Composable
fun LanguageSelector(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit
) {
    val languages = listOf(
        "zh" to stringResource(R.string.language_zh),
        "en" to stringResource(R.string.language_en)
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        languages.forEach { (code, label) ->
            val isSelected = currentLanguage == code
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent
                    )
                    .clickable { onLanguageSelected(code) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = if (isSelected) MaterialTheme.typography.bodyLarge.fontSize else MaterialTheme.typography.bodyMedium.fontSize,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownItem(
    label: String,
    currentValue: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            Surface(
                modifier = Modifier
                    .menuAnchor()
                    .width(120.dp)
                    .heightIn(min = 36.dp),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = Color.Transparent,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = currentValue,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (expanded) AppIcons.KeyboardArrowDown else AppIcons.KeyboardArrowRight,
                        contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            V2DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 200.dp),
                minSize = DpSize(120.dp, 0.dp)
            ) {
                options.forEach { option ->
                    V2DropdownMenuItem(
                        text = option,
                        onClick = {
                            onSelected(option)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}

@Composable
fun VoiceprintSettingItem(
    isEnrolled: Boolean,
    onEnroll: () -> Unit,
    onDelete: () -> Unit
) {
    LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.voiceprint_registration),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isEnrolled) {
                    stringResource(R.string.voiceprint_enrolled_subtitle)
                } else {
                    stringResource(R.string.voiceprint_enroll_hint)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isEnrolled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isEnrolled) {
            TextButton(onClick = { showDeleteConfirm = true }) {
                Text(
                    text = stringResource(R.string.voiceprint_delete_button),
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else {
            Button(onClick = onEnroll) {
                Text(stringResource(R.string.voiceprint_register_button))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.voiceprint_delete)) },
            text = { Text(stringResource(R.string.voiceprint_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun VoiceSelectionDialog(
    currentVoice: String,
    onVoiceSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.voice_package_select),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(AliyunVoice.entries) { voice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onVoiceSelected(voice.value)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        RadioButton(
                            selected = voice.value == currentVoice,
                            onClick = {
                                onVoiceSelected(voice.value)
                                onDismiss()
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = voice.getLabel(),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun VoiceprintEnrollmentDialog(
    wakeWord: String,
    voiceprintManager: VoiceprintManager,
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentStep by remember { mutableIntStateOf(0) }
    var isRecording by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val recordedFeatures = remember { mutableListOf<FloatArray>() }

    var currentAmplitude by remember { mutableFloatStateOf(0f) }

    val isModelReady = voiceprintManager.isModelInitialized()

    if (!isModelReady) {
        LaunchedEffect(Unit) {
            ToastUtils.show(context, context.getString(R.string.voiceprint_model_not_ready), Toast.LENGTH_LONG)
            delay(500)
            onDismiss()
        }
    }

    fun startRecording() {
        if (!isModelReady) {
            ToastUtils.show(context, context.getString(R.string.voiceprint_model_not_ready), Toast.LENGTH_SHORT)
            return
        }

        isRecording = true
        errorMessage = null
        currentAmplitude = 0f

        scope.launch {
            try {
                val result = voiceprintManager.startEnrollmentRecording(currentStep) { amplitude ->
                    currentAmplitude = amplitude
                }

                isRecording = false
                currentAmplitude = 0f

                if (result.isSuccess) {
                    val features = result.getOrNull()
                    if (features != null) {
                        recordedFeatures.add(features)
                        currentStep++

                        if (currentStep >= 3) {
                            isProcessing = true

                            val completeResult = voiceprintManager.completeEnrollment(recordedFeatures, wakeWord)

                            isProcessing = false

                            if (completeResult.isSuccess) {
                                ToastUtils.show(context, context.getString(R.string.voiceprint_success), Toast.LENGTH_SHORT)
                                delay(500)
                                onComplete()
                            } else {
                                errorMessage = completeResult.exceptionOrNull()?.message
                                    ?: context.getString(R.string.voiceprint_failed)
                            }
                        }
                    } else {
                        errorMessage = context.getString(R.string.voiceprint_failed)
                    }
                } else {
                    errorMessage = result.exceptionOrNull()?.message
                        ?: context.getString(R.string.voiceprint_failed)
                }
            } catch (e: Exception) {
                isRecording = false
                errorMessage = e.message ?: context.getString(R.string.voiceprint_failed)
            }
        }
    }

    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(16.dp),
        onDismissRequest = {
            if (!isRecording && !isProcessing) {
                onDismiss()
            }
        },
        title = {
            Text(
                text = stringResource(R.string.voiceprint_enroll_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.voiceprint_enroll_instruction),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.voiceprint_wake_word_label, wakeWord),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { index ->
                        ProgressCircle(
                            isActive = index == currentStep && isRecording,
                            isCompleted = index < currentStep,
                            index = index + 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                when {
                    isProcessing -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.height(120.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.voiceprint_processing),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    isRecording -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.height(120.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            WaveformAnimation(amplitude = currentAmplitude)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.voiceprint_recording, currentStep + 1),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    else -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.height(120.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = stringResource(R.string.voiceprint_recording_step, currentStep + 1),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.voiceprint_say_wake_word),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { startRecording() },
                enabled = !isRecording && !isProcessing && isModelReady
            ) {
                Text(
                    text = if (currentStep < 3) {
                        stringResource(R.string.voiceprint_start_recording)
                    } else {
                        stringResource(R.string.finish)
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isRecording && !isProcessing
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ProgressCircle(
    isActive: Boolean,
    isCompleted: Boolean,
    index: Int
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
                when {
                    isCompleted -> MaterialTheme.colorScheme.primary
                    isActive -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
    ) {
        Text(
            text = "$index",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = when {
                isCompleted -> MaterialTheme.colorScheme.onPrimary
                isActive -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun WaveformAnimation(amplitude: Float = 0f) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")

    val animatedPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(
        modifier = Modifier
            .width(240.dp)
            .height(80.dp)
    ) {
        val centerY = size.height / 2
        val barCount = 24
        val barWidth = size.width / barCount

        for (i in 0 until barCount) {
            val phase = (animatedPhase + i * 15) % 360
            val waveAmplitude = abs(sin(Math.toRadians(phase.toDouble()))).toFloat()

            val minHeight = 8f
            val maxHeight = 60f
            val baseHeight = minHeight + (maxHeight - minHeight) * amplitude * 0.5f
            val waveHeight = waveAmplitude * amplitude * (maxHeight - minHeight) * 0.5f
            val barHeight = baseHeight + waveHeight

            val color = when {
                amplitude > 0.6f -> Color(0xFF4CAF50)
                amplitude > 0.3f -> Color(0xFFFFC107)
                amplitude > 0.1f -> Color(0xFFFF9800)
                else -> Color(0xFFBDBDBD)
            }

            drawLine(
                color = color,
                start = Offset(i * barWidth + barWidth / 2, centerY - barHeight / 2),
                end = Offset(i * barWidth + barWidth / 2, centerY + barHeight / 2),
                strokeWidth = barWidth * 0.5f
            )
        }
    }
}
