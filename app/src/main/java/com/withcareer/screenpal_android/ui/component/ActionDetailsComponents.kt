package com.withcareer.screenpal_android.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import com.withcareer.screenpal_android.recording.EditableAction
import com.withcareer.screenpal_android.recording.RecordedAction

import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

import android.util.Log

/**
 * 帮助提示图标，点击后显示浮窗
 */
@Composable
fun HelpTooltip(
    text: String,
    modifier: Modifier = Modifier
) {
    var showPopup by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
            contentDescription = "帮助",
            modifier = Modifier
                .size(16.dp)
                .clickable { showPopup = true },
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )

        if (showPopup) {
            Popup(
                alignment = Alignment.TopCenter,
                onDismissRequest = { showPopup = false },
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .padding(top = 20.dp)
                        .widthIn(max = 280.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = text,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * 可拖拽的操作卡片（包含上下移动按钮）
 * @deprecated 已不再使用上下移动按钮，直接使用 EnhancedActionCard 代替
 */
@Deprecated("Use EnhancedActionCard directly instead", ReplaceWith("EnhancedActionCard"))
@Composable
fun DraggableActionCard(
    action: EditableAction,
    index: Int,
    totalCount: Int,
    totalDuration: Long,
    screenWidth: Int,
    screenHeight: Int,
    currentInterval: Float,  // 新增：当前间隔
    maxInterval: Float? = null,  // 新增：最大允许间隔
    showInsertControls: Boolean = false,
    onInsertBefore: (() -> Unit)? = null,
    onInsertAfter: (() -> Unit)? = null,
    onToggleExpand: () -> Unit,
    onToggleSelect: () -> Unit,
    onProgressChange: (Float) -> Unit,
    onIntervalChange: (Float) -> Unit,  // 新增：间隔变化回调
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
    intervalScaleFactor: Float = 2f,
    onIntervalScaleFactorChange: (Float) -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 移动按钮列
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 向上移动按钮
            IconButton(
                onClick = onMoveUp,
                enabled = index > 0,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.ArrowUpward,
                    contentDescription = "向上移动",
                    modifier = Modifier.size(20.dp),
                    tint = if (index > 0)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }

            // 向下移动按钮
            IconButton(
                onClick = onMoveDown,
                enabled = index < totalCount - 1,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.ArrowDownward,
                    contentDescription = "向下移动",
                    modifier = Modifier.size(20.dp),
                    tint = if (index < totalCount - 1)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }
        }

        // 操作卡片
        EnhancedActionCard(
            action = action,
            index = index,
            totalDuration = totalDuration,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            currentInterval = currentInterval,  // 传递间隔
            maxInterval = maxInterval,  // 传递最大间隔
            intervalScaleFactor = intervalScaleFactor,
            onIntervalScaleFactorChange = onIntervalScaleFactorChange,
            showInsertControls = showInsertControls,
            onInsertBefore = onInsertBefore,
            onInsertAfter = onInsertAfter,
            onToggleExpand = onToggleExpand,
            onToggleSelect = onToggleSelect,
            onProgressChange = onProgressChange,
            onIntervalChange = onIntervalChange,  // 传递间隔回调
            onDelete = onDelete,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 增强的操作卡片
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedActionCard(
    action: EditableAction,
    index: Int,
    totalDuration: Long,
    screenWidth: Int,
    screenHeight: Int,
    currentInterval: Float,  // 新增：当前间隔
    maxInterval: Float? = null,  // 新增：最大允许间隔
    intervalScaleFactor: Float = 2f,
    onIntervalScaleFactorChange: (Float) -> Unit = {},
    showInsertControls: Boolean = false,
    onInsertBefore: (() -> Unit)? = null,
    onInsertAfter: (() -> Unit)? = null,
    onToggleExpand: () -> Unit,
    onToggleSelect: () -> Unit,
    onProgressChange: (Float) -> Unit,
    onIntervalChange: (Float) -> Unit,  // 新增：间隔变化回调
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onToggleExpand() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // 头部：序号、图标、类型、时间
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (showInsertControls) {
                        var insertMenuExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.width(40.dp)) {
                            IconButton(
                                onClick = { insertMenuExpanded = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "插入",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = insertMenuExpanded,
                                onDismissRequest = { insertMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("前插") },
                                    onClick = {
                                        insertMenuExpanded = false
                                        onInsertBefore?.invoke()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("后插") },
                                    onClick = {
                                        insertMenuExpanded = false
                                        onInsertAfter?.invoke()
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    } else {
                        // 选择框
                        Checkbox(
                            checked = action.isSelected,
                            onCheckedChange = { onToggleSelect() },
                            modifier = Modifier
                                .size(18.dp)
                                .scale(0.9f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // 序号
                    Surface(
                        shape = CircleShape,
                        color = getActionColor(action.action.actionType).copy(alpha = 0.2f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = getActionColor(action.action.actionType)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = getActionIcon(action.action.actionType),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = getActionColor(action.action.actionType)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = getActionTypeText(action.action.actionType),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = formatTimestamp((action.action.progress * totalDuration).toLong()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                // 展开/收起图标
                Icon(
                    imageVector = if (action.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (action.isExpanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 简要信息（始终显示）
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatActionBrief(action.action, screenWidth, screenHeight),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 详细信息（可展开）
            AnimatedVisibility(
                visible = action.isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))

                    // 根据操作类型显示详情
                    ActionDetails(action.action, screenWidth, screenHeight)

                    Spacer(modifier = Modifier.height(12.dp))

                    // 执行位置/间隔调整
                    ProgressEditor(
                        currentProgress = action.action.progress,
                        totalDuration = totalDuration,
                        currentInterval = currentInterval,  // 传递间隔
                        maxInterval = maxInterval,  // 传递最大间隔
                        intervalScaleFactor = intervalScaleFactor,
                        onIntervalScaleFactorChange = onIntervalScaleFactorChange,
                        onProgressChange = onProgressChange,
                        onIntervalChange = onIntervalChange  // 传递间隔回调
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    var showDeleteConfirm by remember { mutableStateOf(false) }

                    if (showDeleteConfirm) {
                        AlertDialog(
                            shape = RoundedCornerShape(16.dp),
                            onDismissRequest = { showDeleteConfirm = false },
                            title = { Text("确认删除") },
                            text = { Text("确定要删除该操作吗？") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showDeleteConfirm = false
                                        onDelete()
                                    }
                                ) {
                                    Text("删除", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteConfirm = false }) {
                                    Text("取消")
                                }
                            }
                        )
                    }

                    // 删除按钮
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("删除此操作")
                    }
                }
            }
        }
    }
}

/**
 * 操作详情
 */
@Composable
fun ActionDetails(
    action: RecordedAction,
    screenWidth: Int,
    screenHeight: Int
) {
    Log.d("RecordingPreview", "Rendering action details")
    when (action.actionType) {
        "tap" -> TapDetails(action.params, screenWidth, screenHeight)
        "multiTap" -> MultiTapDetails(action.params, screenWidth, screenHeight)
        "longPress" -> LongPressDetails(action.params, screenWidth, screenHeight)
        "swipe" -> SwipeDetails(action.params, screenWidth, screenHeight)
        "longPressSwipe" -> LongPressSwipeDetails(action.params, screenWidth, screenHeight)
        "type" -> TypeDetails(action.params)
        "scroll" -> ScrollDetails(action.params)
        else -> GenericDetails(action.params)
    }
}

private fun parsePoint(
    value: Any?,
    screenWidth: Int,
    screenHeight: Int
): Pair<Int, Int>? {
    return when (value) {
        is Map<*, *> -> {
            val x = (value["x"] as? Number)?.toFloat()
            val y = (value["y"] as? Number)?.toFloat()
            if (x != null && y != null) {
                // Check for normalized coordinates (0.0 - 1.5 range heuristic)
                if (x <= 1.5f && y <= 1.5f && screenWidth > 0 && screenHeight > 0) {
                    (x * screenWidth).toInt() to (y * screenHeight).toInt()
                } else {
                    x.toInt() to y.toInt()
                }
            } else null
        }
        is List<*> -> {
            val relX = (value.getOrNull(0) as? Number)?.toFloat()
            val relY = (value.getOrNull(1) as? Number)?.toFloat()
            if (relX != null && relY != null && screenWidth > 0 && screenHeight > 0) {
                (relX * screenWidth).toInt() to (relY * screenHeight).toInt()
            } else {
                null
            }
        }
        else -> null
    }
}

private fun resolvePoint(
    params: Map<String, Any>,
    screenWidth: Int,
    screenHeight: Int
): Pair<Int, Int> {
    Log.d("RecordingPreview", "Resolving recorded point")
    val directX = (params["x"] as? Number)?.toFloat()
    val directY = (params["y"] as? Number)?.toFloat()
    if (directX != null && directY != null) {
        // Check for normalized coordinates (0.0 - 1.5 range heuristic)
        if (directX <= 1.5f && directY <= 1.5f && screenWidth > 0 && screenHeight > 0) {
            val result = (directX * screenWidth).toInt() to (directY * screenHeight).toInt()
            Log.d("RecordingPreview", "Resolved normalized point")
            return result
        }
        return directX.toInt() to directY.toInt()
    }
    val element = params["element"] as? List<*>
    val relX = (element?.getOrNull(0) as? Number)?.toFloat()
    val relY = (element?.getOrNull(1) as? Number)?.toFloat()
    return if (relX != null && relY != null && screenWidth > 0 && screenHeight > 0) {
        (relX * screenWidth).toInt() to (relY * screenHeight).toInt()
    } else {
        0 to 0
    }
}

private fun resolvePathPoints(
    params: Map<String, Any>,
    screenWidth: Int,
    screenHeight: Int
): List<Pair<Int, Int>> {
    val path = params["path"] as? List<*>
    if (path != null) {
        val points = path.mapNotNull { parsePoint(it, screenWidth, screenHeight) }
        if (points.isNotEmpty()) return points
    }
    val start = parsePoint(params["start"], screenWidth, screenHeight)
    val end = parsePoint(params["end"], screenWidth, screenHeight)
    return if (start != null && end != null) listOf(start, end) else emptyList()
}

private fun resolveMultiTapPoints(
    params: Map<String, Any>,
    screenWidth: Int,
    screenHeight: Int
): List<Pair<Int, Int>> {
    val points = params["points"] as? List<*>
    if (points != null) {
        val resolved = points.mapNotNull { parsePoint(it, screenWidth, screenHeight) }
        if (resolved.isNotEmpty()) return resolved
    }
    return emptyList()
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
            text = "$label ×${formatScaleFactor(value)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onValueChange(stepScaleFactor(value, -1)) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "减少倍数")
            }
            IconButton(
                onClick = { onValueChange(stepScaleFactor(value, 1)) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "增加倍数")
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
 * 点击详情
 */
@Composable
private fun TapDetails(
    params: Map<String, Any>,
    screenWidth: Int,
    screenHeight: Int
) {
    val (x, y) = resolvePoint(params, screenWidth, screenHeight)
    DetailRow(label = "点击位置", value = "($x, $y)")
}

/**
 * 多点点击详情
 */
@Composable
private fun MultiTapDetails(
    params: Map<String, Any>,
    screenWidth: Int,
    screenHeight: Int
) {
    val points = resolveMultiTapPoints(params, screenWidth, screenHeight)
    if (points.isEmpty()) {
        DetailRow(label = "点击点位", value = "0 个")
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DetailRow(label = "点击点位", value = "${points.size} 个")
        points.forEachIndexed { index, point ->
            DetailRow(label = "点位 ${index + 1}", value = "(${point.first}, ${point.second})")
        }
    }
}

/**
 * 长按详情
 */
@Composable
private fun LongPressDetails(
    params: Map<String, Any>,
    screenWidth: Int,
    screenHeight: Int
) {
    val (x, y) = resolvePoint(params, screenWidth, screenHeight)
    val duration = (params["duration"] as? Number)?.toLong() ?: 800L

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DetailRow(label = "长按位置", value = "($x, $y)")
        DetailRow(label = "按压时长", value = "${duration}ms")
    }
}

/**
 * 滑动详情
 */
@Composable
private fun SwipeDetails(
    params: Map<String, Any>,
    screenWidth: Int,
    screenHeight: Int
) {
    val pathPoints = resolvePathPoints(params, screenWidth, screenHeight)
    if (pathPoints.isNotEmpty()) {
        val first = pathPoints.first()
        val last = pathPoints.last()
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DetailRow(label = "起点", value = "(${first.first}, ${first.second})")
            DetailRow(label = "终点", value = "(${last.first}, ${last.second})")
            DetailRow(label = "路径点数", value = "${pathPoints.size} 个")
        }
    } else {
        // 旧格式：startX, startY, endX, endY
        val startX = (params["startX"] as? Number)?.toInt() ?: 0
        val startY = (params["startY"] as? Number)?.toInt() ?: 0
        val endX = (params["endX"] as? Number)?.toInt() ?: 0
        val endY = (params["endY"] as? Number)?.toInt() ?: 0

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DetailRow(label = "起点", value = "($startX, $startY)")
            DetailRow(label = "终点", value = "($endX, $endY)")
        }
    }
}

/**
 * 长按滑动详情
 */
@Composable
private fun LongPressSwipeDetails(
    params: Map<String, Any>,
    screenWidth: Int,
    screenHeight: Int
) {
    val duration = (params["duration"] as? Number)?.toLong() ?: 800L
    val pathPoints = resolvePathPoints(params, screenWidth, screenHeight)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (pathPoints.isNotEmpty()) {
            val first = pathPoints.first()
            val last = pathPoints.last()
            DetailRow(label = "起点", value = "(${first.first}, ${first.second})")
            DetailRow(label = "终点", value = "(${last.first}, ${last.second})")
            DetailRow(label = "路径点数", value = "${pathPoints.size} 个")
        }
        DetailRow(label = "按压时长", value = "${duration}ms")
    }
}

/**
 * 输入详情
 */
@Composable
private fun TypeDetails(params: Map<String, Any>) {
    val text = params["text"] as? String ?: ""

    DetailRow(label = "输入内容", value = if (text.length > 30) "${text.take(30)}..." else text)
}

/**
 * 滚动详情
 */
@Composable
private fun ScrollDetails(params: Map<String, Any>) {
    val direction = params["direction"] as? String ?: "down"

    DetailRow(label = "滚动方向", value = when (direction) {
        "up" -> "向上"
        "down" -> "向下"
        "left" -> "向左"
        "right" -> "向右"
        else -> direction
    })
}

/**
 * 通用详情（未知类型）
 */
@Composable
private fun GenericDetails(params: Map<String, Any>) {
    params.forEach { (key, value) ->
        DetailRow(label = key, value = value.toString())
    }
}

/**
 * 详情行组件
 */
@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 进度编辑器（基于相对位置）
 * 使用 0-1 的进度范围，操作点在时间轴缩放时保持相对位置不变
 * 支持位置和间隔两种编辑模式
 */
@Composable
fun ProgressEditor(
    currentProgress: Float,  // 0.0 到 1.0
    totalDuration: Long,
    currentInterval: Float? = null,  // 新增：当前间隔
    maxInterval: Float? = null,  // 新增：最大允许间隔
    intervalScaleFactor: Float = 2f,
    onIntervalScaleFactorChange: (Float) -> Unit = {},
    onProgressChange: (Float) -> Unit,
    onIntervalChange: ((Float) -> Unit)? = null,  // 新增：间隔变化回调
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var draggingValue by remember { mutableStateOf(currentProgress) }
    var editMode by remember { mutableStateOf(1) } // 0: 位置, 1: 间隔

    // 计算最大允许间隔的时间（毫秒）
    val maxIntervalTime = maxInterval?.let { (it * totalDuration).toLong() }

    Column(modifier = modifier.fillMaxWidth()) {
        // 模式切换（仅当提供了间隔回调时显示）
        if (onIntervalChange != null && currentInterval != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = editMode == 1,
                    onClick = { editMode = 1 },
                    label = { Text("设置间隔") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = editMode == 0,
                    onClick = { editMode = 0 },
                    label = { Text("设置位置") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // 标题
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (editMode == 0) "执行位置" else "时间间隔",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                if (editMode == 1) {
                    Spacer(modifier = Modifier.width(4.dp))
                    HelpTooltip(
                        text = "设置该操作点距离前一个操作点的时间间隔。" +
                               (maxIntervalTime?.let { "\n\n⚠️ 当前最大可设置为 ${it}ms (不能超过后一个操作点的前 100ms)。" } ?: "") +
                               "\n\n📍 如有需要，后续操作点会自动后移保持原有间隔。"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Slider 和显示值
        if (editMode == 0) {
            // 位置编辑模式（原有逻辑）
            val displayTime = (if (isDragging) draggingValue else currentProgress) * totalDuration

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Slider(
                    value = if (isDragging) draggingValue else currentProgress,
                    onValueChange = { newProgress ->
                        isDragging = true
                        draggingValue = newProgress
                        onProgressChange(newProgress)
                    },
                    onValueChangeFinished = {
                        isDragging = false
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.widthIn(min = 90.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = formatTimestamp(displayTime.toLong()),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        } else {
            // 间隔编辑模式（新增）
            var intervalValue by remember(currentInterval) { mutableStateOf(currentInterval ?: 0f) }
            val intervalTime = intervalValue * totalDuration
            var intervalInputText by remember(intervalTime) {
                mutableStateOf(intervalTime.toLong().toString())
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                var hasFocus by remember { mutableStateOf(false) }
                fun commitIntervalInput() {
                    val millis = intervalInputText.toLongOrNull() ?: 0L
                    val constrainedMillis = if (maxIntervalTime != null && millis > maxIntervalTime) {
                        maxIntervalTime
                    } else {
                        millis
                    }
                    val normalizedText = if (intervalInputText.isBlank()) "" else constrainedMillis.toString()
                    intervalInputText = normalizedText
                    val newInterval = if (totalDuration > 0) {
                        constrainedMillis.toFloat() / totalDuration.toFloat()
                    } else {
                        0f
                    }
                    intervalValue = newInterval
                    onIntervalChange?.invoke(newInterval)
                }
                fun applyIntervalScale(scale: Float) {
                    val millis = intervalInputText.toLongOrNull() ?: 0L
                    val scaled = (millis * scale).toLong().coerceAtLeast(0L)
                    val constrainedMillis = if (maxIntervalTime != null && scaled > maxIntervalTime) {
                        maxIntervalTime
                    } else {
                        scaled
                    }
                    intervalInputText = constrainedMillis.toString()
                    val newInterval = if (totalDuration > 0) {
                        constrainedMillis.toFloat() / totalDuration.toFloat()
                    } else {
                        0f
                    }
                    intervalValue = newInterval
                    onIntervalChange?.invoke(newInterval)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = intervalInputText,
                        onValueChange = { input ->
                            val digitsOnly = input.filter { it.isDigit() }
                            intervalInputText = digitsOnly
                        },
                        label = { Text("间隔时间 (毫秒)") },
                        suffix = { Text("ms") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { state ->
                                if (hasFocus && !state.isFocused) {
                                    commitIntervalInput()
                                }
                                hasFocus = state.isFocused
                            },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = { commitIntervalInput() }
                        ),
                        shape = RoundedCornerShape(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { applyIntervalScale(intervalScaleFactor) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("设置间隔加倍")
                    }
                    IntervalFactorStepper(
                        label = "倍数",
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
                            text = "快捷:",
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
                                        // 如果超过最大值，使用最大值
                                        val constrainedMillis = if (maxIntervalTime != null && millis > maxIntervalTime) {
                                            maxIntervalTime
                                        } else {
                                            millis
                                        }

                                        intervalInputText = constrainedMillis.toString()
                                        val newInterval = if (totalDuration > 0) {
                                            constrainedMillis.toFloat() / totalDuration.toFloat()
                                        } else {
                                            0f
                                        }
                                        intervalValue = newInterval
                                        onIntervalChange?.invoke(newInterval)
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
        }

        // 拖动提示
        if (isDragging) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "↔ 拖动中",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 执行时间编辑器（已弃用，向后兼容）
 * @deprecated 使用 ProgressEditor 代替
 */
@Deprecated("Use ProgressEditor instead", ReplaceWith("ProgressEditor(currentProgress, totalDuration, onProgressChange)"))
@Composable
fun TimestampEditor(
    currentTimestamp: Long,
    totalDuration: Long,
    startDelay: Long = 0L,
    firstActionTime: Long = 0L,
    onTimestampChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // 计算在时间轴上的实际显示位置
    val actualTime = startDelay + (currentTimestamp - firstActionTime)

    // 使用本地状态来跟踪拖动中的临时值，减少频繁更新
    var isDragging by androidx.compose.runtime.remember { mutableStateOf(false) }
    var draggingValue by androidx.compose.runtime.remember { mutableStateOf(actualTime.toFloat()) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "执行时间",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "总时长: ${formatTimestamp(totalDuration)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Slider(
                value = if (isDragging) draggingValue else actualTime.toFloat(),
                onValueChange = { newActualTime ->
                    isDragging = true
                    draggingValue = newActualTime

                    // 将时间轴上的位置转换回 timestamp
                    // actualTime 是在时间轴上的绝对位置（0 到 totalDuration）
                    // timestamp 是操作的原始时间戳
                    // 关系：actualTime = startDelay + (timestamp - firstActionTime)
                    // 因此：timestamp = firstActionTime + (actualTime - startDelay)
                    val newTimestamp = firstActionTime + (newActualTime.toLong() - startDelay)

                    // 允许 timestamp 为负数（相对于 firstActionTime），这样操作可以移到起始延迟区域
                    // 但不能小于 (firstActionTime - startDelay)，否则会移到时间轴 0 之前
                    val minTimestamp = firstActionTime - startDelay
                    onTimestampChange(newTimestamp.coerceAtLeast(minTimestamp))
                },
                onValueChangeFinished = {
                    // 拖动结束后重置状态
                    isDragging = false
                },
                // 操作可以在整个时间轴上移动（从 0 到 totalDuration）
                valueRange = 0f..totalDuration.toFloat().coerceAtLeast(1000f),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.widthIn(min = 90.dp)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formatTimestamp(if (isDragging) draggingValue.toLong() else actualTime),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // 进度百分比提示
        val displayTime = if (isDragging) draggingValue.toLong() else actualTime
        val progress = if (totalDuration > 0) {
            (displayTime.toFloat() / totalDuration * 100).toInt()
        } else {
            0
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "在整体时间的 $progress%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            // 拖动中的提示
            if (isDragging) {
                Text(
                    text = "↔ 拖动中",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * 延迟编辑器（保留用于其他地方可能的使用）
 */
@Composable
fun DelayEditor(
    currentDelay: Long,
    onDelayChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "执行后延迟",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Slider(
                value = currentDelay.toFloat(),
                onValueChange = { onDelayChange(it.toLong()) },
                valueRange = 0f..5000f,
                steps = 49, // 每100ms一个刻度
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.widthIn(min = 80.dp)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${currentDelay}ms",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // 快速预设
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickPresetChip(label = "快速", value = 200L, currentDelay = currentDelay, onClick = onDelayChange)
            QuickPresetChip(label = "正常", value = 500L, currentDelay = currentDelay, onClick = onDelayChange)
            QuickPresetChip(label = "慢速", value = 1000L, currentDelay = currentDelay, onClick = onDelayChange)
            QuickPresetChip(label = "很慢", value = 2000L, currentDelay = currentDelay, onClick = onDelayChange)
        }
    }
}

/**
 * 快速预设按钮
 */
@Composable
private fun RowScope.QuickPresetChip(
    label: String,
    value: Long,
    currentDelay: Long,
    onClick: (Long) -> Unit
) {
    FilterChip(
        selected = currentDelay == value,
        onClick = { onClick(value) },
        label = {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall
            )
        },
        modifier = Modifier.weight(1f)
    )
}

/**
 * 获取操作类型图标
 */
fun getActionIcon(actionType: String): ImageVector {
    return when (actionType) {
        "tap" -> Icons.Default.TouchApp
        "multiTap" -> Icons.Default.SelectAll
        "longPress" -> Icons.Default.PanTool
        "swipe" -> Icons.Default.SwipeRight
        "longPressSwipe" -> Icons.Default.Gesture
        "type" -> Icons.Default.Keyboard
        "scroll" -> Icons.Default.SwipeVertical
        else -> Icons.Default.PlayCircle
    }
}

/**
 * 获取操作类型颜色
 */
fun getActionColor(actionType: String): Color {
    return when (actionType) {
        "tap" -> Color(0xFF2196F3)        // 蓝色
        "multiTap" -> Color(0xFF03A9F4)   // 浅蓝色
        "longPress" -> Color(0xFFFF9800)  // 橙色
        "swipe" -> Color(0xFF4CAF50)      // 绿色
        "longPressSwipe" -> Color(0xFFFF5722) // 深橙色
        "type" -> Color(0xFF9C27B0)       // 紫色
        "scroll" -> Color(0xFF00BCD4)     // 青色
        else -> Color.Gray
    }
}

/**
 * 获取操作类型文本
 */
fun getActionTypeText(actionType: String): String {
    return when (actionType) {
        "tap" -> "点击"
        "multiTap" -> "多点点击"
        "longPress" -> "长按"
        "swipe" -> "滑动"
        "longPressSwipe" -> "长按滑动"
        "type" -> "输入"
        "scroll" -> "滚动"
        else -> actionType
    }
}

/**
 * 格式化时间戳
 */
private fun formatTimestamp(ms: Long): String {
    val seconds = ms / 1000.0
    return "%.2fs".format(seconds)
}

/**
 * 格式化操作简要信息
 */
fun formatActionBrief(
    action: RecordedAction,
    screenWidth: Int,
    screenHeight: Int
): String {
    return when (action.actionType) {
        "tap" -> {
            val (x, y) = resolvePoint(action.params, screenWidth, screenHeight)
            "位置: ($x, $y)"
        }
        "multiTap" -> {
            val points = resolveMultiTapPoints(action.params, screenWidth, screenHeight)
            "点位: ${points.size} 个"
        }
        "longPress" -> {
            val (x, y) = resolvePoint(action.params, screenWidth, screenHeight)
            val duration = (action.params["duration"] as? Number)?.toLong() ?: 800L
            "位置: ($x, $y) • 时长: ${duration}ms"
        }
        "swipe" -> {
            val pathPoints = resolvePathPoints(action.params, screenWidth, screenHeight)
            if (pathPoints.isNotEmpty()) {
                val first = pathPoints.first()
                val last = pathPoints.last()
                "起点: (${first.first}, ${first.second}) → 终点: (${last.first}, ${last.second})"
            } else {
                val startX = (action.params["startX"] as? Number)?.toInt() ?: 0
                val startY = (action.params["startY"] as? Number)?.toInt() ?: 0
                val endX = (action.params["endX"] as? Number)?.toInt() ?: 0
                val endY = (action.params["endY"] as? Number)?.toInt() ?: 0
                "起点: ($startX, $startY) → 终点: ($endX, $endY)"
            }
        }
        "longPressSwipe" -> {
            val pathPoints = resolvePathPoints(action.params, screenWidth, screenHeight)
            val duration = (action.params["duration"] as? Number)?.toLong() ?: 800L
            if (pathPoints.isNotEmpty()) {
                val first = pathPoints.first()
                val last = pathPoints.last()
                "起点: (${first.first}, ${first.second}) → 终点: (${last.first}, ${last.second}) • 时长: ${duration}ms"
            } else {
                "时长: ${duration}ms"
            }
        }
        "type" -> {
            val text = action.params["text"] as? String ?: ""
            "内容: ${if (text.length > 20) "${text.take(20)}..." else text}"
        }
        "scroll" -> {
            val direction = action.params["direction"] as? String ?: "down"
            "方向: ${when (direction) {
                "up" -> "向上"
                "down" -> "向下"
                "left" -> "向左"
                "right" -> "向右"
                else -> direction
            }}"
        }
        else -> "参数: ${action.paramsJson}"
    }
}
