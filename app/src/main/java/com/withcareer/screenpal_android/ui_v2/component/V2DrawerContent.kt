package com.withcareer.screenpal_android.ui_v2.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.withcareer.screenpal_android.ui_v2.util.rememberV2ActionCardColor
import com.withcareer.screenpal_android.ui_v2.util.rememberV2CardColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.withcareer.screenpal_android.R
import com.withcareer.screenpal_android.data.preference.Task
import com.withcareer.screenpal_android.ui_v2.util.rememberV2StatusBarTopPadding

@Composable
fun V2DrawerContent(
    historyTasks: List<Task>,
    onNewChat: () -> Unit,
    onHistoryClick: (Task) -> Unit,
    onDeleteTasks: (Set<String>) -> Unit,
    onRenameTask: (String, String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenScheduledTasks: () -> Unit,
    onOpenAbilityCenter: () -> Unit,
    onProfileClick: () -> Unit
) {
    val statusBarPadding = rememberV2StatusBarTopPadding()
    val actionCardColor = rememberV2ActionCardColor()
    val cardColor = rememberV2CardColor()
    val visibleTasks = historyTasks.take(50)
    val visibleIds = visibleTasks.map { it.id }.toSet()

    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameTaskId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var menuTaskId by remember { mutableStateOf<String?>(null) }

    if (showDeleteDialog) {
        V2AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = "删除话题",
            text = "确定删除选中的 ${selectedIds.size} 个话题吗？",
            confirmText = "删除",
            onConfirm = {
                onDeleteTasks(selectedIds)
                selectedIds = emptySet()
                selectionMode = false
                showDeleteDialog = false
            },
            dismissText = "取消",
            onDismiss = { showDeleteDialog = false },
            isError = true
        )
    }

    if (showRenameDialog) {
        val targetId = renameTaskId
        V2AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("修改标题") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("输入新标题") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (targetId != null) {
                            onRenameTask(targetId, renameText)
                        }
                        selectedIds = emptySet()
                        selectionMode = false
                        showRenameDialog = false
                    },
                    enabled = renameText.trim().isNotBlank() && targetId != null
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("取消") }
            }
        )
    }

    ModalDrawerSheet(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.82f),
        drawerContainerColor = MaterialTheme.colorScheme.background,
        drawerTonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(statusBarPadding))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.logo_remove),
                        contentDescription = "Logo",
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(4.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "阿宝Pal",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onOpenSearch) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "搜索"
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (selectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            cardColor
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = {
                            selectionMode = false
                            selectedIds = emptySet()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "取消选择",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "已选 ${selectedIds.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            selectedIds = if (selectedIds.containsAll(visibleIds) && visibleIds.isNotEmpty()) {
                                emptySet()
                            } else {
                                visibleIds
                            }
                            if (selectedIds.isEmpty()) selectionMode = false
                        }
                    ) {
                        Text(if (selectedIds.containsAll(visibleIds) && visibleIds.isNotEmpty()) "取消全选" else "全选")
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        enabled = selectedIds.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = if (selectedIds.isNotEmpty()) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            Button(
                onClick = onNewChat,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("新建话题")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "工作区",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
            Spacer(modifier = Modifier.height(8.dp))

            V2DrawerActionItem(
                title = "定时任务",
                icon = Icons.Default.Schedule,
                containerColor = actionCardColor,
                onClick = { onOpenScheduledTasks() }
            )
            Spacer(modifier = Modifier.height(8.dp))
            V2DrawerActionItem(
                title = "我的技能",
                icon = Icons.Default.AutoAwesome,
                containerColor = actionCardColor,
                onClick = { onOpenAbilityCenter() }
            )
            Spacer(modifier = Modifier.height(8.dp))

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "执行历史",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(visibleTasks, key = { it.id }) { task ->
                    V2DrawerHistoryItem(
                        title = task.prompt,
                        stepCount = task.steps.size,
                        selected = selectedIds.contains(task.id),
                        showMenu = menuTaskId == task.id,
                        onClick = {
                            if (selectionMode) {
                                val next = if (selectedIds.contains(task.id)) selectedIds - task.id else selectedIds + task.id
                                selectedIds = next
                                if (next.isEmpty()) selectionMode = false
                            } else {
                                onHistoryClick(task)
                            }
                        },
                        onLongPress = {
                            if (selectionMode) {
                                val next = if (selectedIds.contains(task.id)) selectedIds - task.id else selectedIds + task.id
                                selectedIds = next
                                if (next.isEmpty()) selectionMode = false
                            } else {
                                menuTaskId = task.id
                            }
                        },
                        onDismissMenu = { menuTaskId = null },
                        onRequestDelete = {
                            menuTaskId = null
                            selectionMode = false
                            selectedIds = setOf(task.id)
                            showDeleteDialog = true
                        },
                        onRequestRename = {
                            menuTaskId = null
                            selectionMode = false
                            selectedIds = emptySet()
                            renameTaskId = task.id
                            renameText = task.prompt
                            showRenameDialog = true
                        },
                        onEnterBatchDelete = {
                            menuTaskId = null
                            selectionMode = true
                            selectedIds = setOf(task.id)
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun V2DrawerActionItem(
    title: String,
    icon: ImageVector,
    containerColor: Color,
    onClick: () -> Unit
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun V2DrawerHistoryItem(
    title: String,
    stepCount: Int,
    selected: Boolean,
    showMenu: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDismissMenu: () -> Unit,
    onRequestDelete: () -> Unit,
    onRequestRename: () -> Unit,
    onEnterBatchDelete: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        val highlight = selected || showMenu
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (highlight) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    else Color.Transparent
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongPress
                )
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${stepCount}步",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        V2DropdownMenu(
            expanded = showMenu,
            onDismissRequest = onDismissMenu
        ) {
            V2DropdownMenuItem(
                text = "删除",
                onClick = onRequestDelete,
                leadingIcon = Icons.Default.Delete,
                leadingIconTint = MaterialTheme.colorScheme.error
            )
            V2DropdownMenuItem(
                text = "编辑标题",
                onClick = onRequestRename,
                leadingIcon = Icons.Default.Edit
            )
            V2DropdownMenuItem(
                text = "批量删除",
                onClick = onEnterBatchDelete,
                leadingIcon = Icons.Default.Delete
            )
        }
    }
}
