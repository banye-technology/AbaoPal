package com.withcareer.screenpal_android.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.viewmodel.compose.viewModel
import com.withcareer.screenpal_android.recording.RecordingResult
import com.withcareer.screenpal_android.recording.RecordingEditState
import com.withcareer.screenpal_android.recording.RecordedAction
import com.withcareer.screenpal_android.ui.viewmodel.RecordingEditViewModel
import com.withcareer.screenpal_android.ui.component.RecordingTimeline
import com.withcareer.screenpal_android.ui.component.EnhancedActionCard
import com.withcareer.screenpal_android.ui.component.HelpTooltip
import com.withcareer.screenpal_android.util.ToastUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.size
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalView
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalConfiguration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import com.withcareer.screenpal_android.recording.EditableAction
import android.util.Log
import com.google.gson.Gson
import androidx.compose.ui.viewinterop.AndroidView
import com.withcareer.screenpal_android.recording.FeedbackIconView
import com.withcareer.screenpal_android.util.AccessibilityServiceHelper
import android.provider.Settings
import android.net.Uri
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import java.io.File
import java.io.FileOutputStream

import com.withcareer.screenpal_android.recording.RecordingPlaybackService
import com.withcareer.screenpal_android.R
import com.withcareer.screenpal_android.recording.RecordingService
import com.withcareer.screenpal_android.ui_v2.screen.recordingResultToSteps
import com.withcareer.screenpal_android.data.model.SkillInstructionSet
import com.withcareer.screenpal_android.ui_v2.util.rememberV2StatusBarTopPadding
import java.util.UUID
import androidx.compose.ui.res.stringResource
import com.withcareer.screenpal_android.util.OperationGuard
import com.withcareer.screenpal_android.util.OperationCheckFailure

/**
 * 录制编辑界面（增强版）
 * 展示录制的操作时间线，允许用户编辑延迟时间、删除步骤、批量操作等
 *
 * @param recordingResult 录制结果（包含操作列表和屏幕尺寸）
 * @param onSave 保存回调
 * @param onBack 返回回调
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Suppress("DEPRECATION")
@Composable
fun RecordingEditScreen(
    recordingResult: RecordingResult,
    description: String? = null,
    onDescriptionChange: ((String) -> Unit)? = null,
    descriptionError: Boolean = false,
    onSave: (String, RecordingResult) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val statusBarPadding = rememberV2StatusBarTopPadding()
    val viewModel: RecordingEditViewModel = viewModel(
        key = "recording-${recordingResult.id ?: "new"}-${recordingResult.actions.size}-${recordingResult.actions.lastOrNull()?.timestamp ?: 0L}-${recordingResult.screenWidth}x${recordingResult.screenHeight}",
        factory = RecordingEditViewModel.Factory(recordingResult)
    )
    val uiState by viewModel.uiState.collectAsState()

    // 用于控制操作点列表的滚动
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val operationGuard = remember(context) { OperationGuard(context) }

    // 数据未加载完成时显示骨架屏，避免主线程在初始化时阻塞导致的白屏
    if (uiState == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    modifier = Modifier.padding(top = statusBarPadding),
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    title = {
                        Text(
                            text = stringResource(R.string.recording_edit_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                stringResource(R.string.back)
                            )
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        return
    }

    val state = uiState!!
    Log.d("RecordingEditScreen", "UI state loaded: actionCount=${state.actionCount}")

    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showBatchDelayDialog by remember { mutableStateOf(false) }
    var showBatchIntervalDialog by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var isLiveEditMode by rememberSaveable { mutableStateOf(false) }
    var openLiveEditAfterPick by remember { mutableStateOf(false) }
    var mainTimelineZoomScale by rememberSaveable { mutableStateOf(1f) }
    var liveTimelineZoomScale by rememberSaveable { mutableStateOf(1f) }
    var liveEditRotation by rememberSaveable { mutableIntStateOf(0) }
    var intervalScaleFactor by rememberSaveable { mutableFloatStateOf(2f) }
    var selectionSnapshotMain by remember { mutableStateOf<RecordingEditViewModel.SelectionSnapshot?>(null) }
    var selectionSnapshotLive by remember { mutableStateOf<RecordingEditViewModel.SelectionSnapshot?>(null) }
    var selectionInitialized by remember { mutableStateOf(false) }
    val hasScreenshot = !state.screenshotPath.isNullOrBlank()
    var pendingInsertRequest by remember { mutableStateOf<InsertRequest?>(null) }
    var showInsertConfirm by remember { mutableStateOf(false) }
    var skipInsertConfirm by remember { mutableStateOf(false) }
    var liveEditDisplayRectInWindow by remember { mutableStateOf<Rect?>(null) }
    var liveEditWindowOffset by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    val rootView = LocalView.current
    LaunchedEffect(hasScreenshot) {
        if (!hasScreenshot) {
            isLiveEditMode = false
        }
    }
    LaunchedEffect(isLiveEditMode) {
        if (!selectionInitialized) {
            selectionInitialized = true
            return@LaunchedEffect
        }
        if (isLiveEditMode) {
            viewModel.enterLiveEdit()
            skipInsertConfirm = false
            selectionSnapshotMain = viewModel.captureSelectionSnapshot()
            viewModel.clearSelectionAndFocus()
            selectionSnapshotLive?.let { viewModel.restoreSelectionSnapshot(it) }
        } else {
            selectionSnapshotLive = viewModel.captureSelectionSnapshot()
            selectionSnapshotMain?.let { viewModel.restoreSelectionSnapshot(it) }
            skipInsertConfirm = false
        }
    }
    LaunchedEffect(rootView) {
        val location = IntArray(2)
        rootView.getLocationOnScreen(location)
        liveEditWindowOffset = location[0].toFloat() to location[1].toFloat()
    }

    val screenshotPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val cachedPath = cacheScreenshotFromUri(context, uri)
            if (cachedPath != null) {
                viewModel.updateScreenshotPath(cachedPath)
                if (openLiveEditAfterPick) {
                    isLiveEditMode = true
                }
            } else {
                ToastUtils.show(context, context.getString(R.string.screenshot_import_failed), Toast.LENGTH_SHORT)
            }
        }
        openLiveEditAfterPick = false
    }
    val pendingInsertState by rememberUpdatedState(pendingInsertRequest)
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != RecordingService.ACTION_RECORDING_INSERT_COMPLETED) {
                    return
                }
                val result = RecordingService.lastRecordingResult
                val request = pendingInsertState
                if (result == null || request == null) {
                    return
                }
                if (result.actions.isEmpty()) {
                    ToastUtils.show(context, context.getString(R.string.recording_insert_empty), Toast.LENGTH_SHORT)
                } else {
                    if (result.actions.size > 1) {
                        ToastUtils.show(context, context.getString(R.string.recording_insert_only_first), Toast.LENGTH_SHORT)
                    }
                    val normalizedAction = result.actions.first().normalizeActionType()
                    val action = if (isLiveEditMode) {
                        mapRecordedActionToScreen(
                            action = normalizedAction,
                            displayRect = liveEditDisplayRectInWindow,
                            screenWidth = state.screenWidth,
                            screenHeight = state.screenHeight,
                            rotationDegrees = liveEditRotation,
                            windowOffset = liveEditWindowOffset
                        )
                    } else {
                        normalizedAction
                    }
                    val insertedIndex = viewModel.insertRecordedAction(
                        anchorActionId = request.actionId,
                        insertBefore = request.insertBefore,
                        recordedAction = action
                    )
                    if (insertedIndex >= 0) {
                        coroutineScope.launch {
                            listState.animateScrollToItem(
                                index = insertedIndex,
                                scrollOffset = 0
                            )
                        }
                    }
                }
                RecordingService.lastRecordingResult = null
                pendingInsertRequest = null
            }
        }
        val filter = IntentFilter(
            RecordingService.ACTION_RECORDING_INSERT_COMPLETED
        )
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
    fun startInsertRecording() {
        coroutineScope.launch {
            val result = operationGuard.checkWithResult(
                requireApiKey = false,
                requireAccessibility = true,
                requireOverlay = true
            )
            if (!result.passed) {
                when (result.failure) {
                    OperationCheckFailure.ACCESSIBILITY_NOT_READY -> {
                        AccessibilityServiceHelper.goToAccessibilitySettings(context)
                    }
                    OperationCheckFailure.OVERLAY_DENIED -> {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                    else -> {}
                }
                return@launch
            }
            viewModel.clearSelectionAndFocus()
            RecordingService.startInsert(context)
            ToastUtils.show(context, context.getString(R.string.recording_insert_started), Toast.LENGTH_LONG)
        }
    }

    if (showInsertConfirm && pendingInsertRequest != null) {
        AlertDialog(
            shape = RoundedCornerShape(16.dp),
            onDismissRequest = { showInsertConfirm = false },
            title = { Text(stringResource(R.string.insert_operation_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.insert_recording_confirm_message))
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = skipInsertConfirm,
                            onCheckedChange = { checked -> skipInsertConfirm = checked }
                        )
                        Text(stringResource(R.string.do_not_remind_realtime_edit))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showInsertConfirm = false
                    pendingInsertRequest ?: return@TextButton
                    startInsertRecording()
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showInsertConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    fun exitLiveEditMode() {
        isLiveEditMode = false
        viewModel.exitLiveEdit()
    }

    if (isLiveEditMode) {
        BackHandler(enabled = true) {
            exitLiveEditMode()
        }
        LiveEditScreen(
            state = state,
            viewModel = viewModel,
            listState = listState,
            timelineZoomScale = liveTimelineZoomScale,
            onTimelineZoomScaleChange = { liveTimelineZoomScale = it },
            onBack = { exitLiveEditMode() },
            rotationDegrees = liveEditRotation,
            onRotate = { liveEditRotation = (liveEditRotation + 90) % 360 },
            onPickScreenshot = {
                openLiveEditAfterPick = true
                screenshotPicker.launch("image/*")
            },
            onInsertRequested = { actionId, insertBefore ->
                pendingInsertRequest = InsertRequest(actionId = actionId, insertBefore = insertBefore)
                if (skipInsertConfirm) {
                    startInsertRecording()
                } else {
                    showInsertConfirm = true
                }
            },
            intervalScaleFactor = intervalScaleFactor,
            onIntervalScaleFactorChange = { intervalScaleFactor = normalizeScaleFactor(it) },
            onDisplayRectInWindow = { rect ->
                liveEditDisplayRectInWindow = rect
            }
        )
        return
    }

    // 返回处理（未保存提示）
    fun handleBack() {
        if (state.isModified) {
            showUnsavedDialog = true
        } else {
            viewModel.discardLiveEditDraft()
            onBack()
        }
    }

    // 保存处理
    fun handleSave() {
        val validation = viewModel.validate()

        if (!validation.isValid) {
            ToastUtils.show(context, validation.errors.first(), Toast.LENGTH_SHORT)
            return
        }

        if (validation.warnings.isNotEmpty()) {
            ToastUtils.show(
                context,
                context.getString(R.string.validation_warning_format, validation.warnings.first()),
                Toast.LENGTH_LONG
            )
        }

        viewModel.commitLiveEditDraft()
        val result = viewModel.getFinalResult()
        onSave(state.title, result)
    }

    // 未保存确认对话框
    if (showUnsavedDialog) {
        AlertDialog(
            shape = RoundedCornerShape(16.dp),
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text(stringResource(R.string.unsaved_changes_title)) },
            text = { Text(stringResource(R.string.unsaved_changes_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.discardAllChanges()
                    onBack()
                }) {
                    Text(stringResource(R.string.discard), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 批量删除确认对话框
    if (showBatchDeleteDialog) {
        AlertDialog(
            shape = RoundedCornerShape(16.dp),
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text(stringResource(R.string.batch_delete_title)) },
            text = { Text(stringResource(R.string.batch_delete_message, state.selectedCount)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.batchDelete()
                        showBatchDeleteDialog = false
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 批量调整延迟对话框
    if (showBatchDelayDialog) {
        BatchDelayDialog(
            selectedCount = state.selectedCount,
            onDismiss = { showBatchDelayDialog = false },
            onConfirm = { uniformDelay, scaleRatio ->
                if (uniformDelay != null) {
                    viewModel.batchUpdateDelay(uniformDelay)
                } else if (scaleRatio != null) {
                    viewModel.batchScaleDelay(scaleRatio)
                }
                showBatchDelayDialog = false
            }
        )
    }

    // 批量设置间隔对话框
    if (showBatchIntervalDialog) {
        BatchIntervalDialog(
            selectedCount = state.selectedCount,
            totalDuration = state.totalDuration,
            intervalScaleFactor = intervalScaleFactor,
            onIntervalScaleFactorChange = { intervalScaleFactor = normalizeScaleFactor(it) },
            onDismiss = { showBatchIntervalDialog = false },
            onConfirm = { interval ->
                viewModel.batchSetInterval(interval)
                showBatchIntervalDialog = false
            },
            onScaleApply = {
                viewModel.batchScaleInterval(intervalScaleFactor)
            }
        )
    }

    BackHandler(enabled = true) {
        handleBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.padding(top = statusBarPadding),
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.recording_edit_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (state.isModified) {
                            Text(
                                stringResource(R.string.unsaved_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val result = viewModel.getFinalResult()
                            RecordingPlaybackService.startWithSkill(
                                context = context,
                                skillInstructionSet = SkillInstructionSet(
                                    id = result.id ?: UUID.randomUUID().toString(),
                                    name = state.title,
                                    description = description ?: "",
                                    steps = recordingResultToSteps(result),
                                    createdAt = System.currentTimeMillis()
                                ),
                                recordedWidth = result.screenWidth,
                                recordedHeight = result.screenHeight
                            )
                        }
                    ) {
                        Icon(Icons.Default.PlayCircleOutline, contentDescription = stringResource(R.string.playback))
                    }
                    IconButton(
                        onClick = {
                            if (hasScreenshot) {
                                isLiveEditMode = true
                            } else {
                                ToastUtils.show(
                                    context,
                                    context.getString(R.string.enter_live_edit_upload_first),
                                    Toast.LENGTH_SHORT
                                )
                                openLiveEditAfterPick = true
                                screenshotPicker.launch("image/*")
                            }
                        }
                    ) {
                        Icon(Icons.Default.Image, contentDescription = stringResource(R.string.live_edit_title))
                    }
                    TextButton(
                        onClick = { handleSave() },
                        enabled = state.title.isNotBlank() && state.actions.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.save))
                    }
                }
            )
        }
        ) { padding ->
        val dynamicMinDuration = remember(state.actions, state.totalDuration) {
            if (state.actions.isNotEmpty()) {
                val maxActionTime = state.actions.maxOfOrNull {
                    (it.action.progress * state.totalDuration).toLong()
                } ?: 0L
                maxOf(1000L, maxActionTime)
            } else {
                0L
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                RecordingInfoCard(
                    title = state.title,
                    onTitleChange = { viewModel.updateTitle(it) },
                    description = description,
                    onDescriptionChange = {
                        onDescriptionChange?.invoke(it)
                        viewModel.markAsModified()
                    },
                    descriptionError = descriptionError,
                    actionCount = state.actionCount,
                    totalDuration = state.totalDuration,
                    screenWidth = state.screenWidth,
                    screenHeight = state.screenHeight
                )
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (state.actions.isNotEmpty()) {
                stickyHeader {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            RecordingTimeline(
                                actions = state.actions,
                                totalDuration = state.totalDuration,
                                baseActionsDuration = state.baseActionsDuration,
                                dynamicMinDuration = dynamicMinDuration,
                                startDelay = state.startDelay,
                                endDelay = state.endDelay,
                                firstActionTime = state.firstActionTime,
                                zoomScale = mainTimelineZoomScale,
                                onZoomScaleChange = { mainTimelineZoomScale = it },
                                focusedActionId = state.focusedActionId,
                                onActionClick = { actionId ->
                                    val targetIndex = viewModel.expandSingleAction(actionId)
                                    if (targetIndex >= 0) {
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(
                                                index = targetIndex,
                                                scrollOffset = 0
                                            )
                                        }
                                    }
                                    viewModel.selectSingleAction(actionId, null)
                                },
                                onBaseActionsDurationChange = { newTotalDuration ->
                                    viewModel.updateTotalDuration(newTotalDuration)
                                }
                            )
                        }
                    }
                }
            }

            if (state.selectedCount > 0) {
                item {
                    BatchOperationBar(
                        selectedCount = state.selectedCount,
                        onSelectAll = { viewModel.selectAll() },
                        onDeselectAll = { viewModel.deselectAll() },
                        onBatchDelete = { showBatchDeleteDialog = true },
                        onBatchSetInterval = { showBatchIntervalDialog = true }
                    )
                }
            }

            if (state.actions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillParentMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.TouchApp,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.no_recorded_actions),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = state.actions,
                    key = { _, item -> item.id }
                ) { index, editableAction ->
                    EnhancedActionCard(
                        action = editableAction,
                        index = index,
                        totalDuration = state.totalDuration,
                        screenWidth = state.screenWidth,
                        screenHeight = state.screenHeight,
                        currentInterval = viewModel.getActionInterval(editableAction.id),
                        maxInterval = null,
                        intervalScaleFactor = intervalScaleFactor,
                        onIntervalScaleFactorChange = { intervalScaleFactor = normalizeScaleFactor(it) },
                        showInsertControls = false,
                        onInsertBefore = null,
                        onInsertAfter = null,
                        onToggleExpand = {
                            val wasExpanded = editableAction.isExpanded
                            viewModel.toggleExpand(editableAction.id)
                            if (!wasExpanded) {
                                viewModel.updateFocusedAction(editableAction.id, null)
                            }
                        },
                        onToggleSelect = {
                            viewModel.toggleSelection(editableAction.id)
                        },
                        onProgressChange = { newProgress ->
                            viewModel.updateProgress(editableAction.id, newProgress)
                        },
                        onIntervalChange = { newInterval ->
                            viewModel.setActionInterval(editableAction.id, newInterval, cascadeForward = true)
                        },
                        onDelete = { viewModel.deleteAction(editableAction.id) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

private data class LiveEditPoint(
    val actionId: String,
    val pointIndex: Int,
    val x: Float,
    val y: Float,
    val actionType: String
)

private data class DisplayPoint(
    val actionId: String,
    val pointIndex: Int,
    val actionType: String,
    val screenX: Float,
    val screenY: Float,
    val displayX: Float,
    val displayY: Float
)

private data class InsertRequest(
    val actionId: String,
    val insertBefore: Boolean
)

private enum class SelectionMode {
    Multi,
    Single
}

@Composable
private fun ScreenshotButtonRow(
    hasScreenshot: Boolean,
    onPick: () -> Unit
) {
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.End
    ) {
        OutlinedButton(onClick = onPick) {
            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                if (hasScreenshot) {
                    stringResource(R.string.replace_screenshot)
                } else {
                    stringResource(R.string.upload_screenshot)
                }
            )
        }
    }
}

@Composable
private fun ActionList(
    state: RecordingEditState,
    viewModel: RecordingEditViewModel,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    selectionMode: SelectionMode = SelectionMode.Multi,
    showInsertControls: Boolean = false,
    onInsertBefore: ((String) -> Unit)? = null,
    onInsertAfter: ((String) -> Unit)? = null,
    onActionFocused: ((String) -> Unit)? = null,
    intervalScaleFactor: Float = 2f,
    onIntervalScaleFactorChange: (Float) -> Unit = {}
) {
    if (state.actions.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.TouchApp,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.no_recorded_actions),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                items = state.actions,
                key = { _, item -> item.id }
            ) { index, editableAction ->
                EnhancedActionCard(
                    action = editableAction,
                    index = index,
                    totalDuration = state.totalDuration,
                    screenWidth = state.screenWidth,
                    screenHeight = state.screenHeight,
                    currentInterval = viewModel.getActionInterval(editableAction.id),
                    maxInterval = null,
                    intervalScaleFactor = intervalScaleFactor,
                    onIntervalScaleFactorChange = onIntervalScaleFactorChange,
                    showInsertControls = showInsertControls,
                    onInsertBefore = {
                        onInsertBefore?.invoke(editableAction.id)
                    },
                    onInsertAfter = {
                        onInsertAfter?.invoke(editableAction.id)
                    },
                    onToggleExpand = {
                        val wasExpanded = editableAction.isExpanded
                        viewModel.toggleExpand(editableAction.id)
                        if (!wasExpanded) {
                            onActionFocused?.invoke(editableAction.id)
                        }
                    },
                    onToggleSelect = {
                        when (selectionMode) {
                            SelectionMode.Single -> viewModel.toggleSingleSelection(editableAction.id)
                            SelectionMode.Multi -> viewModel.toggleSelection(editableAction.id)
                        }
                    },
                    onProgressChange = { newProgress ->
                        viewModel.updateProgress(editableAction.id, newProgress)
                    },
                    onIntervalChange = { newInterval ->
                        viewModel.setActionInterval(editableAction.id, newInterval, cascadeForward = true)
                    },
                    onDelete = { viewModel.deleteAction(editableAction.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveEditScreen(
    state: RecordingEditState,
    viewModel: RecordingEditViewModel,
    listState: LazyListState,
    timelineZoomScale: Float,
    onTimelineZoomScaleChange: (Float) -> Unit,
    onBack: () -> Unit,
    rotationDegrees: Int,
    onRotate: () -> Unit,
    onPickScreenshot: () -> Unit,
    onInsertRequested: (String, Boolean) -> Unit,
    intervalScaleFactor: Float,
    onIntervalScaleFactorChange: (Float) -> Unit,
    onDisplayRectInWindow: (Rect) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val hasScreenshot = !state.screenshotPath.isNullOrBlank()
    val statusBarPadding = rememberV2StatusBarTopPadding()
    val configuration = LocalConfiguration.current
    val containerPortrait = configuration.screenWidthDp < configuration.screenHeightDp
    val recordLandscape = state.screenWidth >= state.screenHeight
    var autoRotated by remember { mutableStateOf(false) }
    LaunchedEffect(recordLandscape, containerPortrait) {
        if (!autoRotated && recordLandscape && containerPortrait && (rotationDegrees % 360) == 0) {
            onRotate()
            autoRotated = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.padding(top = statusBarPadding),
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        text = stringResource(R.string.live_edit_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onPickScreenshot) {
                        Text(
                            if (hasScreenshot) {
                                stringResource(R.string.replace_screenshot)
                            } else {
                                stringResource(R.string.upload_screenshot)
                            }
                        )
                    }
                    IconButton(onClick = onRotate) {
                        Icon(
                            Icons.AutoMirrored.Filled.RotateRight,
                            contentDescription = stringResource(R.string.rotate_screenshot)
                        )
                    }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val density = LocalDensity.current
            val imageSize = remember(state.screenshotPath, state.screenWidth, state.screenHeight) {
                val fromRecord = if (state.screenWidth > 0 && state.screenHeight > 0) {
                    state.screenWidth to state.screenHeight
                } else {
                    state.screenshotPath?.let { getImageSize(it) }
                }
                fromRecord
            }
            val rotationNormalized = ((rotationDegrees % 360) + 360) % 360
            val baseWidth = imageSize?.first ?: 1
            val baseHeight = imageSize?.second ?: 1
            val displayWidth = if (rotationNormalized == 90 || rotationNormalized == 270) baseHeight else baseWidth
            val displayHeight = if (rotationNormalized == 90 || rotationNormalized == 270) baseWidth else baseHeight
            val maxWidthPx = with(density) { maxWidth.toPx() }
            val maxHeightPx = with(density) { maxHeight.toPx() }
            val portraitMaxHeightPx = maxHeightPx * 0.5f
            val isPortraitDisplay = displayHeight >= displayWidth
            val imageHeightPx = (maxWidthPx * (displayHeight.toFloat() / displayWidth.toFloat()))
                .coerceAtMost(if (isPortraitDisplay) portraitMaxHeightPx else maxHeightPx)
            val imageHeightDp = with(density) { imageHeightPx.toDp() }
            val maxSheetTopPx = maxHeightPx * 0.5f
            var sheetTopPx by remember(maxHeightPx) { mutableFloatStateOf(maxSheetTopPx) }
            val sheetHeightPx = (maxHeightPx - sheetTopPx).coerceAtLeast(0f)
            val sheetHeightDp = with(density) { sheetHeightPx.toDp() }
            val sheetOffsetDp = with(density) { sheetTopPx.toDp() }
            val sheetDragState = rememberDraggableState { delta ->
                sheetTopPx = (sheetTopPx + delta).coerceIn(0f, maxSheetTopPx)
            }

            Box(modifier = Modifier.fillMaxSize()) {
                LiveEditPanel(
                    screenshotPath = state.screenshotPath,
                    screenWidth = state.screenWidth,
                    screenHeight = state.screenHeight,
                    actions = state.actions,
                    focusedActionId = state.focusedActionId,
                    focusedPointIndex = state.focusedPointIndex,
                    onPointSelected = { actionId, pointIndex ->
                        val targetIndex = viewModel.expandSingleAction(actionId)
                        if (targetIndex >= 0) {
                            coroutineScope.launch {
                                listState.animateScrollToItem(
                                    index = targetIndex,
                                    scrollOffset = 0
                                )
                            }
                        }
                        viewModel.selectSingleAction(actionId, pointIndex)
                    },
                    onPointDragged = { actionId, pointIndex, newX, newY ->
                        viewModel.updateActionCoordinate(actionId, newX, newY, pointIndex)
                    },
                    rotationDegrees = rotationDegrees,
                    onDisplayRectInWindow = onDisplayRectInWindow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(imageHeightDp)
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = sheetOffsetDp)
                        .height(sheetHeightDp),
                    shadowElevation = 6.dp,
                    tonalElevation = 2.dp,
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .draggable(
                                    state = sheetDragState,
                                    orientation = Orientation.Vertical
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = stringResource(R.string.drag_handle),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!hasScreenshot) {
                            Text(
                                text = stringResource(R.string.enter_live_edit_upload_first),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        if (state.actions.isNotEmpty()) {
                            val maxActionTime = remember(state.actions, state.totalDuration) {
                                state.actions.maxOfOrNull {
                                    (it.action.progress * state.totalDuration).toLong()
                                } ?: 0L
                            }
                            val dynamicMinDuration = maxOf(1000L, maxActionTime)
                            RecordingTimeline(
                                actions = state.actions,
                                totalDuration = state.totalDuration,
                                baseActionsDuration = state.baseActionsDuration,
                                dynamicMinDuration = dynamicMinDuration,
                                startDelay = state.startDelay,
                                endDelay = state.endDelay,
                                firstActionTime = state.firstActionTime,
                                zoomScale = timelineZoomScale,
                                onZoomScaleChange = onTimelineZoomScaleChange,
                                focusedActionId = state.focusedActionId,
                                onActionClick = { actionId ->
                                    val targetIndex = viewModel.expandSingleAction(actionId)
                                    if (targetIndex >= 0) {
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(
                                                index = targetIndex,
                                                scrollOffset = 0
                                            )
                                        }
                                    }
                                    viewModel.selectSingleAction(actionId, null)
                                }
                            )
                        }

                        ActionList(
                            state = state,
                            viewModel = viewModel,
                            listState = listState,
                            selectionMode = SelectionMode.Single,
                            showInsertControls = true,
                            intervalScaleFactor = intervalScaleFactor,
                            onIntervalScaleFactorChange = onIntervalScaleFactorChange,
                            onInsertBefore = { actionId -> onInsertRequested(actionId, true) },
                            onInsertAfter = { actionId -> onInsertRequested(actionId, false) },
                            modifier = Modifier.fillMaxHeight(),
                            onActionFocused = { actionId ->
                                viewModel.selectSingleAction(actionId, null)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveEditPanel(
    screenshotPath: String?,
    screenWidth: Int,
    screenHeight: Int,
    actions: List<EditableAction>,
    focusedActionId: String?,
    focusedPointIndex: Int?,
    onPointSelected: (String, Int) -> Unit,
    onPointDragged: (String, Int, Int, Int) -> Unit,
    rotationDegrees: Int,
    onDisplayRectInWindow: (Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val bitmap = remember(screenshotPath, screenWidth, screenHeight) {
        screenshotPath?.let { loadOrientedScreenshot(it, screenWidth, screenHeight) }
    }
    val displayBitmap = remember(bitmap, rotationDegrees) {
        bitmap?.let { rotateBitmapIfNeeded(it, rotationDegrees) }
    }
    val effectiveScreenWidth = if (screenWidth > 0) screenWidth else (bitmap?.width ?: 0)
    val effectiveScreenHeight = if (screenHeight > 0) screenHeight else (bitmap?.height ?: 0)
    val rotationNormalized = ((rotationDegrees % 360) + 360) % 360
    val displayScreenWidth = if (rotationNormalized == 90 || rotationNormalized == 270) {
        effectiveScreenHeight
    } else {
        effectiveScreenWidth
    }
    val displayScreenHeight = if (rotationNormalized == 90 || rotationNormalized == 270) {
        effectiveScreenWidth
    } else {
        effectiveScreenHeight
    }
    val points = remember(actions, effectiveScreenWidth, effectiveScreenHeight) {
        resolveLiveEditPoints(actions, effectiveScreenWidth, effectiveScreenHeight)
    }

    LaunchedEffect(screenshotPath, screenWidth, screenHeight, effectiveScreenWidth, effectiveScreenHeight, bitmap) {
        Log.d(
            "LiveEditPanel",
            "Live edit panel initialized: pointCount=${points.size}"
        )
    }

    if (displayBitmap == null) {
        Text(
            text = stringResource(R.string.screenshot_load_failed),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        return
    }

    var draggingPoint by remember { mutableStateOf<DisplayPoint?>(null) }
    var draggingScreenOffset by remember { mutableStateOf<Offset?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
    ) {
        val containerWidth = with(density) { maxWidth.toPx() }
        val containerHeight = with(density) { maxHeight.toPx() }
        val targetWidth = displayScreenWidth.toFloat().coerceAtLeast(1f)
        val targetHeight = displayScreenHeight.toFloat().coerceAtLeast(1f)
        val scale = minOf(containerWidth / targetWidth, containerHeight / targetHeight)
        val displayWidth = targetWidth * scale
        val displayHeight = targetHeight * scale
        val offsetX = (containerWidth - displayWidth) / 2f
        val offsetY = (containerHeight - displayHeight) / 2f
        val displayRect = Rect(offsetX, offsetY, offsetX + displayWidth, offsetY + displayHeight)
        val hitRadiusPx = with(density) { 18.dp.toPx() }
        val pointRadiusPx = with(density) { 6.dp.toPx() }

        val displayPoints = remember(points, displayRect, effectiveScreenWidth, effectiveScreenHeight, rotationNormalized) {
            points.map { point ->
                val rotated = rotatePointForDisplay(
                    point.x,
                    point.y,
                    effectiveScreenWidth.toFloat(),
                    effectiveScreenHeight.toFloat(),
                    rotationNormalized
                )
                val relX = if (displayScreenWidth > 0) rotated.first / displayScreenWidth.toFloat() else 0f
                val relY = if (displayScreenHeight > 0) rotated.second / displayScreenHeight.toFloat() else 0f
                DisplayPoint(
                    actionId = point.actionId,
                    pointIndex = point.pointIndex,
                    actionType = point.actionType,
                    screenX = point.x,
                    screenY = point.y,
                    displayX = displayRect.left + relX * displayRect.width,
                    displayY = displayRect.top + relY * displayRect.height
                )
            }
        }
        val selectedDisplayPoints = remember(displayPoints, focusedActionId, focusedPointIndex) {
            displayPoints.filter { point ->
                point.actionId == focusedActionId &&
                    (focusedPointIndex == null || point.pointIndex == focusedPointIndex)
            }
        }
        val actionIndexMap = remember(actions) {
            actions.mapIndexed { index, action -> action.id to (index + 1) }.toMap()
        }
        val iconSizeDp = FeedbackIconView.ICON_SIZE_DP.dp
        val iconSizePx = with(density) { iconSizeDp.toPx() }

        LaunchedEffect(displayPoints, displayRect) {
            val sample = displayPoints.firstOrNull()
            if (sample != null) {
                Log.d(
                    "LiveEditPanel",
                    "Live edit sample resolved"
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                bitmap = displayBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .offset { IntOffset(offsetX.toInt(), offsetY.toInt()) }
                    .size(
                        with(density) { displayWidth.toDp() },
                        with(density) { displayHeight.toDp() }
                    )
                    .onGloballyPositioned { coords ->
                        val bounds = coords.boundsInWindow()
                        onDisplayRectInWindow(bounds)
                    }
            )

            selectedDisplayPoints.forEach { point ->
                val dragOverride = if (draggingPoint?.actionId == point.actionId &&
                    draggingPoint?.pointIndex == point.pointIndex) {
                    draggingScreenOffset
                } else {
                    null
                }
                val rotated = rotatePointForDisplay(
                    dragOverride?.x ?: point.screenX,
                    dragOverride?.y ?: point.screenY,
                    effectiveScreenWidth.toFloat(),
                    effectiveScreenHeight.toFloat(),
                    rotationNormalized
                )
                val relX = if (displayScreenWidth > 0) rotated.first / displayScreenWidth.toFloat() else 0f
                val relY = if (displayScreenHeight > 0) rotated.second / displayScreenHeight.toFloat() else 0f
                val drawX = displayRect.left + relX * displayRect.width
                val drawY = displayRect.top + relY * displayRect.height
                val stepNumber = actionIndexMap[point.actionId] ?: 1
                val centerLabel = if (point.actionType == "swipe") {
                    if (point.pointIndex == 0) "S" else "E"
                } else {
                    null
                }

                AndroidView(
                    factory = { context ->
                        FeedbackIconView(context).apply {
                            setIcon(point.actionType)
                            setStepNumber(stepNumber)
                            setCenterLabel(centerLabel)
                        }
                    },
                    update = { view ->
                        view.setIcon(point.actionType)
                        view.setStepNumber(stepNumber)
                        view.setCenterLabel(centerLabel)
                    },
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (drawX - iconSizePx / 2f).toInt(),
                                (drawY - iconSizePx / 2f).toInt()
                            )
                        }
                        .size(iconSizeDp)
                )
            }

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(selectedDisplayPoints, displayRect) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val hit = selectedDisplayPoints.minByOrNull { point ->
                                    val dx = offset.x - point.displayX
                                    val dy = offset.y - point.displayY
                                    dx * dx + dy * dy
                                }
                                if (hit != null) {
                                    val dx = offset.x - hit.displayX
                                    val dy = offset.y - hit.displayY
                                    if (dx * dx + dy * dy <= hitRadiusPx * hitRadiusPx) {
                                        Log.d(
                                            "LiveEditPanel",
                                            "Drag started"
                                        )
                                        draggingPoint = hit
                                        draggingScreenOffset = Offset(hit.screenX, hit.screenY)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }
                            },
                            onDrag = { change, _ ->
                                val current = draggingPoint ?: return@detectDragGestures
                                val clampedX = change.position.x.coerceIn(displayRect.left, displayRect.right)
                                val clampedY = change.position.y.coerceIn(displayRect.top, displayRect.bottom)
                                val relX = (clampedX - displayRect.left) / displayRect.width
                                val relY = (clampedY - displayRect.top) / displayRect.height
                                val displayX = (relX * displayScreenWidth.toFloat())
                                val displayY = (relY * displayScreenHeight.toFloat())
                                val original = rotatePointFromDisplay(
                                    displayX,
                                    displayY,
                                    effectiveScreenWidth.toFloat(),
                                    effectiveScreenHeight.toFloat(),
                                    rotationNormalized
                                )
                                val screenX = original.first.coerceIn(0f, effectiveScreenWidth.toFloat())
                                val screenY = original.second.coerceIn(0f, effectiveScreenHeight.toFloat())
                                draggingScreenOffset = Offset(screenX, screenY)
                                change.consume()
                            },
                            onDragEnd = {
                                val current = draggingPoint
                                val screenOffset = draggingScreenOffset
                                if (current != null && screenOffset != null) {
                                    Log.d(
                                        "LiveEditPanel",
                                        "Drag ended"
                                    )
                                    onPointDragged(
                                        current.actionId,
                                        current.pointIndex,
                                        screenOffset.x.toInt(),
                                        screenOffset.y.toInt()
                                    )
                                }
                                draggingPoint = null
                                draggingScreenOffset = null
                            },
                            onDragCancel = {
                                draggingPoint = null
                                draggingScreenOffset = null
                            }
                        )
                    }
            )
        }
    }
}

private fun loadOrientedScreenshot(
    path: String,
    recordWidth: Int,
    recordHeight: Int
): Bitmap? {
    val raw = BitmapFactory.decodeFile(path) ?: return null
    val exifRotation = getExifRotation(path)
    var rotated = rotateBitmapIfNeeded(raw, exifRotation)

    if (recordWidth > 0 && recordHeight > 0) {
        val recordLandscape = recordWidth >= recordHeight
        val bitmapLandscape = rotated.width >= rotated.height
        if (recordLandscape != bitmapLandscape) {
            rotated = rotateBitmapIfNeeded(rotated, 90)
        }
        if (rotated.width != recordWidth || rotated.height != recordHeight) {
            rotated = Bitmap.createScaledBitmap(rotated, recordWidth, recordHeight, true)
        }
    }
    return rotated
}

private fun getExifRotation(path: String): Int {
    return try {
        val exif = ExifInterface(path)
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (_: Exception) {
        0
    }
}

private fun rotateBitmapIfNeeded(source: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return source
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

private fun rotatePointForDisplay(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    rotation: Int
): Pair<Float, Float> {
    return when (rotation) {
        90 -> (height - y) to x
        180 -> (width - x) to (height - y)
        270 -> y to (width - x)
        else -> x to y
    }
}

private fun rotatePointFromDisplay(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    rotation: Int
): Pair<Float, Float> {
    return when (rotation) {
        90 -> y to (height - x)
        180 -> (width - x) to (height - y)
        270 -> (width - y) to x
        else -> x to y
    }
}

private fun getImageSize(path: String): Pair<Int, Int>? {
    return try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        if (options.outWidth > 0 && options.outHeight > 0) {
            options.outWidth to options.outHeight
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}

private fun mapRecordedActionToScreen(
    action: RecordedAction,
    displayRect: Rect?,
    screenWidth: Int,
    screenHeight: Int,
    rotationDegrees: Int,
    windowOffset: Pair<Float, Float>?
): RecordedAction {
    if (displayRect == null || screenWidth <= 0 || screenHeight <= 0) {
        return action
    }
    val rotationNormalized = ((rotationDegrees % 360) + 360) % 360
    val displayScreenWidth = if (rotationNormalized == 90 || rotationNormalized == 270) {
        screenHeight
    } else {
        screenWidth
    }
    val displayScreenHeight = if (rotationNormalized == 90 || rotationNormalized == 270) {
        screenWidth
    } else {
        screenHeight
    }

    fun toAbsoluteIfNeeded(x: Float, y: Float): Pair<Float, Float> {
        return if (x in 0f..1.5f && y in 0f..1.5f) {
            (x * screenWidth) to (y * screenHeight)
        } else {
            x to y
        }
    }

    fun mapPoint(x: Float, y: Float): Pair<Float, Float> {
        val (absX, absY) = toAbsoluteIfNeeded(x, y)
        val offset = windowOffset ?: (0f to 0f)
        val windowX = absX - offset.first
        val windowY = absY - offset.second
        val relX = ((windowX - displayRect.left) / displayRect.width).coerceIn(0f, 1f)
        val relY = ((windowY - displayRect.top) / displayRect.height).coerceIn(0f, 1f)
        val displayX = relX * displayScreenWidth.toFloat()
        val displayY = relY * displayScreenHeight.toFloat()
        val original = rotatePointFromDisplay(
            displayX,
            displayY,
            screenWidth.toFloat(),
            screenHeight.toFloat(),
            rotationNormalized
        )
        return original.first to original.second
    }

    val params = action.params.toMutableMap()
    when (action.actionType) {
        "tap", "longPress" -> {
            val x = safeToFloat(params["x"])
            val y = safeToFloat(params["y"])
            val (absX, absY) = toAbsoluteIfNeeded(x, y)
            val mapped = mapPoint(absX, absY)
            params["x"] = mapped.first.toInt()
            params["y"] = mapped.second.toInt()
        }
        "multiTap" -> {
            val points = params["points"] as? List<*> ?: emptyList<Any>()
            val mappedPoints = points.mapNotNull { item ->
                val raw = when (item) {
                    is Map<*, *> -> {
                        val x = safeToFloat(item["x"])
                        val y = safeToFloat(item["y"])
                        toAbsoluteIfNeeded(x, y)
                    }
                    is List<*> -> {
                        val x = safeToFloat(item.getOrNull(0))
                        val y = safeToFloat(item.getOrNull(1))
                        toAbsoluteIfNeeded(x, y)
                    }
                    else -> null
                } ?: return@mapNotNull null
                val mapped = mapPoint(raw.first, raw.second)
                mapOf("x" to mapped.first.toInt(), "y" to mapped.second.toInt())
            }
            params["points"] = mappedPoints
        }
        "swipe", "longPressSwipe" -> {
            val pathList = params["path"] as? List<*>
            if (!pathList.isNullOrEmpty()) {
                val mappedPath = pathList.mapNotNull { item ->
                    val raw = when (item) {
                        is Map<*, *> -> {
                            val x = safeToFloat(item["x"])
                            val y = safeToFloat(item["y"])
                            toAbsoluteIfNeeded(x, y)
                        }
                        is List<*> -> {
                            val x = safeToFloat(item.getOrNull(0))
                            val y = safeToFloat(item.getOrNull(1))
                            toAbsoluteIfNeeded(x, y)
                        }
                        else -> null
                    } ?: return@mapNotNull null
                    val mapped = mapPoint(raw.first, raw.second)
                    mapOf("x" to mapped.first.toInt(), "y" to mapped.second.toInt())
                }
                params["path"] = mappedPath
            } else {
                val startX = safeToFloat(params["startX"])
                val startY = safeToFloat(params["startY"])
                val endX = safeToFloat(params["endX"])
                val endY = safeToFloat(params["endY"])
                val (absStartX, absStartY) = toAbsoluteIfNeeded(startX, startY)
                val (absEndX, absEndY) = toAbsoluteIfNeeded(endX, endY)
                val mappedStart = mapPoint(absStartX, absStartY)
                val mappedEnd = mapPoint(absEndX, absEndY)
                params["startX"] = mappedStart.first.toInt()
                params["startY"] = mappedStart.second.toInt()
                params["endX"] = mappedEnd.first.toInt()
                params["endY"] = mappedEnd.second.toInt()
            }
        }
    }
    return action.copy(paramsJson = Gson().toJson(params))
}

private fun resolveLiveEditPoints(
    actions: List<EditableAction>,
    screenWidth: Int,
    screenHeight: Int
): List<LiveEditPoint> {
    val points = mutableListOf<LiveEditPoint>()
    actions.forEach { editableAction ->
        val action = editableAction.action
        val params = action.params
        when (action.actionType) {
            "tap", "longPress" -> {
                resolveTapPointFromParams(params, screenWidth, screenHeight)?.let { resolved ->
                    points.add(
                        LiveEditPoint(
                            actionId = editableAction.id,
                            pointIndex = 0,
                            x = resolved.first,
                            y = resolved.second,
                            actionType = action.actionType
                        )
                    )
                }
            }
            "multiTap" -> {
                val resolvedPoints = resolveMultiTapPointsFromParams(params, screenWidth, screenHeight)
                resolvedPoints.forEachIndexed { index, resolved ->
                    points.add(
                        LiveEditPoint(
                            actionId = editableAction.id,
                            pointIndex = index,
                            x = resolved.first,
                            y = resolved.second,
                            actionType = action.actionType
                        )
                    )
                }
            }
            "swipe", "longPressSwipe" -> {
                val resolvedPoints = resolveSwipeEndpointsFromParams(params, screenWidth, screenHeight)
                resolvedPoints.forEachIndexed { index, resolved ->
                    points.add(
                        LiveEditPoint(
                            actionId = editableAction.id,
                            pointIndex = index,
                            x = resolved.first,
                            y = resolved.second,
                            actionType = action.actionType
                        )
                    )
                }
            }
        }
    }
    return points
}

private fun resolveTapPointFromParams(
    params: Map<String, Any>,
    screenWidth: Int,
    screenHeight: Int
): Pair<Float, Float>? {
    if (params.containsKey("x") && params.containsKey("y")) {
        val x = safeToFloat(params["x"])
        val y = safeToFloat(params["y"])
        return resolvePointToScreen(x, y, screenWidth, screenHeight)
    }
    val element = params["element"] as? List<*>
    val fromElement = element?.let { extractPointFromList(it) } ?: return null
    return resolvePointToScreen(fromElement.first, fromElement.second, screenWidth, screenHeight)
}

private fun resolveMultiTapPointsFromParams(
    params: Map<String, Any>,
    screenWidth: Int,
    screenHeight: Int
): List<Pair<Float, Float>> {
    val pointsList = params["points"] as? List<*> ?: emptyList<Any>()
    val resolved = mutableListOf<Pair<Float, Float>>()
    pointsList.forEach { item ->
        val rawPoint = when (item) {
            is Map<*, *> -> extractPointFromMap(item)
            is List<*> -> extractPointFromList(item)
            else -> null
        } ?: return@forEach
        resolved.add(resolvePointToScreen(rawPoint.first, rawPoint.second, screenWidth, screenHeight))
    }
    return resolved
}

private fun resolveSwipeEndpointsFromParams(
    params: Map<String, Any>,
    screenWidth: Int,
    screenHeight: Int
): List<Pair<Float, Float>> {
    val pathList = params["path"] as? List<*>
    if (!pathList.isNullOrEmpty()) {
        val first = pathList.firstOrNull()
        val last = pathList.lastOrNull()
        val start = when (first) {
            is Map<*, *> -> extractPointFromMap(first)
            is List<*> -> extractPointFromList(first)
            else -> null
        }
        val end = when (last) {
            is Map<*, *> -> extractPointFromMap(last)
            is List<*> -> extractPointFromList(last)
            else -> null
        }
        return listOfNotNull(start, end).map { resolvePointToScreen(it.first, it.second, screenWidth, screenHeight) }
    }

    val startX = safeToFloat(params["startX"])
    val startY = safeToFloat(params["startY"])
    val endX = safeToFloat(params["endX"])
    val endY = safeToFloat(params["endY"])
    val hasStart = params.containsKey("startX") && params.containsKey("startY")
    val hasEnd = params.containsKey("endX") && params.containsKey("endY")
    val start = if (hasStart) resolvePointToScreen(startX, startY, screenWidth, screenHeight) else null
    val end = if (hasEnd) resolvePointToScreen(endX, endY, screenWidth, screenHeight) else null
    return listOfNotNull(start, end)
}

private fun extractPointFromMap(map: Map<*, *>): Pair<Float, Float>? {
    if (!map.containsKey("x") || !map.containsKey("y")) return null
    val x = safeToFloat(map["x"])
    val y = safeToFloat(map["y"])
    return x to y
}

private fun extractPointFromList(list: List<*>): Pair<Float, Float>? {
    if (list.size < 2) return null
    val x = safeToFloat(list[0])
    val y = safeToFloat(list[1])
    return x to y
}

private fun resolvePointToScreen(
    x: Float,
    y: Float,
    screenWidth: Int,
    screenHeight: Int
): Pair<Float, Float> {
    if (screenWidth <= 0 || screenHeight <= 0) {
        return x to y
    }
    val isRelative = x in 0f..1.5f && y in 0f..1.5f
    return if (isRelative) {
        (x * screenWidth) to (y * screenHeight)
    } else {
        x to y
    }
}

private fun safeToFloat(value: Any?): Float {
    return when (value) {
        is Float -> value
        is Double -> value.toFloat()
        is Int -> value.toFloat()
        is Number -> value.toFloat()
        else -> 0f
    }
}

private fun cacheScreenshotFromUri(context: Context, uri: Uri): String? {
    return try {
        val cacheFile = File(context.cacheDir, "recording_upload_${System.currentTimeMillis()}.png")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(cacheFile).use { output ->
                input.copyTo(output)
            }
        } ?: return null
        cacheFile.absolutePath
    } catch (e: Exception) {
        null
    }
}

/**
 * 录制信息卡片
 */
@Composable
private fun RecordingInfoCard(
    title: String,
    onTitleChange: (String) -> Unit,
    description: String? = null,
    onDescriptionChange: ((String) -> Unit)? = null,
    descriptionError: Boolean = false,
    actionCount: Int,
    totalDuration: Long,
    screenWidth: Int,
    screenHeight: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题输入
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                label = { Text(stringResource(R.string.recording_name_label)) },
                placeholder = { Text(stringResource(R.string.recording_name_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Edit, contentDescription = null)
                },
                shape = RoundedCornerShape(18.dp)
            )

            if (description != null && onDescriptionChange != null) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    label = { Text(stringResource(R.string.recording_description_label)) },
                    placeholder = { Text(stringResource(R.string.recording_description_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    isError = descriptionError,
                    supportingText = if (descriptionError) {
                        { Text(stringResource(R.string.recording_description_required)) }
                    } else null,
                    shape = RoundedCornerShape(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 统计信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                InfoChip(
                    icon = Icons.AutoMirrored.Filled.List,
                    label = stringResource(R.string.recording_info_actions),
                    value = stringResource(R.string.recording_action_count_format, actionCount)
                )
                InfoChip(
                    icon = Icons.Default.Timer,
                    label = stringResource(R.string.recording_info_duration),
                    value = formatDuration(totalDuration)
                )
                InfoChip(
                    icon = Icons.Default.Smartphone,
                    label = stringResource(R.string.recording_info_size),
                    value = "${screenWidth}×${screenHeight}"
                )
            }
        }
    }
}

/**
 * 信息芯片
 */
@Composable
private fun InfoChip(
    icon: ImageVector,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 批量操作工具栏
 */
@Composable
private fun BatchOperationBar(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onBatchDelete: () -> Unit,
    onBatchSetInterval: () -> Unit  // 改为设置间隔
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.selected_count_format, selectedCount),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onSelectAll) {
                Text(stringResource(R.string.select_all), style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onDeselectAll) {
                Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onBatchSetInterval) {
                Icon(Icons.Default.Schedule, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.interval), style = MaterialTheme.typography.labelSmall)
            }
            TextButton(
                onClick = onBatchDelete,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.delete), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * 批量调整延迟对话框
 */
@Composable
private fun BatchDelayDialog(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (uniformDelay: Long?, scaleRatio: Float?) -> Unit
) {
    var selectedMode by remember { mutableStateOf(0) } // 0: 统一延迟, 1: 按比例缩放
    var uniformDelay by remember { mutableStateOf(500L) }
    var scaleRatio by remember { mutableStateOf(1.0f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.batch_delay_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.batch_delay_message, selectedCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 模式选择
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedMode == 0,
                        onClick = { selectedMode = 0 },
                        label = { Text(stringResource(R.string.batch_delay_uniform)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedMode == 1,
                        onClick = { selectedMode = 1 },
                        label = { Text(stringResource(R.string.batch_delay_scale)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 统一延迟设置
                if (selectedMode == 0) {
                    Text(
                        stringResource(R.string.batch_delay_uniform_label),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = uniformDelay.toFloat(),
                            onValueChange = { uniformDelay = it.toLong() },
                            valueRange = 0f..3000f,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${uniformDelay}ms",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.widthIn(min = 60.dp)
                        )
                    }
                }

                // 比例缩放设置
                if (selectedMode == 1) {
                    Text(
                        stringResource(R.string.batch_delay_scale_label),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0.5f to "0.5×", 1.0f to "1.0×", 1.5f to "1.5×", 2.0f to "2.0×").forEach { (ratio, label) ->
                            FilterChip(
                                selected = scaleRatio == ratio,
                                onClick = { scaleRatio = ratio },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedMode == 0) {
                        onConfirm(uniformDelay, null)
                    } else {
                        onConfirm(null, scaleRatio)
                    }
                }
            ) {
                Text(stringResource(R.string.confirm))
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
 * 批量设置间隔对话框
 */
@Composable
private fun BatchIntervalDialog(
    selectedCount: Int,
    totalDuration: Long,
    intervalScaleFactor: Float,
    onIntervalScaleFactorChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit,
    onScaleApply: () -> Unit
) {
    // 默认 1000ms
    val defaultMillis = 1000L
    var intervalInputText by remember { mutableStateOf(defaultMillis.toString()) }
    var intervalValue by remember {
        mutableStateOf(if (totalDuration > 0) defaultMillis.toFloat() / totalDuration.toFloat() else 0f)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.interval_dialog_title))
                Spacer(modifier = Modifier.width(8.dp))
                HelpTooltip(
                    text = stringResource(R.string.help_interval_tooltip)
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.interval_for_selected_format, selectedCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = intervalInputText,
                    onValueChange = { input ->
                        // 只允许数字输入
                        if (input.isEmpty() || input.all { it.isDigit() }) {
                            intervalInputText = input
                            val millis = input.toLongOrNull() ?: 0L
                            val newInterval = if (totalDuration > 0) {
                                millis.toFloat() / totalDuration.toFloat()
                            } else {
                                0f
                            }
                            intervalValue = newInterval
                        }
                    },
                    label = { Text(stringResource(R.string.interval_ms_label)) },
                    suffix = { Text(stringResource(R.string.ms_suffix)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    shape = RoundedCornerShape(18.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            onScaleApply()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.double_interval))
                    }
                    IntervalFactorStepper(
                        label = stringResource(R.string.times_label),
                        value = intervalScaleFactor,
                        onValueChange = { onIntervalScaleFactorChange(normalizeScaleFactor(it)) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 快捷时间选项
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.quick_presets),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    val presets = listOf(
                        100L to "0.1s",
                        250L to "0.25s",
                        500L to "0.5s",
                        1000L to "1.0s",
                        1500L to "1.5s",
                        2000L to "2.0s",
                        5000L to "5.0s",
                        10000L to "10.0s"
                    )
                    presets.chunked(4).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowItems.forEach { (millis, label) ->
                                FilterChip(
                                    selected = intervalInputText == millis.toString(),
                                    onClick = {
                                        intervalInputText = millis.toString()
                                        val newInterval = if (totalDuration > 0) {
                                            millis.toFloat() / totalDuration.toFloat()
                                        } else {
                                            0f
                                        }
                                        intervalValue = newInterval
                                    },
                                    label = {
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Center,
                                            maxLines = 1
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(intervalValue) }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun IntervalFactorStepper(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(
                R.string.scale_factor_label_format,
                label,
                formatScaleFactor(value)
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onValueChange(stepScaleFactor(value, -1)) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.decrease_scale))
            }
            IconButton(
                onClick = { onValueChange(stepScaleFactor(value, 1)) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.increase_scale))
            }
        }
    }
}

private fun stepScaleFactor(current: Float, direction: Int): Float {
    val clamped = current.coerceAtLeast(0.1f)
    val next = if (clamped <= 1f) {
        clamped + (0.1f * direction)
    } else {
        clamped + (1f * direction)
    }
    return normalizeScaleFactor(next)
}

private fun normalizeScaleFactor(value: Float): Float {
    val clamped = value.coerceAtLeast(0.1f)
    return if (clamped < 1f) {
        kotlin.math.round(clamped * 10f) / 10f
    } else {
        clamped.toInt().toFloat()
    }
}

private fun formatScaleFactor(value: Float): String {
    return if (value < 1f) {
        String.format("%.1f", value)
    } else {
        value.toInt().toString()
    }
}

/**
 * 格式化时长显示
 */
private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000.0
    return if (seconds >= 60) {
        val minutes = (seconds / 60).toInt()
        val remainingSeconds = (seconds % 60).toInt()
        "${minutes}分${remainingSeconds}秒"
    } else {
        "%.1f秒".format(seconds)
    }
}
