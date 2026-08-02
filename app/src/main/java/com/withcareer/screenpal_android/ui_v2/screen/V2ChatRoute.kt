package com.withcareer.screenpal_android.ui_v2.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.withcareer.screenpal_android.ui_v2.component.V2AppBar
import com.withcareer.screenpal_android.ui_v2.component.V2DrawerContent
import com.withcareer.screenpal_android.ui_v2.component.V2Greeting
import com.withcareer.screenpal_android.ui_v2.component.V2InputBar
import com.withcareer.screenpal_android.ui_v2.component.V2MessageList
import com.withcareer.screenpal_android.ui_v2.viewmodel.V2ChatViewModel
import com.withcareer.screenpal_android.util.ToastUtils
import kotlinx.coroutines.launch


@Composable
fun V2ChatRoute(
    onOpenSettings: () -> Unit,
    onOpenScheduledTasks: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAbilityCenter: () -> Unit,
    prefillText: String? = null,
    onConsumePrefillText: () -> Unit = {},
    viewModel: V2ChatViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(prefillText) {
        val text = prefillText?.trim().orEmpty()
        if (text.isNotBlank()) {
            viewModel.updateInputText(text)
            onConsumePrefillText()
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            V2DrawerContent(
                historyTasks = uiState.tasks,
                onNewChat = {
                    viewModel.newChat()
                    scope.launch { drawerState.close() }
                },
                onHistoryClick = { task ->
                    viewModel.replayTask(task)
                    scope.launch { drawerState.close() }
                },
                onDeleteTasks = { taskIds ->
                    viewModel.deleteTasks(taskIds)
                },
                onRenameTask = { taskId, title ->
                    viewModel.updateTaskTitle(taskId, title)
                },
                onOpenSearch = {
                    onOpenSearch()
                    scope.launch { drawerState.close() }
                },
                onOpenScheduledTasks = {
                    onOpenScheduledTasks()
                    scope.launch { drawerState.close() }
                },
                onOpenAbilityCenter = {
                    onOpenAbilityCenter()
                    scope.launch { drawerState.close() }
                },
                onProfileClick = { ToastUtils.show(context, "建设中") }
            )
        }
    ) {
        val density = LocalDensity.current
        val imeBottom = WindowInsets.ime.getBottom(density)
        val isImeVisible = imeBottom > 0

        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    V2AppBar(
                        isTtsEnabled = uiState.isTtsEnabled,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onToggleTts = viewModel::toggleTtsEnabled,
                        onOpenSettings = onOpenSettings
                    )
                },
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0)
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    if (uiState.messages.isEmpty()) {
                        V2Greeting(
                            quickCommands = uiState.quickCommands,
                            onSampleClick = { sample ->
                                viewModel.newChat()
                                viewModel.updateInputText(sample)
                            },
                            onRunQuickCommand = viewModel::runQuickCommand
                        )
                    } else {
                        V2MessageList(
                            modifier = Modifier.fillMaxSize(),
                            messages = uiState.messages,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 100.dp),
                            onActionClick = viewModel::handleInteractiveAction,
                            onReExecute = { message ->
                                val started = viewModel.reExecuteTask(message.content)
                                if (!started) {
                                    ToastUtils.show(context, "暂无可重新执行的任务")
                                }
                            }
                        )
                    }
                }
            }

            // Input Bar - Placed outside Scaffold to ensure z-index is higher than TopBar
            Surface(
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .fillMaxWidth()
                    .imePadding(),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    V2InputBar(
                        modifier = Modifier.fillMaxWidth(),
                        inputText = uiState.inputText,
                        isSending = uiState.isSending,
                        isListening = uiState.isListening,
                        isProcessingVoice = uiState.isProcessingVoice,
                        voiceError = uiState.voiceError,
                        isTaskRunning = uiState.isTaskRunning,
                        thinkingContent = uiState.thinkingContent,
                        applyNavigationBarsPaddingWhenImeHidden = false,
                        bottomPadding = 0.dp,
                        onInputChange = viewModel::updateInputText,
                        onInputFocus = viewModel::interruptVoiceInput,
                        onVoiceStart = viewModel::startVoiceInput,
                        onVoiceEnd = viewModel::stopVoiceInput,
                        onStopTask = viewModel::stopTask,
                        onSend = viewModel::send
                    )

                    AnimatedVisibility(visible = !isImeVisible) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "内容由AI生成",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            )
                        }
                    }
                    Spacer(
                        modifier = Modifier.windowInsetsBottomHeight(
                            WindowInsets.navigationBars.exclude(WindowInsets.ime)
                        )
                    )
                }
            }
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
}
