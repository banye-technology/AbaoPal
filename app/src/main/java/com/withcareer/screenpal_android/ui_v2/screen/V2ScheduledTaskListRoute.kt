package com.withcareer.screenpal_android.ui_v2.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.withcareer.screenpal_android.ui_v2.component.V2AlertDialog
import com.withcareer.screenpal_android.ui_v2.component.V2EmptyState
import com.withcareer.screenpal_android.ui_v2.component.V2ScreenTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.withcareer.screenpal_android.R
import com.withcareer.screenpal_android.data.preference.RepeatType
import com.withcareer.screenpal_android.data.preference.ScheduledTask
import com.withcareer.screenpal_android.ui.viewmodel.ScheduledTaskViewModel
import com.withcareer.screenpal_android.ui_v2.util.rememberV2StatusBarTopPadding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V2ScheduledTaskListRoute(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: ScheduledTaskViewModel = viewModel()
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val statusBarPadding = rememberV2StatusBarTopPadding()

    Scaffold(
        topBar = {
            V2ScreenTopBar(
                title = stringResource(R.string.scheduled_tasks_title),
                onBack = onBack,
                actions = {
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Default.Add, contentDescription = "添加任务")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (tasks.isEmpty()) {
            V2EmptyState(
                text = "暂无定时任务",
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    V2ScheduledTaskItem(
                        task = task,
                        onClick = { onEdit(task.id) },
                        onToggle = { enabled -> viewModel.toggleTaskEnabled(task, enabled) },
                        onDelete = { viewModel.deleteScheduledTask(task.id) },
                        onExecute = { viewModel.executeTaskNow(task) }
                    )
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun V2ScheduledTaskItem(
    task: ScheduledTask,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onExecute: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        V2AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = stringResource(R.string.confirm_delete),
            text = "确定要删除这个定时任务吗？",
            confirmText = stringResource(R.string.delete),
            onConfirm = {
                onDelete()
                showDeleteDialog = false
            },
            dismissText = stringResource(R.string.cancel),
            onDismiss = { showDeleteDialog = false },
            isError = true
        )
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = String.format(Locale.getDefault(), "%02d:%02d", task.hour, task.minute),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = task.prompt,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = getRepeatDescription(task),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Switch(checked = task.isEnabled, onCheckedChange = onToggle)
                Row {
                    IconButton(onClick = onExecute) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.execute_now),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun getRepeatDescription(task: ScheduledTask): String {
    return when (task.repeatType) {
        RepeatType.ONCE -> {
            if (task.targetTimestamp != null) {
                val date = Date(task.targetTimestamp!!)
                val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                stringResource(R.string.repeat_desc_once_with_date, format.format(date))
            } else {
                stringResource(R.string.repeat_once)
            }
        }
        RepeatType.DAILY -> stringResource(R.string.repeat_daily)
        RepeatType.WEEKLY -> {
            val sun = stringResource(R.string.week_sun_full)
            val mon = stringResource(R.string.week_mon_full)
            val tue = stringResource(R.string.week_tue_full)
            val wed = stringResource(R.string.week_wed_full)
            val thu = stringResource(R.string.week_thu_full)
            val fri = stringResource(R.string.week_fri_full)
            val sat = stringResource(R.string.week_sat_full)

            val days = task.weekDays.sorted().joinToString(" ") {
                when (it) {
                    1 -> sun
                    2 -> mon
                    3 -> tue
                    4 -> wed
                    5 -> thu
                    6 -> fri
                    7 -> sat
                    else -> ""
                }
            }
            stringResource(R.string.repeat_desc_weekly, days)
        }
        RepeatType.WORKDAYS -> stringResource(R.string.repeat_desc_workdays_detail)
        RepeatType.MONTHLY -> stringResource(R.string.repeat_desc_monthly, task.dayOfMonth)
    }
}
