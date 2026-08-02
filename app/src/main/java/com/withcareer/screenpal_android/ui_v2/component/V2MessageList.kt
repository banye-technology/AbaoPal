package com.withcareer.screenpal_android.ui_v2.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.OutlinedCard
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.withcareer.screenpal_android.core.model.ChatMessage
import com.withcareer.screenpal_android.core.model.MessageRole
import kotlinx.coroutines.launch

@Composable
fun V2MessageList(
    modifier: Modifier,
    messages: List<ChatMessage>,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onActionClick: (actionId: String, payload: String?) -> Unit,
    onReExecute: (ChatMessage) -> Unit = {}
) {
    var pendingReExecute by remember { mutableStateOf<ChatMessage?>(null) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 当滚动到第3个条目之后显示回到顶部按钮
    val showScrollToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 2
        }
    }

    LaunchedEffect(messages.lastOrNull()?.id) {
        val lastIndex = messages.lastIndex
        if (lastIndex >= 0) {
            listState.animateScrollToItem(lastIndex)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val actions = msg.interactiveActions.orEmpty()
                val summaryAction = actions.firstOrNull { it.actionId == "ADD_TO_QUICK_COMMAND" }
                if (summaryAction != null) {
                    SummaryCard(
                        summary = msg.content,
                        buttonLabel = summaryAction.label,
                        onClick = { onActionClick(summaryAction.actionId, summaryAction.payload) }
                    )
                } else {
                    val isUser = msg.role == MessageRole.USER
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isUser) {
                            IconButton(
                                onClick = { pendingReExecute = msg },
                                modifier = Modifier.padding(end = 4.dp).size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "重新执行",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        OutlinedCard(
                            colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
                            shape = RoundedCornerShape(14.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text(
                                    text = msg.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (actions.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    actions.forEach { action ->
                                        Button(onClick = { onActionClick(action.actionId, action.payload) }) {
                                            Text(action.label)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 回到顶部按钮 - 右下角，输入框上方
        if (showScrollToTop) {
            FilledIconButton(
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(0)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "回到顶部",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    if (pendingReExecute != null) {
        V2AlertDialog(
            onDismissRequest = { pendingReExecute = null },
            title = "重新执行任务",
            text = "是否重新规划并执行该任务？",
            confirmText = "确认",
            onConfirm = {
                pendingReExecute?.let { onReExecute(it) }
                pendingReExecute = null
            },
            dismissText = "取消",
            onDismiss = { pendingReExecute = null }
        )
    }
}

@Composable
private fun SummaryCard(
    summary: String,
    buttonLabel: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = "任务总结",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onClick) {
                Text(buttonLabel)
            }
        }
    }
}
