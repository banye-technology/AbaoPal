package com.withcareer.screenpal_android.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.withcareer.screenpal_android.recording.EditableAction
import com.withcareer.screenpal_android.ui.component.getActionColor
import android.os.SystemClock
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.ceil

/**
 * 刻度配置
 * @param minorInterval 小刻度间隔（毫秒）
 * @param majorInterval 大刻度间隔（毫秒）
 * @param majorMultiple 大刻度是小刻度的倍数
 */
private data class TickConfig(
    val minorInterval: Long,
    val majorInterval: Long,
    val majorMultiple: Int
)

/**
 * 刻度数据
 * @param timeMs 刻度的时间位置（毫秒）
 * @param isMajor 是否为大刻度
 * @param label 标签文本（仅大刻度有）
 */
private data class Tick(
    val timeMs: Long,
    val isMajor: Boolean,
    val label: String?
)

/**
 * 根据时间轴长度和缩放级别选择合适的刻度配置
 */
private fun selectTickConfig(durationMs: Long, zoomScale: Float = 1f): TickConfig {
    // 考虑缩放后的有效时长
    val effectiveDuration = durationMs / zoomScale

    return when {
        effectiveDuration <= 5000 -> TickConfig(
            minorInterval = 100L,   // 0.1s
            majorInterval = 500L,   // 0.5s
            majorMultiple = 5
        )
        effectiveDuration <= 15000 -> TickConfig(
            minorInterval = 500L,   // 0.5s
            majorInterval = 2500L,  // 2.5s
            majorMultiple = 5
        )
        effectiveDuration <= 30000 -> TickConfig(
            minorInterval = 1000L,  // 1s
            majorInterval = 5000L,  // 5s
            majorMultiple = 5
        )
        effectiveDuration <= 60000 -> TickConfig(
            minorInterval = 2000L,  // 2s
            majorInterval = 10000L, // 10s
            majorMultiple = 5
        )
        effectiveDuration <= 120000 -> TickConfig(
            minorInterval = 5000L,  // 5s
            majorInterval = 20000L, // 20s
            majorMultiple = 4
        )
        effectiveDuration <= 300000 -> TickConfig(
            minorInterval = 10000L,  // 10s
            majorInterval = 60000L,  // 1min
            majorMultiple = 6
        )
        else -> TickConfig(
            minorInterval = 15000L,  // 15s
            majorInterval = 60000L,  // 1min
            majorMultiple = 4
        )
    }
}

/**
 * 生成刻度数据
 */
private fun generateTicks(
    totalDuration: Long,
    zoomScale: Float = 1f,
    maxTicks: Int = Int.MAX_VALUE
): List<Tick> {
    if (totalDuration <= 0) return emptyList()

    var config = selectTickConfig(totalDuration, zoomScale)
    val estimatedTicks = (totalDuration / config.minorInterval).toInt().coerceAtLeast(1)
    if (estimatedTicks > maxTicks && maxTicks > 0) {
        val factor = ceil(estimatedTicks.toFloat() / maxTicks.toFloat()).toInt().coerceAtLeast(1)
        val adjustedMinor = config.minorInterval * factor
        config = config.copy(
            minorInterval = adjustedMinor,
            majorInterval = adjustedMinor * config.majorMultiple
        )
    }
    val ticks = mutableListOf<Tick>()

    var currentTime = 0L
    var tickIndex = 0

    while (currentTime <= totalDuration) {
        // 0s 不标注标签
        val isMajor = tickIndex % config.majorMultiple == 0
        val label = if (isMajor && currentTime > 0) formatTime(currentTime) else null

        ticks.add(Tick(
            timeMs = currentTime,
            isMajor = isMajor,
            label = label
        ))

        currentTime += config.minorInterval
        tickIndex++
    }

    return ticks
}

/**
 * 录制时间轴组件
 * 横向显示所有操作的时间点，支持点击选择和双指缩放
 * 选中的操作点会高亮显示
 * 底部有可拖动的单横条用于调整时间轴长度（1秒 - 1分钟）
 */
@Composable
fun RecordingTimeline(
    actions: List<EditableAction>,
    totalDuration: Long,
    baseActionsDuration: Long = 5000L,
    startDelay: Long = 0L,
    endDelay: Long = 0L,
    firstActionTime: Long = 0L,
    currentTime: Long = 0,
    dynamicMinDuration: Long = 1000L, // 新增：动态最小长度限制
    zoomScale: Float? = null,
    onZoomScaleChange: ((Float) -> Unit)? = null,
    focusedActionId: String? = null,
    onActionClick: (String) -> Unit,
    onBaseActionsDurationChange: ((Long) -> Unit)? = null,  // 接收新的总时长（按比例缩放）
    onStartDelayChange: ((Long) -> Unit)? = null,
    onEndDelayChange: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (actions.isEmpty()) return

    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val timelineHeight = 80.dp
    var lastZoomUpdateMs by remember { mutableStateOf(0L) }

    // 记录容器宽度
    var containerWidthPx by remember { mutableStateOf(0f) }

    // 缩放状态：1.0 表示默认视图（显示全部），值越大越放大
    var internalZoomScale by remember { mutableStateOf(1f) }
    val zoomScaleValue = zoomScale ?: internalZoomScale
    val setZoomScale: (Float) -> Unit = { newValue ->
        if (onZoomScaleChange != null) {
            onZoomScaleChange(newValue)
        } else {
            internalZoomScale = newValue
        }
    }

    // 计算基准宽度（屏幕宽度 - padding）
    val baseWidthDp = with(density) {
        containerWidthPx.toDp()
    }.coerceAtLeast(300.dp) // 最小宽度

    // 计算缩放范围
    val minZoom = 1f
    // 最大缩放：100ms 占据至少 50dp
    val pixelsPer100ms = 50.dp
    val maxZoomFromTicks = if (totalDuration > 0) {
        // 最小刻度为 0.1s(100ms)，达到该刻度后不再放大
        (totalDuration / 5000f).coerceAtLeast(minZoom)
    } else {
        minZoom
    }
    val maxZoom = max(minZoom, maxZoomFromTicks)
    val zoomScaleState by rememberUpdatedState(zoomScaleValue)
    val maxZoomState by rememberUpdatedState(maxZoom)

    // 根据缩放计算时间轴宽度
    val timelineWidth = baseWidthDp * zoomScaleValue
    val timelineWidthPx = with(density) { timelineWidth.toPx() }
    val maxTicks = remember(timelineWidthPx) {
        val minTickSpacingPx = with(density) { 12.dp.toPx() }
        (timelineWidthPx / minTickSpacingPx).roundToInt().coerceAtLeast(12)
    }
    val ticks by remember(totalDuration, zoomScaleValue, maxTicks) {
        derivedStateOf { generateTicks(totalDuration, zoomScaleValue, maxTicks) }
    }
    val majorTicks by remember(ticks) {
        derivedStateOf { ticks.filter { it.isMajor } }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // 标题 + 缩放提示
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "操作时间轴",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = if (zoomScaleValue > 1.1f) "缩放: ${String.format("%.1f", zoomScaleValue)}×" else "双指缩放查看细节",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }

        // 时间轴容器
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(timelineHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                .onSizeChanged { size ->
                    // 记录容器宽度（减去 padding）
                    containerWidthPx = (size.width - with(density) { 32.dp.toPx() }).coerceAtLeast(0f)
                }
        ) {
            // 可滚动的时间轴内容（添加 padding 为左右横条留出空间）
            // 单指滑动 = horizontalScroll 处理
            // 双指缩放 = pointerInput 处理（只在双指时触发）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .pointerInput(Unit) {
                        // 手动处理手势，只在双指时处理缩放
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var currentZoom = zoomScaleState
                            do {
                                val event = awaitPointerEvent()
                                val pointerCount = event.changes.size

                                // 只有双指或以上才处理缩放
                                if (pointerCount >= 2) {
                                    val zoom = event.calculateZoom()
                                    if (zoom != 1.0f) {
                                        currentZoom = (currentZoom * zoom).coerceIn(minZoom, maxZoomState)
                                        val now = SystemClock.elapsedRealtime()
                                        if (now - lastZoomUpdateMs > 16L) {
                                            setZoomScale(currentZoom)
                                            lastZoomUpdateMs = now
                                        }
                                        // 消费事件，防止滚动
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                                // 单指不消费事件，让 horizontalScroll 处理
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .horizontalScroll(scrollState)
            ) {
                // 背景线和刻度
                Canvas(
                    modifier = Modifier
                        .width(timelineWidth)
                        .fillMaxHeight()
                ) {
                    val lineY = size.height / 2

                    // 绘制主线
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.4f),
                        start = Offset(0f, lineY),
                        end = Offset(size.width, lineY),
                        strokeWidth = 2.dp.toPx()
                    )

                    // 生成并绘制刻度（分大小刻度）
                    ticks.forEach { tick ->
                        val x = (tick.timeMs.toFloat() / totalDuration.toFloat()) * size.width

                        if (tick.isMajor) {
                            // 大刻度：更长、更粗
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.6f),
                                start = Offset(x, lineY - 12.dp.toPx()),
                                end = Offset(x, lineY + 12.dp.toPx()),
                                strokeWidth = 2.dp.toPx()
                            )
                        } else {
                            // 小刻度：更短、更细
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.3f),
                                start = Offset(x, lineY - 6.dp.toPx()),
                                end = Offset(x, lineY + 6.dp.toPx()),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }

                    // 绘制当前进度（预览时使用）
                    if (currentTime > 0) {
                        val progress = currentTime.toFloat() / totalDuration.coerceAtLeast(1)
                        val progressX = progress * size.width
                        drawLine(
                            color = Color.Red.copy(alpha = 0.8f),
                            start = Offset(progressX, 0f),
                            end = Offset(progressX, size.height),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f))
                        )
                    }
                }

                // 操作点
                Box(
                    modifier = Modifier
                        .width(timelineWidth)
                        .fillMaxHeight()
                ) {
                    actions.forEachIndexed { index, editableAction ->
                        // 直接使用 progress 计算位置（0-1 范围）
                        val progress = editableAction.action.progress
                        val clampedProgress = progress.coerceIn(0f, 1f)

                        // 选中的或展开的操作点更大、更亮
                        val isHighlighted = editableAction.isSelected ||
                            editableAction.isExpanded ||
                            (focusedActionId != null && editableAction.id == focusedActionId)
                        val dotSize = if (isHighlighted) 24.dp else 16.dp
                        val rawX = (timelineWidth * clampedProgress) - (dotSize / 2)
                        // 确保圆点不超出左边界，右边界允许超出（因为可能有 Bar）
                        val boundedX = maxOf(rawX, 0.dp)
                        val offsetY = (timelineHeight - dotSize) / 2

                        // 获取持续时间
                        val params = editableAction.action.params
                        val duration = when (editableAction.action.actionType) {
                            "longPress" -> (params["duration"] as? Number)?.toLong() ?: 0L
                            "swipe" -> (params["duration"] as? Number)?.toLong() ?: 0L
                            "longPressSwipe" -> (params["duration"] as? Number)?.toLong() ?: 0L
                            else -> 0L
                        }
                        val durationProgress = if (totalDuration > 0) duration.toFloat() / totalDuration else 0f
                        val barWidth = timelineWidth * durationProgress

                        ActionTimelineItem(
                            action = editableAction,
                            index = index,
                            isHighlighted = isHighlighted,
                            dotSize = dotSize,
                            barWidth = barWidth,
                            onClick = { onActionClick(editableAction.id) },
                            modifier = Modifier
                                .offset(x = boundedX, y = offsetY)
                        )
                    }
                }
            }
        }

        // 时间标签（与时间轴同步滚动，只显示大刻度标签）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Box(modifier = Modifier.width(timelineWidth)) {
                majorTicks.forEach { tick ->
                    val progress = tick.timeMs.toFloat() / totalDuration.toFloat()
                    val offsetX = (timelineWidth * progress) - 25.dp // 居中对齐（标签宽度的一半）

                    Text(
                        text = tick.label ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .offset(x = offsetX.coerceAtLeast(0.dp))
                            .width(50.dp)
                    )
                }
            }
        }

        // 时间轴长度调整控制区域（单横条 + 输入框）
        if (onBaseActionsDurationChange != null) {
            Spacer(modifier = Modifier.height(12.dp))

            TimelineDurationControl(
                currentDuration = totalDuration,
                minDuration = dynamicMinDuration, // 使用动态最小值
                maxDuration = 600000L,
                onDurationChange = onBaseActionsDurationChange
            )
        }
    }
}

/**
 * 时间轴长度控制（单横条 + 输入框）
 * 可通过拖动或输入调整时间轴的总长度
 */
@Composable
private fun TimelineDurationControl(
    currentDuration: Long,
    minDuration: Long,
    maxDuration: Long,
    onDurationChange: (Long) -> Unit
) {
    val currentDurationState by rememberUpdatedState(currentDuration)
    val minDurationState by rememberUpdatedState(minDuration)
    val maxDurationState by rememberUpdatedState(maxDuration)
    var initialDuration by remember { mutableStateOf(0L) }

    // 输入框文本状态
    // 使用 LaunchedEffect 监听 currentDuration 的变化，并同步到 inputText
    // 这样当 currentDuration 因为拖动或其他逻辑改变时，输入框能实时更新
    var inputText by remember {
        mutableStateOf("%.1f".format(currentDuration / 1000.0))
    }

    LaunchedEffect(currentDuration) {
        val formatted = "%.1f".format(currentDuration / 1000.0)
        if (inputText != formatted && !formatted.startsWith(inputText)) {
            inputText = formatted
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "设置时间轴长度",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    HelpTooltip(
                        text = "调整录制内容的总时长。时间点在时长变化时保持绝对时间不变（即 1.0s 永远是 1.0s）。\n\n最小长度受最后一个操作点位置限制。"
                    )
                }

                // 时长输入框
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = inputText,
                            onValueChange = { newText ->
                                // 限制输入格式：数字和小数点，且最多一位小数
                                if (newText.isEmpty() || newText.matches(Regex("""^\d*\.?\d{0,1}$"""))) {
                                    inputText = newText
                                    newText.toDoubleOrNull()?.let { seconds ->
                                        val millis = (seconds * 1000).toLong()
                                        if (millis in minDurationState..maxDurationState) {
                                            onDurationChange(millis)
                                        }
                                    }
                                }
                            },
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.width(64.dp),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                                imeAction = androidx.compose.ui.text.input.ImeAction.Done
                            ),
                            singleLine = true
                        )
                        Text(
                            text = "s",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 拖动条
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                contentAlignment = Alignment.Center
            ) {
                // 背景轨道
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(4.dp)
                        )
                )

                // 可拖动横条
                TimelineDurationBar(
                    currentDuration = currentDurationState,
                    minDuration = minDurationState,
                    maxDuration = maxDurationState,
                    onDragStart = {
                        initialDuration = currentDurationState
                    },
                    onDrag = { dragOffsetPx, barWidthPx ->
                        val dragProgress = dragOffsetPx / barWidthPx
                        val durationRange = maxDurationState - minDurationState
                        val durationChange = (durationRange * dragProgress).toLong()
                        val newDuration = (initialDuration + durationChange)
                            .coerceIn(minDurationState, maxDurationState)

                        onDurationChange(newDuration)
                    }
                )
            }

            // 范围提示
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "%.1fs".format(minDurationState / 1000.0),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Text(
                    text = "${maxDurationState / 1000}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * 时间轴长度调整横条
 */
@Composable
private fun TimelineDurationBar(
    currentDuration: Long,
    minDuration: Long,
    maxDuration: Long,
    onDragStart: () -> Unit,
    onDrag: (dragOffsetPx: Float, barWidthPx: Float) -> Unit
) {
    val density = LocalDensity.current
    var totalDragOffset by remember { mutableStateOf(0f) }
    var barWidthPx by remember { mutableStateOf(0f) }
    val barHeight = 12.dp

    // 计算当前时长在范围内的进度（0-1）
    val progress = ((currentDuration - minDuration).toFloat() /
                   (maxDuration - minDuration).toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight)
            .onSizeChanged { size ->
                barWidthPx = size.width.toFloat()
            }
            .pointerInput(minDuration, maxDuration) {
                detectDragGestures(
                    onDragStart = {
                        totalDragOffset = 0f
                        onDragStart()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragOffset += dragAmount.x
                        onDrag(totalDragOffset, barWidthPx)
                    },
                    onDragEnd = {
                        totalDragOffset = 0f
                    }
                )
            }
    ) {
        // 横条主体（带渐变效果）
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            shape = RoundedCornerShape(6.dp)
        ) {}

        // 进度指示器（小圆点）
        Box(
            modifier = Modifier
                .offset(x = with(density) { (barWidthPx * progress).toDp() } - 8.dp)
                .align(Alignment.CenterStart)
                .size(16.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                )
                .then(
                    Modifier.padding(4.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = Color.White,
                        shape = CircleShape
                    )
            )
        }

        // 拖动手柄（三条竖线）
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .height(barHeight),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .height(6.dp)
                        .background(Color.White.copy(alpha = 0.9f))
                )
            }
        }
    }
}

/**
 * 时间轴边缘横条（带标签）- 已弃用，保留用于向后兼容
 * 可拖动调整起始/结束延迟
 */
@Deprecated("使用 TimelineDurationControl 代替")
@Composable
private fun TimelineEdgeBarWithLabel(
    isStart: Boolean,
    delay: Long,
    totalDuration: Long,
    density: Density,
    timelineWidth: Dp,
    onDelayChange: (Long) -> Unit
) {
    // 使用 rememberUpdatedState 确保始终访问最新的 delay 值
    val currentDelay by rememberUpdatedState(delay)

    // 记录拖动开始时的初始延迟值（用于在拖动过程中计算）
    var initialDelay by remember { mutableStateOf(0L) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 延迟时间显示
        Text(
            text = "%.1fs".format(delay / 1000.0),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        // 横条
        TimelineEdgeBar(
            isStart = isStart,
            onDragStart = {
                // 拖动开始时，捕获当前最新的延迟值作为初始值
                initialDelay = currentDelay
                android.util.Log.d("RecordingTimeline", "Timeline delay drag started")
            },
            onDrag = { dragOffsetPx ->
                // 基于初始延迟和累计偏移量计算新延迟
                val timelineWidthPx = with(density) { timelineWidth.toPx() }
                val offsetProgress = if (isStart) {
                    -dragOffsetPx / timelineWidthPx // 起始：向左增加延迟
                } else {
                    dragOffsetPx / timelineWidthPx  // 结束：向右增加延迟
                }
                val timeOffset = (totalDuration * offsetProgress).toLong()
                val newDelay = (initialDelay + timeOffset).coerceAtLeast(0L)

                android.util.Log.d("RecordingTimeline", "Timeline delay drag updated")

                onDelayChange(newDelay)
            }
        )

        // 标签
        Text(
            text = if (isStart) "起始" else "结束",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/**
 * 时间轴边缘横条
 */
@Composable
private fun TimelineEdgeBar(
    isStart: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit // 参数：累计拖动偏移量（px）
) {
    var totalDragOffset by remember { mutableStateOf(0f) }
    val barWidth = 60.dp  // 横条的长度
    val barHeight = 10.dp  // 横条的高度
    val barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)

    Box(
        modifier = Modifier
            .width(barWidth)
            .height(barHeight)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        totalDragOffset = 0f
                        onDragStart()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragOffset += dragAmount.x
                        onDrag(totalDragOffset)
                    },
                    onDragEnd = {
                        totalDragOffset = 0f
                    }
                )
            }
    ) {
        // 横条主体
        Surface(
            modifier = Modifier
                .width(barWidth)
                .height(barHeight),
            color = barColor,
            shape = RoundedCornerShape(5.dp)
        ) {}

        // 拖动手柄（三条竖线）
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .height(barHeight),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .height(5.dp)
                        .background(Color.White.copy(alpha = 0.9f))
                )
            }
        }
    }
}

/**
 * 操作点/条标记（带编号和持续时间条）
 */
@Composable
private fun ActionTimelineItem(
    action: EditableAction,
    index: Int,
    isHighlighted: Boolean,
    dotSize: Dp,
    barWidth: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = getActionColor(action.action.actionType)

    Box(modifier = modifier) {
        // 绘制持续时间条 (如果 barWidth > 0)
        // 放在圆点下方(图层下)，或者右侧
        if (barWidth > 0.dp) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = dotSize / 2) // 从圆心开始延伸
                    .width(barWidth)
                    .height(if (isHighlighted) 12.dp else 8.dp), // 条的高度
                color = color.copy(alpha = 0.4f),
                shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
            ) {}
        }

        // 绘制圆点
        Surface(
            shape = CircleShape,
            color = if (isHighlighted) color else color.copy(alpha = 0.7f),
            modifier = Modifier
                .size(dotSize)
                .align(Alignment.CenterStart) // 确保圆点在左侧起始位置
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onClick() })
                },
            shadowElevation = if (isHighlighted) 8.dp else 2.dp,
            border = if (isHighlighted) BorderStroke(
                width = 2.dp,
                color = Color.White
            ) else null
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = if (isHighlighted) 10.sp else 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * 格式化时间显示
 */
private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000.0).toInt()
    return if (totalSeconds < 60) {
        "%.1fs".format(ms / 1000.0)
    } else {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        "%d:%02d".format(minutes, seconds)
    }
}

/**
 * 时间轴延迟控制面板
 * 专门用于调整起始延迟和结束延迟
 */
@Composable
fun TimelineDelayControl(
    startDelay: Long,
    endDelay: Long,
    actionsDuration: Long,
    onStartDelayChange: (Long) -> Unit,
    onEndDelayChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "时间轴控制",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 起始延迟控制
            DelayControlItem(
                icon = Icons.Default.HourglassTop,
                label = "起始延迟",
                delay = startDelay,
                maxDelay = 10000L, // 最大10秒
                onDelayChange = onStartDelayChange
            )

            // 操作时长显示（只读）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.HourglassBottom,
                    contentDescription = "操作时长",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "操作时长",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "%.2fs".format(actionsDuration / 1000.0),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            // 结束延迟控制
            DelayControlItem(
                icon = Icons.Default.HourglassBottom,
                label = "结束延迟",
                delay = endDelay,
                maxDelay = 10000L, // 最大10秒
                onDelayChange = onEndDelayChange
            )
        }
    }
}

/**
 * 延迟控制项
 */
@Composable
private fun DelayControlItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    delay: Long,
    maxDelay: Long,
    onDelayChange: (Long) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "%.2fs".format(delay / 1000.0),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Slider(
                value = delay.toFloat(),
                onValueChange = { onDelayChange(it.toLong()) },
                valueRange = 0f..maxDelay.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
