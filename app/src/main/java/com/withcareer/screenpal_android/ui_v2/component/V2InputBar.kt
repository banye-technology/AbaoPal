@file:JvmName("V2InputBarKt")

package com.withcareer.screenpal_android.ui_v2.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.withcareer.screenpal_android.R
import com.withcareer.screenpal_android.ui.icons.AppIcons

@Composable
fun V2InputBar(
    modifier: Modifier = Modifier,
    inputText: String,
    isSending: Boolean,
    isListening: Boolean,
    isProcessingVoice: Boolean,
    voiceError: String?,
    isTaskRunning: Boolean,
    thinkingContent: String,
    applyNavigationBarsPaddingWhenImeHidden: Boolean = true,
    bottomPadding: Dp = 16.dp,
    onInputChange: (String) -> Unit,
    onInputFocus: () -> Unit,
    onVoiceStart: () -> Unit,
    onVoiceEnd: () -> Unit,
    onStopTask: () -> Unit,
    onSend: () -> Unit
) {
    var showStopDialog by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var lastShowEditor by remember { mutableStateOf(false) }
    var lastEditorClosedByIme by remember { mutableStateOf(false) }
    // 新增：用于标记是否是因为键盘收起而关闭的，从而决定退出动画
    var isClosedByImeAction by remember { mutableStateOf(false) }
    var isInputFocused by remember { mutableStateOf(false) }
    var visualLineCount by remember { mutableStateOf(1) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val inputFocusRequester = remember { FocusRequester() }
    val editorFocusRequester = remember { FocusRequester() }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    var lastImeBottom by remember { mutableStateOf(0) }
    val imeBottom = WindowInsets.ime.getBottom(density)

    val currentBottomPadding by animateDpAsState(
        targetValue = if (imeBottom > 0) 0.dp else bottomPadding,
        label = "BottomPaddingAnimation"
    )

    val expanded = isInputFocused || inputText.isNotBlank()

    LaunchedEffect(imeBottom) {
        val prevImeBottom = lastImeBottom
        if (prevImeBottom > 0 && imeBottom == 0 && isInputFocused) {
            withFrameNanos { }
            focusManager.clearFocus(force = true)
        }
        if (showEditor && prevImeBottom > 0 && imeBottom == 0) {
            withFrameNanos { }
            lastEditorClosedByIme = true
            isClosedByImeAction = true // 标记为键盘导致的关闭
            showEditor = false
            focusManager.clearFocus(force = true)
        }
        lastImeBottom = imeBottom
    }

    LaunchedEffect(isInputFocused) {
        if (isInputFocused) {
            withFrameNanos { }
            keyboardController?.show()
        }
    }

    if (showStopDialog) {
        V2AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = stringResource(R.string.stop_task),
            text = stringResource(R.string.confirm_stop_task),
            confirmText = stringResource(R.string.stop),
            onConfirm = {
                onStopTask()
                showStopDialog = false
            },
            dismissText = stringResource(R.string.cancel),
            onDismiss = { showStopDialog = false },
            isError = true
        )
    }


    LaunchedEffect(showEditor, isTaskRunning) {
        if (isTaskRunning) {
            lastShowEditor = showEditor
            return@LaunchedEffect
        }
        if (showEditor) {
            isClosedByImeAction = false // 重置状态
            lastEditorClosedByIme = false
            editorFocusRequester.requestFocus()
            keyboardController?.show()
        } else if (lastShowEditor) {
            if (!lastEditorClosedByIme) {
                withFrameNanos { }
                inputFocusRequester.requestFocus()
                keyboardController?.show()
            }
            lastEditorClosedByIme = false
        }
        lastShowEditor = showEditor
    }

    val canSend = inputText.isNotBlank() && !isSending && !isProcessingVoice && !isListening
    val hintText = when {
        isListening -> stringResource(R.string.listening)
        isProcessingVoice -> stringResource(R.string.recognizing)
        else -> stringResource(R.string.input_hint)
    }

    val shape = RoundedCornerShape(18.dp)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val containerColor = Color.Transparent

    val onVoiceClick = {
        if (isListening) onVoiceEnd() else onVoiceStart()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (applyNavigationBarsPaddingWhenImeHidden && imeBottom == 0) Modifier.navigationBarsPadding()
                else Modifier
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = currentBottomPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
        if (!voiceError.isNullOrBlank()) {
            Text(
                text = voiceError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (isTaskRunning) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                        .heightIn(min = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.task_running),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { showStopDialog = true }) {
                            Icon(
                                imageVector = AppIcons.Pause,
                                contentDescription = stringResource(R.string.stop_task),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Text(
                        text = thinkingContent.ifBlank { stringResource(R.string.analyzing) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            if (!showEditor) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .animateContentSize()
                            .border(width = 1.dp, color = borderColor, shape = shape),
                        shape = shape,
                        color = containerColor,
                        tonalElevation = 0.dp
                    ) {
                BasicTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .focusRequester(inputFocusRequester)
                        .onFocusChanged { focusState ->
                            isInputFocused = focusState.isFocused
                            if (focusState.isFocused) onInputFocus()
                        }
                        .padding(horizontal = if (expanded) 12.dp else 10.dp, vertical = if (expanded) 8.dp else 4.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = !expanded,
                    maxLines = if (expanded) 10 else 1,
                    onTextLayout = { layoutResult: TextLayoutResult ->
                        visualLineCount = layoutResult.lineCount.coerceAtLeast(1)
                    },
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = if (expanded) ImeAction.Default else ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (!expanded && canSend) onSend()
                        }
                    ),
                    decorationBox = { innerTextField ->
                        if (!expanded) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = onVoiceClick,
                                    enabled = !isSending && !isProcessingVoice
                                ) {
                                    if (isProcessingVoice) {
                                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(
                                            imageVector = if (isListening) AppIcons.Stop else AppIcons.Mic,
                                            contentDescription = stringResource(R.string.voice_input),
                                            tint = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                Box(modifier = Modifier.weight(1f)) {
                                    if (inputText.isBlank()) {
                                        Text(
                                            text = hintText,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.Center),
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                        )
                                    }
                                    innerTextField()
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                IconButton(
                                    onClick = onSend,
                                    enabled = canSend
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Send,
                                        contentDescription = stringResource(R.string.send_task),
                                        tint = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 44.dp)
                                ) {
                                    val trailingSlot = 38.dp
                                    Box(modifier = Modifier.fillMaxWidth().padding(end = trailingSlot)) {
                                        if (inputText.isBlank()) {
                                            Text(
                                                text = hintText,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                            )
                                        }
                                        innerTextField()
                                    }

                                    if (visualLineCount > 3 && inputText.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                showEditor = true
                                            },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = AppIcons.OpenInFull,
                                                contentDescription = stringResource(R.string.expand),
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    IconButton(
                                        onClick = onVoiceClick,
                                        enabled = !isSending && !isProcessingVoice
                                    ) {
                                        if (isProcessingVoice) {
                                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(
                                                imageVector = if (isListening) AppIcons.Stop else AppIcons.Mic,
                                                contentDescription = stringResource(R.string.voice_input),
                                                tint = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = onSend,
                                        enabled = canSend
                                    ) {
                                        Icon(
                                            imageVector = AppIcons.Send,
                                            contentDescription = stringResource(R.string.send_task),
                                            tint = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
            }
            }
        }
        }
        }

        AnimatedVisibility(
            visible = showEditor && !isTaskRunning,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .zIndex(10f),
            enter = expandVertically(
                expandFrom = Alignment.Bottom,
                animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(durationMillis = 150)),
            exit = if (isClosedByImeAction) {
                fadeOut(animationSpec = tween(durationMillis = 50))
            } else {
                shrinkVertically(
                    shrinkTowards = Alignment.Bottom,
                    animationSpec = tween(durationMillis = 250, easing = FastOutLinearInEasing)
                ) + fadeOut(animationSpec = tween(durationMillis = 200))
            }
        ) {
            val editorMaxHeight = configuration.screenHeightDp.dp * 0.52f
            val editorMinHeight = configuration.screenHeightDp.dp * 0.34f
            val isDark = isSystemInDarkTheme()
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = editorMinHeight, max = editorMaxHeight),
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp, // 无论亮暗都给一点 tonal elevation，增加层次
                shadowElevation = 0.dp, // 去掉阴影
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isDark)
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    else
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f) // 亮色模式也给一个极淡的边框
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { onInputChange("") },
                            enabled = inputText.isNotBlank()
                        ) {
                            Text(text = stringResource(R.string.clear))
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { showEditor = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.finish),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    TextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .focusRequester(editorFocusRequester),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        placeholder = { Text(text = stringResource(R.string.input_hint)) },
                        minLines = 6,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            errorIndicatorColor = Color.Transparent
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = onVoiceClick,
                            enabled = !isSending && !isProcessingVoice,
                            modifier = Modifier.size(40.dp)
                        ) {
                            if (isProcessingVoice) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = if (isListening) AppIcons.Stop else AppIcons.Mic,
                                    contentDescription = stringResource(R.string.voice_input),
                                    modifier = Modifier.size(20.dp),
                                    tint = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = onSend,
                            enabled = canSend,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.Send,
                                contentDescription = stringResource(R.string.send_task),
                                modifier = Modifier.size(20.dp),
                                tint = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
