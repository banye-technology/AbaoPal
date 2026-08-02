package com.withcareer.screenpal_android.ui.component

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.withcareer.screenpal_android.R
import com.withcareer.screenpal_android.data.model.SkillInstructionSet
import com.withcareer.screenpal_android.data.model.InstructionStep
import com.withcareer.screenpal_android.data.room.InstructionSetEntity
import com.withcareer.screenpal_android.data.room.InstructionStepEntity

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

private val gson = Gson()

/**
 * 将 InstructionSetEntity 转换为 SkillInstructionSet
 * 并在转换过程中将坐标归一化
 */
fun convertInstructionSetEntityToSkillInstructionSet(
    entity: InstructionSetEntity,
    steps: List<InstructionStepEntity>,
    defaultDescription: String
): SkillInstructionSet {
    val (recordedWidth, recordedHeight) = parseDeviceInfo(entity.deviceInfo)

    return SkillInstructionSet(
        id = UUID.randomUUID().toString(), // 生成新ID，避免冲突
        name = entity.title,
        description = entity.description.ifBlank { defaultDescription },
        steps = steps.map { step ->
            InstructionStep(
                orderIndex = step.orderIndex,
                actionType = step.actionType,
                actionParams = normalizeActionParams(step.actionParams, recordedWidth, recordedHeight, step.actionType),
                description = step.description,
                delayBefore = step.delayBefore
            )
        },
        createdAt = System.currentTimeMillis()
    )
}

private fun parseDeviceInfo(deviceInfo: String): Pair<Int, Int> {
    if (deviceInfo.isBlank()) return 0 to 0
    val parts = deviceInfo.split("x")
    if (parts.size >= 2) {
        val w = parts[0].trim().toIntOrNull() ?: 0
        val h = parts[1].trim().toIntOrNull() ?: 0
        return w to h
    }
    return 0 to 0
}

private fun normalizeActionParams(paramsJson: String, screenWidth: Int, screenHeight: Int, externalActionType: String? = null): String {
    if (screenWidth <= 0 || screenHeight <= 0) return paramsJson

    return try {
        val mapType = object : TypeToken<MutableMap<String, Any>>() {}.type
        val params: MutableMap<String, Any> = gson.fromJson(paramsJson, mapType) ?: return paramsJson
        val actionType = (params["action_type"] as? String) ?: externalActionType ?: return paramsJson

        // 确保 action_type 在参数中，保持一致性
        if (!params.containsKey("action_type")) {
            params["action_type"] = actionType
        }

        fun safeToFloat(value: Any?): Float {
            return when (value) {
                is Number -> value.toFloat()
                else -> 0f
            }
        }

        when (actionType) {
            "tap", "longPress" -> {
                val element = params["element"]
                if (element is List<*> && element.size >= 2) {
                    val rawX = safeToFloat(element[0])
                    val rawY = safeToFloat(element[1])

                    // 只有当坐标看起来是绝对坐标时才进行归一化 (大于 1.5)
                    if (rawX > 1.5f || rawY > 1.5f) {
                        val relX = rawX / screenWidth.toFloat()
                        val relY = rawY / screenHeight.toFloat()
                        params["element"] = listOf(relX, relY)
                    }
                }
                // Clean up absolute coordinates to avoid confusion during playback
                params.remove("x")
                params.remove("y")
            }
            "swipe", "longPressSwipe" -> {
                val path = params["path"]
                if (path is List<*>) {
                    val normalizedPath = path.mapNotNull { point ->
                        if (point is Map<*, *>) {
                            val x = safeToFloat(point["x"])
                            val y = safeToFloat(point["y"])
                            if (x > 1.5f || y > 1.5f) {
                                mapOf("x" to x / screenWidth.toFloat(), "y" to y / screenHeight.toFloat())
                            } else {
                                mapOf("x" to x, "y" to y)
                            }
                        } else null
                    }
                    if (normalizedPath.isNotEmpty()) {
                        params["path"] = normalizedPath
                    }
                } else {
                    // Handle start/end format
                    val start = params["start"]
                    if (start is List<*> && start.size >= 2) {
                         val x = safeToFloat(start[0])
                         val y = safeToFloat(start[1])
                         if (x > 1.5f || y > 1.5f) {
                             params["start"] = listOf(x / screenWidth.toFloat(), y / screenHeight.toFloat())
                         }
                    }
                    val end = params["end"]
                    if (end is List<*> && end.size >= 2) {
                         val x = safeToFloat(end[0])
                         val y = safeToFloat(end[1])
                         if (x > 1.5f || y > 1.5f) {
                             params["end"] = listOf(x / screenWidth.toFloat(), y / screenHeight.toFloat())
                         }
                    }
                }

                // Clean up absolute coordinates
                params.remove("startX")
                params.remove("startY")
                params.remove("endX")
                params.remove("endY")
            }
        }
        gson.toJson(params)
    } catch (e: Exception) {
        paramsJson
    }
}


/**
 * 按键集管理区域组件
 * 显示技能中的按键集列表，支持添加、编辑、删除
 */
@Composable
fun InstructionSetManagerSection(
    instructionSets: List<SkillInstructionSet>,
    onAdd: () -> Unit,
    onEdit: (SkillInstructionSet) -> Unit,
    onDelete: (SkillInstructionSet) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "按键集管理",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (instructionSets.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${instructionSets.size}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开"
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))

                    // 添加按钮
                    OutlinedButton(
                        onClick = onAdd,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("新建按键集")
                    }

                    // 按键集列表
                    if (instructionSets.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        instructionSets.forEach { set ->
                            InstructionSetCard(
                                instructionSet = set,
                                onEdit = { onEdit(set) },
                                onDelete = { onDelete(set) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    } else {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "暂无按键集，点击上方按钮创建",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单个按键集卡片
 */
@Composable
private fun InstructionSetCard(
    instructionSet: SkillInstructionSet,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = instructionSet.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = instructionSet.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${instructionSet.steps.size} 个步骤",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "编辑",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            shape = RoundedCornerShape(16.dp),
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_instruction_set_title)) },
            text = { Text(stringResource(R.string.delete_instruction_set_message, instructionSet.name)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * 按键集编辑对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructionSetEditDialog(
    instructionSet: SkillInstructionSet?,
    availableRecordings: List<Pair<InstructionSetEntity, List<InstructionStepEntity>>>,
    modifier: Modifier = Modifier,
    initialSteps: List<InstructionStep>? = null,
    onDismiss: () -> Unit,
    onSave: (SkillInstructionSet) -> Unit
) {
    var name by remember { mutableStateOf(instructionSet?.name ?: "") }
    var description by remember { mutableStateOf(instructionSet?.description ?: "") }
    var steps by remember { mutableStateOf(initialSteps ?: instructionSet?.steps ?: emptyList()) }
    var showImportDialog by remember { mutableStateOf(false) }

    val isValid = name.isNotBlank() && description.isNotBlank() && steps.isNotEmpty()

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(max = 400.dp)
            ) {
                Text(
                    text = if (instructionSet == null) {
                        stringResource(R.string.create_instruction_set_title)
                    } else {
                        stringResource(R.string.edit_instruction_set_title)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                // 显示录制提示
                if (!initialSteps.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.imported_steps_message, initialSteps.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 名称输入
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.recording_name_label)) },
                    placeholder = { Text(stringResource(R.string.recording_name_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = name.isBlank()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 描述输入
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.recording_description_label)) },
                    placeholder = { Text(stringResource(R.string.recording_description_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    isError = description.isBlank()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 步骤信息
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.instruction_set_steps_summary, steps.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 只在编辑现有按键集时显示"从录制导入"按钮
                    if (initialSteps == null && instructionSet != null) {
                        TextButton(onClick = { showImportDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.import_from_recording))
                        }
                    }
                }

                if (steps.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // 步骤列表预览（限高）
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ) {
                        LazyColumn(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(steps) { index, step ->
                                Text(
                                    text = "${index + 1}. ${step.description}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val newSet = SkillInstructionSet(
                                id = instructionSet?.id ?: UUID.randomUUID().toString(),
                                name = name,
                                description = description,
                                steps = steps,
                                createdAt = instructionSet?.createdAt ?: System.currentTimeMillis()
                            )
                            onSave(newSet)
                        },
                        enabled = isValid
                    ) {
                        Text(if (instructionSet == null) stringResource(R.string.create) else stringResource(R.string.save))
                    }
                }
            }
        }
    }

    // 导入选择对话框
    val importedDescription = stringResource(R.string.imported_instruction_set_description)
    if (showImportDialog) {
        ImportRecordingDialog(
            availableRecordings = availableRecordings,
            onDismiss = { showImportDialog = false },
            onSelect = { entity, entitySteps ->
                val imported = convertInstructionSetEntityToSkillInstructionSet(
                    entity = entity,
                    steps = entitySteps,
                    defaultDescription = importedDescription
                )
                // 如果名称为空，使用导入的名称
                if (name.isBlank()) {
                    name = imported.name
                }
                // 如果描述为空，使用导入的描述
                if (description.isBlank()) {
                    description = imported.description
                }
                // 导入步骤
                steps = imported.steps
                showImportDialog = false
            }
        )
    }
}

/**
 * 导入录制选择对话框
 */
@Composable
private fun ImportRecordingDialog(
    availableRecordings: List<Pair<InstructionSetEntity, List<InstructionStepEntity>>>,
    onDismiss: () -> Unit,
    onSelect: (InstructionSetEntity, List<InstructionStepEntity>) -> Unit
) {
    AlertDialog(
        shape = RoundedCornerShape(16.dp),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_recording_title)) },
        text = {
            if (availableRecordings.isEmpty()) {
                Text(
                    stringResource(R.string.no_recordings_available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availableRecordings) { (entity, entitySteps) ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(entity, entitySteps)
                                },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Text(
                                    text = entity.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.steps_count, entitySteps.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (entity.deviceInfo.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.device_size_format, entity.deviceInfo),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (availableRecordings.isEmpty()) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            }
        },
        dismissButton = {
            if (availableRecordings.isNotEmpty()) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}
