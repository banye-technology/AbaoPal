package com.withcareer.screenpal_android.ui_v2.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
fun V2ScheduledTaskEditRoute(
    taskId: String?,
    onBack: () -> Unit,
    viewModel: ScheduledTaskViewModel = viewModel()
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val existingTask = remember(tasks, taskId) { tasks.firstOrNull { it.id == taskId } }
    val statusBarPadding = rememberV2StatusBarTopPadding()

    var prompt by remember { mutableStateOf("") }
    var repeatType by remember { mutableStateOf(RepeatType.ONCE) }
    var selectedWeekDays by remember { mutableStateOf<List<Int>>(emptyList()) }
    var dayOfMonth by remember { mutableStateOf(1) }
    var targetTimestamp by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    val timePickerState = key(existingTask?.id ?: "new") {
        rememberTimePickerState(
            initialHour = existingTask?.hour ?: 9,
            initialMinute = existingTask?.minute ?: 0,
            is24Hour = true
        )
    }

    LaunchedEffect(existingTask) {
        if (existingTask != null) {
            prompt = existingTask.prompt
            repeatType = existingTask.repeatType
            selectedWeekDays = existingTask.weekDays
            dayOfMonth = existingTask.dayOfMonth
            targetTimestamp = existingTask.targetTimestamp
        } else {
            prompt = ""
            repeatType = RepeatType.ONCE
            selectedWeekDays = emptyList()
            dayOfMonth = 1
            targetTimestamp = null
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = targetTimestamp ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        targetTimestamp = datePickerState.selectedDateMillis
                        showDatePicker = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.padding(top = statusBarPadding),
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        text = if (existingTask == null) {
                            stringResource(R.string.create_scheduled_task)
                        } else {
                            stringResource(R.string.edit_scheduled_task)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.back),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val base = existingTask ?: ScheduledTask(
                                prompt = prompt.trim(),
                                hour = timePickerState.hour,
                                minute = timePickerState.minute
                            )

                            val updated = base.copy(
                                prompt = prompt.trim(),
                                hour = timePickerState.hour,
                                minute = timePickerState.minute,
                                repeatType = repeatType,
                                weekDays = if (repeatType == RepeatType.WEEKLY) selectedWeekDays.sorted() else emptyList(),
                                dayOfMonth = if (repeatType == RepeatType.MONTHLY) dayOfMonth else 1,
                                targetTimestamp = if (repeatType == RepeatType.ONCE) targetTimestamp else null,
                                isEnabled = base.isEnabled,
                                createdAt = base.createdAt
                            )

                            if (existingTask == null) {
                                viewModel.addScheduledTask(updated)
                            } else {
                                viewModel.updateScheduledTask(updated)
                            }
                            onBack()
                        },
                        enabled = prompt.trim().isNotEmpty()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save))
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
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TimePicker(state = timePickerState)
            }

            HorizontalDivider()

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.task_prompt)) },
                maxLines = 3
            )

            Column {
                Text(stringResource(R.string.repeat_type), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RepeatType.values().forEach { type ->
                        FilterChip(
                            selected = repeatType == type,
                            onClick = { repeatType = type },
                            label = {
                                Text(
                                    when (type) {
                                        RepeatType.ONCE -> stringResource(R.string.repeat_once)
                                        RepeatType.DAILY -> stringResource(R.string.repeat_daily)
                                        RepeatType.WEEKLY -> stringResource(R.string.repeat_weekly)
                                        RepeatType.WORKDAYS -> stringResource(R.string.repeat_workdays)
                                        RepeatType.MONTHLY -> stringResource(R.string.repeat_monthly)
                                    }
                                )
                            }
                        )
                    }
                }
            }

            if (repeatType == RepeatType.ONCE) {
                val dateText = if (targetTimestamp != null) {
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(targetTimestamp!!))
                } else {
                    stringResource(R.string.select_date_default_today)
                }
                OutlinedCard(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null)
                        Spacer(modifier = Modifier.size(16.dp))
                        Text(dateText)
                    }
                }
            } else if (repeatType == RepeatType.WEEKLY) {
                Column {
                    Text(stringResource(R.string.select_repeat_weekdays), style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val weekDays = listOf(2, 3, 4, 5, 6, 7, 1)
                        val weekNames = listOf(
                            stringResource(R.string.week_mon),
                            stringResource(R.string.week_tue),
                            stringResource(R.string.week_wed),
                            stringResource(R.string.week_thu),
                            stringResource(R.string.week_fri),
                            stringResource(R.string.week_sat),
                            stringResource(R.string.week_sun)
                        )

                        weekDays.zip(weekNames).forEach { (day, name) ->
                            val isSelected = selectedWeekDays.contains(day)
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    )
                                    .clickable {
                                        selectedWeekDays = if (isSelected) {
                                            selectedWeekDays - day
                                        } else {
                                            selectedWeekDays + day
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = name,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            } else if (repeatType == RepeatType.MONTHLY) {
                Column {
                    Text(stringResource(R.string.select_day_of_month), style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (1..31).forEach { day ->
                            FilterChip(
                                selected = dayOfMonth == day,
                                onClick = { dayOfMonth = day },
                                label = { Text("${day}${stringResource(R.string.day_suffix)}") }
                            )
                        }
                    }
                }
            }
        }
    }
}
