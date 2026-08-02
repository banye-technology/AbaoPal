package com.withcareer.screenpal_android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.gson.Gson
import com.withcareer.screenpal_android.recording.EditableAction
import com.withcareer.screenpal_android.recording.RecordedAction
import com.withcareer.screenpal_android.recording.RecordingEditState
import com.withcareer.screenpal_android.recording.RecordingResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 录制编辑页面 ViewModel
 * 管理编辑状态和操作
 */
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import android.util.Log

class RecordingEditViewModel(
    private val initialResult: RecordingResult
) : ViewModel() {

    companion object {
        // 操作之间的最小时间间隔（毫秒）- 防止操作点过于接近
        const val MIN_ACTION_INTERVAL = 100L

        /**
         * 将进度转换为时间（毫秒）
         * @param progress 0.0 到 1.0 之间的相对位置
         * @param totalDuration 时间轴总长度（包含起始和结束延迟）
         * @return 在时间轴上的绝对时间（毫秒）
         */
        fun progressToTime(
            progress: Float,
            totalDuration: Long
        ): Long {
            return (totalDuration * progress).toLong()
        }

        /**
         * 将时间转换为进度
         * @param time 在时间轴上的绝对时间（毫秒）
         * @param totalDuration 时间轴总长度（包含起始和结束延迟）
         * @return 0.0 到 1.0 之间的相对位置
         */
        fun timeToProgress(
            time: Long,
            totalDuration: Long
        ): Float {
            return if (totalDuration > 0) {
                (time.toFloat() / totalDuration).coerceIn(0f, 1f)
            } else {
                0f
            }
        }

        /**
         * 将 timestamp 转换为 progress
         * @param timestamp 操作的原始时间戳（相对录制开始）
         * @param firstActionTime 第一个操作的时间戳
         * @param lastActionTime 最后一个操作的时间戳
         * @return 0.0 到 1.0 之间的相对位置
         */
        fun timestampToProgress(
            timestamp: Long,
            firstActionTime: Long,
            lastActionTime: Long
        ): Float {
            val duration = lastActionTime - firstActionTime
            return if (duration > 0) {
                ((timestamp - firstActionTime).toFloat() / duration).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
    }

    // 立即初始化状态，避免异步延迟导致的白屏
    private val _uiState = MutableStateFlow<RecordingEditState?>(
        createInitialState()
    )
    val uiState: StateFlow<RecordingEditState?> = _uiState.asStateFlow()

    private val gson = Gson()
    private var cachedActionIndexMap: Map<String, Int> = emptyMap()
    private var cachedActionsRef: List<EditableAction>? = null
    private var liveEditBaseActions: List<EditableAction>? = null
    private var liveEditDraftActions: MutableList<EditableAction>? = null
    private var liveEditActive: Boolean = false
    private var liveEditDirty: Boolean = false

    private fun generateDefaultTitle(): String {
        return java.text.SimpleDateFormat("录制_yyyy-MM-dd_HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
    }

    /**
     * 创建初始状态，将 timestamp 转换为 progress
     */
    @Suppress("DEPRECATION")
    private fun createInitialState(): RecordingEditState {
        val normalizedActions = initialResult.actions.map { it.normalizeActionType() }

        // 计算起始延迟、结束延迟和总时长
        var startDelay = 0L
        var endDelay = 1000L
        var totalDuration = initialResult.totalDuration

        // 计算基准时长
        val baseActionsDuration = if (totalDuration > 0) {
            // 如果传入了总时长，说明是加载现有录制
            // 此时动作的 timestamp 已经是绝对时间
            val firstActionTime = normalizedActions.firstOrNull()?.timestamp ?: 0L
            val lastActionTime = normalizedActions.lastOrNull()?.timestamp ?: 0L

            // 恢复起始延迟（第一个动作的时间点）
            startDelay = firstActionTime
            // 恢复结束延迟
            endDelay = (totalDuration - lastActionTime).coerceAtLeast(0L)

            (lastActionTime - firstActionTime).coerceAtLeast(100L)
        } else if (normalizedActions.isNotEmpty()) {
            // 如果是新录制（没有 totalDuration）
            val firstTime = normalizedActions.first().timestamp
            val lastTime = normalizedActions.last().timestamp
            val duration = (lastTime - firstTime).coerceAtLeast(1000L)

            // 新录制默认 startDelay = 0, endDelay = 1000
            startDelay = 0L
            endDelay = 1000L
            totalDuration = duration + endDelay
            duration
        } else {
            5000L // 默认 5 秒
        }

        // 将 timestamp 转换为 progress (相对于 totalDuration)
        val editableActions = if (totalDuration > 0) {
            normalizedActions.mapIndexed { index, action ->
                val progress = (action.timestamp.toFloat() / totalDuration).coerceIn(0f, 1f)
                EditableAction(
                    action = action.copy(progress = progress),
                    isExpanded = false
                )
            }
        } else {
            convertTimestampsToProgress(normalizedActions)
        }

        return RecordingEditState(
            title = initialResult.title ?: generateDefaultTitle(),
            actions = editableActions,
            screenWidth = initialResult.screenWidth,
            screenHeight = initialResult.screenHeight,
            screenshotPath = initialResult.screenshotPath,
            focusedActionId = null,
            focusedPointIndex = null,
            isModified = false,
            startDelay = startDelay,
            endDelay = endDelay,
            baseActionsDuration = baseActionsDuration
        )
    }

    /**
     * 将基于 timestamp 的操作列表转换为基于 progress 的操作列表
     */
    @Suppress("DEPRECATION")
    private fun convertTimestampsToProgress(actions: List<RecordedAction>): List<EditableAction> {
        if (actions.isEmpty()) return emptyList()

        val firstTime = actions.first().timestamp
        val lastTime = actions.last().timestamp
        val duration = (lastTime - firstTime).coerceAtLeast(1L)

        return actions.map { action ->
            val progress = timestampToProgress(action.timestamp, firstTime, lastTime)
            EditableAction(
                action = action.copy(progress = progress),
                isExpanded = false
            )
        }
    }

    private fun currentState(): RecordingEditState {
        return _uiState.value ?: RecordingEditState(
            title = "",
            actions = emptyList(),
            screenWidth = initialResult.screenWidth,
            screenHeight = initialResult.screenHeight,
            screenshotPath = initialResult.screenshotPath
        )
    }

    /**
     * 更新标题
     */
    fun updateTitle(title: String) {
        _uiState.value = currentState().copy(
            title = title,
            isModified = true
        )
    }

    /**
     * 标记为已修改
     */
    fun markAsModified() {
        val current = currentState()
        if (!current.isModified) {
            _uiState.value = current.copy(isModified = true)
        }
    }

    /**
     * 更新截图路径（实时编辑模式）
     */
    fun updateScreenshotPath(path: String?) {
        val current = currentState()
        _uiState.value = current.copy(
            screenshotPath = path,
            isModified = true
        )
    }

    data class SelectionSnapshot(
        val selectedIds: Set<String>,
        val expandedIds: Set<String>,
        val focusedActionId: String?,
        val focusedPointIndex: Int?
    )

    fun captureSelectionSnapshot(): SelectionSnapshot {
        val current = currentState()
        return SelectionSnapshot(
            selectedIds = current.actions.filter { it.isSelected }.map { it.id }.toSet(),
            expandedIds = current.actions.filter { it.isExpanded }.map { it.id }.toSet(),
            focusedActionId = current.focusedActionId,
            focusedPointIndex = current.focusedPointIndex
        )
    }

    fun restoreSelectionSnapshot(snapshot: SelectionSnapshot) {
        val current = currentState()
        val updatedActions = current.actions.map { editableAction ->
            editableAction.copy(
                isSelected = snapshot.selectedIds.contains(editableAction.id),
                isExpanded = snapshot.expandedIds.contains(editableAction.id)
            )
        }
        _uiState.value = current.copy(
            actions = updatedActions,
            focusedActionId = snapshot.focusedActionId,
            focusedPointIndex = snapshot.focusedPointIndex
        )
    }

    fun clearSelectionAndFocus() {
        val current = currentState()
        val updatedActions = current.actions.map { editableAction ->
            editableAction.copy(isSelected = false, isExpanded = false)
        }
        _uiState.value = current.copy(
            actions = updatedActions,
            focusedActionId = null,
            focusedPointIndex = null
        )
    }

    /**
     * 更新当前聚焦的操作点（用于实时编辑模式高亮）
     */
    fun updateFocusedAction(actionId: String?, pointIndex: Int? = null) {
        val current = currentState()
        val resolvedPointIndex = if (actionId != null) {
            resolvePrimaryPointIndex(actionId, pointIndex, current)
        } else {
            null
        }
        Log.d(
            "LiveEditPanel",
            "Focused action updated"
        )
        _uiState.value = current.copy(
            focusedActionId = actionId,
            focusedPointIndex = resolvedPointIndex
        )
    }

    /**
     * 单选并聚焦指定操作（用于时间轴/列表/截图统一选择）
     */
    fun selectSingleAction(actionId: String?, pointIndex: Int? = null) {
        val current = currentState()
        if (actionId == null) {
            _uiState.value = current.copy(
                actions = current.actions.map { it.copy(isSelected = false) },
                focusedActionId = null,
                focusedPointIndex = null
            )
            return
        }
        val resolvedPointIndex = resolvePrimaryPointIndex(actionId, pointIndex, current)
        val updatedActions = current.actions.map { editableAction ->
            editableAction.copy(
                isSelected = editableAction.id == actionId,
                isExpanded = editableAction.id == actionId
            )
        }
        Log.d(
            "LiveEditPanel",
            "Single action selected"
        )
        _uiState.value = current.copy(
            actions = updatedActions,
            focusedActionId = actionId,
            focusedPointIndex = resolvedPointIndex
        )
    }

    /**
     * 获取操作点的默认索引（用于实时编辑模式单点聚焦）
     */
    fun getPrimaryPointIndex(actionId: String): Int? {
        return resolvePrimaryPointIndex(actionId, null, currentState())
    }

    private fun resolvePrimaryPointIndex(
        actionId: String,
        pointIndex: Int?,
        state: RecordingEditState
    ): Int? {
        if (pointIndex != null) return pointIndex
        val target = state.actions.firstOrNull { it.id == actionId } ?: return null
        return when (target.action.actionType) {
            "tap", "longPress" -> 0
            "multiTap" -> {
                // 多点点击：使用 null 表示展示全部点位
                null
            }
            else -> null
        }
    }

    /**
     * 更新指定操作的延迟时间
     */
    fun updateDelay(actionId: String, delay: Long) {
        val current = currentState()
        val updatedActions = current.actions.map { editableAction ->
            if (editableAction.id == actionId) {
                editableAction.copy(
                    action = editableAction.action.copy(delayBefore = delay)
                )
            } else {
                editableAction
            }
        }
        if (liveEditActive && liveEditDraftActions != null) {
            liveEditDraftActions = updatedActions.toMutableList()
            liveEditDirty = true
            liveEditBaseActions = updatedActions.map { it.copy(action = it.action.copy()) }
        }
        if (liveEditActive && liveEditDraftActions != null) {
            liveEditDraftActions = updatedActions.toMutableList()
            liveEditDirty = true
            liveEditBaseActions = updatedActions.map { it.copy(action = it.action.copy()) }
        }
        _uiState.value = current.copy(
            actions = updatedActions,
            isModified = true
        )
    }

    /**
     * 更新操作点坐标（用于实时编辑模式拖拽）
     */
    fun updateActionCoordinate(actionId: String, x: Int, y: Int, pointIndex: Int? = null) {
        val current = currentState()
        val sourceActions = if (liveEditActive) {
            liveEditDraftActions?.toList() ?: current.actions
        } else {
            current.actions
        }
        val updatedActions = sourceActions.map { editableAction ->
            if (editableAction.id != actionId) return@map editableAction

            val action = editableAction.action
            val params = action.params.toMutableMap()
            when (action.actionType) {
                "tap", "longPress" -> {
                    params["x"] = x
                    params["y"] = y
                    if (params.containsKey("element") && current.screenWidth > 0 && current.screenHeight > 0) {
                        val relX = x.toFloat() / current.screenWidth.toFloat()
                        val relY = y.toFloat() / current.screenHeight.toFloat()
                        params["element"] = listOf(relX, relY)
                    }
                }
"swipe", "longPressSwipe" -> {
                    val path = params["path"] as? List<*>
                    if (!path.isNullOrEmpty()) {
                        val normalizedPath = normalizeSwipePath(
                            path,
                            current.screenWidth,
                            current.screenHeight
                        ).toMutableList()
                        if (normalizedPath.isNotEmpty()) {
                            val targetIndex = if ((pointIndex ?: 0) <= 0) 0 else normalizedPath.lastIndex
                            normalizedPath[targetIndex]["x"] = x
                            normalizedPath[targetIndex]["y"] = y
                            params["path"] = normalizedPath
                        }
                        if (current.screenWidth > 0 && current.screenHeight > 0 && normalizedPath.isNotEmpty()) {
                            val relPath = normalizedPath.map { point ->
                                val px = (point["x"] as? Number)?.toFloat() ?: 0f
                                val py = (point["y"] as? Number)?.toFloat() ?: 0f
                                listOf(
                                    px / current.screenWidth.toFloat(),
                                    py / current.screenHeight.toFloat()
                                )
                            }
                            params["path"] = relPath
                        }
                    } else {
                        val isStart = (pointIndex ?: 0) <= 0
                        if (isStart) {
                            params["startX"] = x
                            params["startY"] = y
                        } else {
                            params["endX"] = x
                            params["endY"] = y
                        }
                        if (params.containsKey("start") || params.containsKey("end")) {
                            if (current.screenWidth > 0 && current.screenHeight > 0) {
                                val relX = x.toFloat() / current.screenWidth.toFloat()
                                val relY = y.toFloat() / current.screenHeight.toFloat()
                                if (isStart) {
                                    params["start"] = listOf(relX, relY)
                                } else {
                                    params["end"] = listOf(relX, relY)
                                }
                            }
                        }
                    }
                }
                "multiTap" -> {
                    val normalizedPoints = normalizeMultiTapPoints(
                        params["points"] as? List<*>,
                        current.screenWidth,
                        current.screenHeight
                    ).toMutableList()
                    if (normalizedPoints.isNotEmpty()) {
                        val targetIndex = pointIndex?.coerceIn(0, normalizedPoints.lastIndex) ?: 0
                        normalizedPoints[targetIndex]["x"] = x
                        normalizedPoints[targetIndex]["y"] = y
                        params["points"] = normalizedPoints
                    }
                    if (current.screenWidth > 0 && current.screenHeight > 0 && normalizedPoints.isNotEmpty()) {
                        val relPoints = normalizedPoints.map { point ->
                            val px = (point["x"] as? Number)?.toFloat() ?: 0f
                            val py = (point["y"] as? Number)?.toFloat() ?: 0f
                            listOf(
                                px / current.screenWidth.toFloat(),
                                py / current.screenHeight.toFloat()
                            )
                        }
                        params["points"] = relPoints
                    }
                }
            }
            val newParamsJson = gson.toJson(params)
            editableAction.copy(
                action = action.copy(paramsJson = newParamsJson),
                isExpanded = true
            )
        }
        val normalizedActions = if (liveEditActive) {
            updatedActions.map { editableAction ->
                editableAction.copy(
                    isSelected = editableAction.id == actionId,
                    isExpanded = editableAction.id == actionId
                )
            }
        } else {
            updatedActions
        }
        if (liveEditActive && liveEditDraftActions != null) {
            liveEditDraftActions = normalizedActions.toMutableList()
            liveEditDirty = true
        }
        val focusedPoint = if (current.actions.firstOrNull { it.id == actionId }?.action?.actionType == "multiTap") {
            null
        } else {
            pointIndex
        }
        _uiState.value = current.copy(
            actions = normalizedActions,
            isModified = true,
            focusedActionId = actionId,
            focusedPointIndex = focusedPoint
        )
    }

    fun enterLiveEdit() {
        val current = currentState()
        liveEditActive = true
        if (liveEditBaseActions == null || liveEditDraftActions == null) {
            liveEditBaseActions = current.actions.map { action ->
                action.copy(action = action.action.copy())
            }
            liveEditDraftActions = current.actions.map { action ->
                action.copy(action = action.action.copy())
            }.toMutableList()
        }
        val draft = liveEditDraftActions?.toList() ?: current.actions
        _uiState.value = current.copy(actions = draft)
    }

    fun exitLiveEdit() {
        val current = currentState()
        liveEditActive = false
        val draft = liveEditDraftActions?.map { it.copy(action = it.action.copy()) }
            ?: current.actions
        _uiState.value = current.copy(
            actions = draft,
            isModified = current.isModified || liveEditDirty
        )
        liveEditBaseActions = draft
        liveEditDraftActions = null
        liveEditDirty = false
    }

    fun commitLiveEditDraft() {
        val current = currentState()
        val draft = liveEditDraftActions ?: return
        _uiState.value = current.copy(
            actions = draft.map { it.copy(action = it.action.copy()) },
            isModified = true
        )
        liveEditBaseActions = null
        liveEditDraftActions = null
        liveEditDirty = false
        liveEditActive = false
    }

    fun discardLiveEditDraft() {
        liveEditBaseActions = null
        liveEditDraftActions = null
        liveEditDirty = false
        liveEditActive = false
    }

    fun discardAllChanges() {
        liveEditBaseActions = null
        liveEditDraftActions = null
        liveEditDirty = false
        liveEditActive = false
        _uiState.value = createInitialState()
    }

    /**
     * 插入录制动作（用于实时编辑）
     * @param anchorActionId 锚点操作 ID
     * @param insertBefore true 表示前插，false 表示后插
     * @param recordedAction 新录制的动作
     */
    fun insertRecordedAction(
        anchorActionId: String,
        insertBefore: Boolean,
        recordedAction: RecordedAction
    ): Int {
        val current = currentState()
        val anchorIndex = current.actions.indexOfFirst { it.id == anchorActionId }
        if (anchorIndex == -1 || current.actions.isEmpty()) return -1

        val totalDuration = current.totalDuration.coerceAtLeast(1L)
        val anchorProgress = current.actions[anchorIndex].action.progress.coerceIn(0f, 1f)
        val deltaProgress = 100f / totalDuration.toFloat()
        val newProgress = if (insertBefore) {
            (anchorProgress - deltaProgress).coerceAtLeast(0f)
        } else {
            (anchorProgress + deltaProgress).coerceAtMost(1f)
        }
        val shiftStartProgress = anchorProgress
        val shiftDelta = deltaProgress

        val shiftedActions = current.actions.map { editableAction ->
            val progress = editableAction.action.progress
            val shouldShift = if (insertBefore) {
                progress >= shiftStartProgress
            } else {
                progress > shiftStartProgress
            }
            if (!shouldShift || shiftDelta == 0f) {
                editableAction
            } else {
                val shiftedProgress = (progress + shiftDelta).coerceIn(0f, 1f)
                editableAction.copy(
                    action = editableAction.action.copy(progress = shiftedProgress)
                )
            }
        }.toMutableList()

        val absoluteTime = (newProgress * totalDuration).toLong()
        val newAction = recordedAction.copy(
            progress = newProgress,
            timestamp = absoluteTime
        )
        val insertIndex = if (insertBefore) anchorIndex else anchorIndex + 1
        val insertedAction = EditableAction(
            action = newAction,
            isSelected = true,
            isExpanded = true
        )
        shiftedActions.add(
            insertIndex.coerceIn(0, shiftedActions.size),
            insertedAction
        )

        val updatedActions = shiftedActions.map { editableAction ->
            if (editableAction.id == insertedAction.id) {
                editableAction
            } else {
                editableAction.copy(isSelected = false)
            }
        }

        if (liveEditActive && liveEditDraftActions != null) {
            liveEditDraftActions = updatedActions.toMutableList()
            liveEditDirty = true
            // 插入新操作点应立即体现在基础动作中，避免退出实时编辑后丢失
            liveEditBaseActions = updatedActions.map { it.copy(action = it.action.copy()) }
        }
        _uiState.value = current.copy(
            actions = updatedActions,
            focusedActionId = insertedAction.id,
            focusedPointIndex = null,
            isModified = true
        )
        return insertIndex.coerceIn(0, updatedActions.lastIndex)
    }

    private fun normalizeMultiTapPoints(
        points: List<*>?,
        screenWidth: Int,
        screenHeight: Int
    ): List<MutableMap<String, Any>> {
        if (points.isNullOrEmpty()) return emptyList()
        val normalized = mutableListOf<MutableMap<String, Any>>()
        points.forEach { item ->
            val raw = when (item) {
                is Map<*, *> -> {
                    val x = safeToFloat(item["x"])
                    val y = safeToFloat(item["y"])
                    if (x != 0f || y != 0f) x to y else null
                }
                is List<*> -> {
                    val x = safeToFloat(item.getOrNull(0))
                    val y = safeToFloat(item.getOrNull(1))
                    if (x != 0f || y != 0f) x to y else null
                }
                else -> null
            } ?: return@forEach

            val resolved = resolvePointToScreen(raw.first, raw.second, screenWidth, screenHeight)
            normalized.add(mutableMapOf("x" to resolved.first, "y" to resolved.second))
        }
        return normalized
    }

    private fun normalizeSwipePath(
        path: List<*>,
        screenWidth: Int,
        screenHeight: Int
    ): List<MutableMap<String, Any>> {
        if (path.isEmpty()) return emptyList()
        val normalized = mutableListOf<MutableMap<String, Any>>()
        path.forEach { item ->
            val raw = when (item) {
                is Map<*, *> -> {
                    val x = safeToFloat(item["x"])
                    val y = safeToFloat(item["y"])
                    if (x != 0f || y != 0f) x to y else null
                }
                is List<*> -> {
                    val x = safeToFloat(item.getOrNull(0))
                    val y = safeToFloat(item.getOrNull(1))
                    if (x != 0f || y != 0f) x to y else null
                }
                else -> null
            } ?: return@forEach

            val resolved = resolvePointToScreen(raw.first, raw.second, screenWidth, screenHeight)
            normalized.add(mutableMapOf("x" to resolved.first, "y" to resolved.second))
        }
        return normalized
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

    private fun normalizeActionParamsForSave(
        action: RecordedAction,
        screenWidth: Int,
        screenHeight: Int
    ): RecordedAction {
        if (screenWidth <= 0 || screenHeight <= 0) {
            return action
        }
        val params = action.params.toMutableMap()
        when (action.actionType) {
            "tap", "longPress" -> {
                val directX = safeToFloat(params["x"])
                val directY = safeToFloat(params["y"])
                val element = params["element"] as? List<*>
                val elementMap = params["element"] as? Map<*, *>
                val raw = when {
                    directX != 0f || directY != 0f -> directX to directY
                    element != null -> {
                        val ex = safeToFloat(element.getOrNull(0))
                        val ey = safeToFloat(element.getOrNull(1))
                        ex to ey
                    }
                    elementMap != null -> {
                        val ex = safeToFloat(elementMap["x"])
                        val ey = safeToFloat(elementMap["y"])
                        ex to ey
                    }
                    else -> null
                }
                if (raw != null) {
                    val abs = resolvePointToScreen(raw.first, raw.second, screenWidth, screenHeight)
                    val relX = abs.first / screenWidth.toFloat()
                    val relY = abs.second / screenHeight.toFloat()
                    params["element"] = listOf(relX, relY)
                    params["x"] = abs.first.toInt()
                    params["y"] = abs.second.toInt()
                }
                params["screen_width"] = screenWidth
                params["screen_height"] = screenHeight
            }
            "multiTap" -> {
                val normalizedPoints = normalizeMultiTapPoints(
                    params["points"] as? List<*>,
                    screenWidth,
                    screenHeight
                )
                if (normalizedPoints.isNotEmpty()) {
                    val relPoints = normalizedPoints.map { point ->
                        val px = (point["x"] as? Number)?.toFloat() ?: 0f
                        val py = (point["y"] as? Number)?.toFloat() ?: 0f
                        listOf(
                            px / screenWidth.toFloat(),
                            py / screenHeight.toFloat()
                        )
                    }
                    params["points"] = relPoints
                }
                params["screen_width"] = screenWidth
                params["screen_height"] = screenHeight
            }
            "swipe", "longPressSwipe" -> {
                val path = params["path"] as? List<*>
                if (!path.isNullOrEmpty()) {
                    val normalizedPath = normalizeSwipePath(path, screenWidth, screenHeight)
                    if (normalizedPath.isNotEmpty()) {
                        val relPath = normalizedPath.map { point ->
                            val px = (point["x"] as? Number)?.toFloat() ?: 0f
                            val py = (point["y"] as? Number)?.toFloat() ?: 0f
                            listOf(
                                px / screenWidth.toFloat(),
                                py / screenHeight.toFloat()
                            )
                        }
                        params["path"] = relPath
                    }
                } else {
                    val startX = safeToFloat(params["startX"])
                    val startY = safeToFloat(params["startY"])
                    val endX = safeToFloat(params["endX"])
                    val endY = safeToFloat(params["endY"])
                    val start = if (startX != 0f || startY != 0f) {
                        resolvePointToScreen(startX, startY, screenWidth, screenHeight)
                    } else {
                        null
                    }
                    val end = if (endX != 0f || endY != 0f) {
                        resolvePointToScreen(endX, endY, screenWidth, screenHeight)
                    } else {
                        null
                    }
                    if (start != null) {
                        params["start"] = listOf(
                            start.first / screenWidth.toFloat(),
                            start.second / screenHeight.toFloat()
                        )
                    }
                    if (end != null) {
                        params["end"] = listOf(
                            end.first / screenWidth.toFloat(),
                            end.second / screenHeight.toFloat()
                        )
                    }
                }
                params["screen_width"] = screenWidth
                params["screen_height"] = screenHeight
            }
        }
        params["action_type"] = action.actionType
        val newParamsJson = gson.toJson(params)
        return action.copy(paramsJson = newParamsJson)
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

    /**
     * 更新指定操作的进度位置（用于拖动操作点）
     * 增加了最小时间间隔限制，防止操作点过于接近导致卡顿
     * 第一个操作点可以从 0s 开始，不受最小间隔限制
     */
    fun updateProgress(actionId: String, newProgress: Float) {
        val current = currentState()
        val actionIndex = getActionIndex(current.actions, actionId)
        if (actionIndex == -1) return

        // 获取当前排序下前后操作的进度值
        val prevProgress = if (actionIndex > 0) {
            current.actions[actionIndex - 1].action.progress
        } else {
            0f
        }

        val nextProgress = if (actionIndex < current.actions.size - 1) {
            current.actions[actionIndex + 1].action.progress
        } else {
            1f
        }

        // 计算最小进度间隔（基于当前总时长）
        val minProgressInterval = if (current.totalDuration > 0) {
            MIN_ACTION_INTERVAL.toFloat() / current.totalDuration
        } else {
            0.01f // 默认最小间隔 1%
        }

        // 限制新进度在安全范围内，确保与前后操作保持最小间隔
        // 第一个操作点（index == 0）可以从 0f 开始，不需要最小间隔
        val minAllowedProgress = if (actionIndex == 0) {
            0f  // 第一个操作点可以在 0s 位置
        } else {
            prevProgress + minProgressInterval  // 其他操作点需要保持最小间隔
        }

        val clampedProgress = newProgress
            .coerceAtLeast(minAllowedProgress)
            .coerceAtMost(nextProgress - minProgressInterval)
            .coerceIn(0f, 1f)

        android.util.Log.d("RecordingEditViewModel",
            "Action progress updated"
        )

        val updatedActions = current.actions.map { editableAction ->
            if (editableAction.id == actionId) {
                editableAction.copy(
                    action = editableAction.action.copy(progress = clampedProgress)
                )
            } else {
                editableAction
            }
        }

        if (liveEditActive && liveEditDraftActions != null) {
            liveEditDraftActions = updatedActions.toMutableList()
            liveEditDirty = true
            liveEditBaseActions = updatedActions.map { it.copy(action = it.action.copy()) }
        }
        if (liveEditActive && liveEditDraftActions != null) {
            liveEditDraftActions = updatedActions.toMutableList()
            liveEditDirty = true
            liveEditBaseActions = updatedActions.map { it.copy(action = it.action.copy()) }
        }
        _uiState.value = current.copy(
            actions = updatedActions,
            isModified = true
        )
    }

    private fun getActionIndex(actions: List<EditableAction>, actionId: String): Int {
        if (actions !== cachedActionsRef) {
            cachedActionsRef = actions
            cachedActionIndexMap = actions.mapIndexed { index, action ->
                action.id to index
            }.toMap()
        }
        return cachedActionIndexMap[actionId] ?: -1
    }

    /**
     * 更新指定操作的时间戳（向后兼容，已弃用）
     * @deprecated 使用 updateProgress 代替
     */
    @Deprecated("Use updateProgress instead", ReplaceWith("updateProgress(actionId, timeToProgress(newTimestamp, currentState().totalDuration))"))
    fun updateTimestamp(actionId: String, newTimestamp: Long) {
        val current = currentState()
        val progress = timeToProgress(newTimestamp, current.totalDuration)
        updateProgress(actionId, progress)
    }

    /**
     * 缩放所有操作的时间戳（用于调整整体时间轴长度）
     * @param newTotalDuration 新的总时长
     */
    @Suppress("DEPRECATION")
    fun scaleTimeline(newTotalDuration: Long) {
        val current = currentState()
        if (current.actions.isEmpty() || current.totalDuration == 0L) return

        val scale = newTotalDuration.toFloat() / current.totalDuration
        val updatedActions = current.actions.map { editableAction ->
            editableAction.copy(
                action = editableAction.action.copy(
                    timestamp = (editableAction.action.timestamp * scale).toLong().coerceAtLeast(0)
                )
            )
        }
        _uiState.value = current.copy(
            actions = updatedActions,
            isModified = true
        )
    }

    /**
     * 更新总时长（保持操作点的绝对时间位置不变）
     * 注意：操作点的 progress 会根据新的总时长重新计算，以保持其在时间轴上的绝对秒数不变
     * @param newTotalDuration 新的总时长（毫秒），范围 [max(1000, 最后一个操作点时间), 600000ms]
     */
    fun updateTotalDuration(newTotalDuration: Long) {
        val current = currentState()
        if (current.totalDuration <= 0) return

        // 重新计算当前所有操作点的最大绝对时间（以防操作点位置刚刚被修改）
        val maxActionTime = current.actions.maxOfOrNull { (it.action.progress * current.totalDuration).toLong() } ?: 0L

        // 动态计算最小限制：不能短于 1s，且不能短于最后一个操作点的位置
        val dynamicMinLimit = maxOf(1000L, maxActionTime)
        val adjustedDuration = newTotalDuration.coerceIn(dynamicMinLimit, 600000L)

        android.util.Log.d("RecordingEditViewModel", "Total duration updated")

        if (adjustedDuration == current.totalDuration) return

        // 保持绝对时间不变，重新计算每个操作点的 progress
        val updatedActions = current.actions.map { editableAction ->
            val absoluteTime = editableAction.action.progress * current.totalDuration
            val newProgress = (absoluteTime / adjustedDuration.toFloat()).coerceIn(0f, 1f)
            editableAction.copy(
                action = editableAction.action.copy(progress = newProgress)
            )
        }

        _uiState.value = current.copy(
            baseActionsDuration = adjustedDuration - current.startDelay - current.endDelay,
            actions = updatedActions,
            isModified = true
        )
    }

    /**
     * 更新基准操作时长（调整时间轴长度）
     * 注意：操作点的 progress 保持不变，但实际时间会按比例缩放
     * @param newDuration 新的基准时长（毫秒），范围 1000ms - 600000ms
     */
    fun updateBaseActionsDuration(newDuration: Long) {
        val current = currentState()
        val adjustedDuration = newDuration.coerceIn(100L, 600000L)

        android.util.Log.d("RecordingEditViewModel", "Base actions duration updated")

        _uiState.value = current.copy(
            baseActionsDuration = adjustedDuration,
            isModified = true
        )
    }

    /**
     * 更新开始延迟（调整起始节点）
     * @param newStartDelay 新的开始延迟（毫秒），不能为负数
     */
    fun updateStartDelay(newStartDelay: Long) {
        val current = currentState()
        val adjustedDelay = newStartDelay.coerceAtLeast(0L)

        android.util.Log.d("RecordingEditViewModel", "Start delay updated")

        _uiState.value = current.copy(
            startDelay = adjustedDelay,
            isModified = true
        )
    }

    /**
     * 更新结束延迟（调整结束节点）
     * @param newEndDelay 新的结束延迟（毫秒），不能为负数
     */
    fun updateEndDelay(newEndDelay: Long) {
        val current = currentState()
        val adjustedDelay = newEndDelay.coerceAtLeast(0L)

        android.util.Log.d("RecordingEditViewModel", "End delay updated")

        _uiState.value = current.copy(
            endDelay = adjustedDelay,
            isModified = true
        )
    }

    /**
     * 删除指定操作
     */
    fun deleteAction(actionId: String) {
        val current = currentState()
        val sourceActions = if (liveEditActive) {
            liveEditDraftActions?.toList() ?: current.actions
        } else {
            current.actions
        }
        val updatedActions = sourceActions.filter { it.id != actionId }
        val updatedFocusedActionId = if (current.focusedActionId == actionId) null else current.focusedActionId
        val updatedFocusedPointIndex = if (current.focusedActionId == actionId) null else current.focusedPointIndex
        if (liveEditActive && liveEditDraftActions != null) {
            liveEditDraftActions = updatedActions.toMutableList()
            liveEditDirty = true
            liveEditBaseActions = updatedActions.map { it.copy(action = it.action.copy()) }
        }
        _uiState.value = current.copy(
            actions = updatedActions,
            focusedActionId = updatedFocusedActionId,
            focusedPointIndex = updatedFocusedPointIndex,
            isModified = true
        )
    }

    /**
     * 获取指定操作的可移动时间范围
     * 返回 Pair(minTimestamp, maxTimestamp)，如果操作不存在则返回 null
     */
    @Suppress("DEPRECATION")
    fun getActionTimeRange(actionId: String): Pair<Long, Long>? {
        val current = currentState()
        val actionIndex = current.actions.indexOfFirst { it.id == actionId }
        if (actionIndex == -1) return null

        val minTimestamp = if (actionIndex > 0) {
            current.actions[actionIndex - 1].action.timestamp + MIN_ACTION_INTERVAL
        } else {
            0L
        }

        val maxTimestamp = if (actionIndex < current.actions.size - 1) {
            current.actions[actionIndex + 1].action.timestamp - MIN_ACTION_INTERVAL
        } else {
            Long.MAX_VALUE
        }

        return Pair(minTimestamp, maxTimestamp)
    }

    /**
     * 切换操作的选中状态
     */
    fun toggleSelection(actionId: String) {
        val current = currentState()
        val updatedActions = current.actions.map { editableAction ->
            if (editableAction.id == actionId) {
                editableAction.copy(isSelected = !editableAction.isSelected)
            } else {
                editableAction
            }
        }
        _uiState.value = current.copy(actions = updatedActions)
    }

    /**
     * 单选模式切换（用于实时编辑模式）
     */
    fun toggleSingleSelection(actionId: String) {
        val current = currentState()
        val target = current.actions.firstOrNull { it.id == actionId }
        val shouldSelect = target?.isSelected != true
        val updatedActions = current.actions.map { editableAction ->
            when {
                editableAction.id == actionId -> editableAction.copy(isSelected = shouldSelect)
                else -> editableAction.copy(isSelected = false)
            }
        }
        val focusedActionId = if (shouldSelect) actionId else null
        val focusedPointIndex = if (shouldSelect) {
            resolvePrimaryPointIndex(actionId, null, current)
        } else {
            null
        }
        Log.d(
            "LiveEditPanel",
            "Single selection toggled: selected=$shouldSelect"
        )
        _uiState.value = current.copy(
            actions = updatedActions,
            focusedActionId = focusedActionId,
            focusedPointIndex = focusedPointIndex
        )
    }

    /**
     * 切换操作的展开状态（独占模式）
     * 展开当前操作点时，自动折叠其他所有操作点
     */
    fun toggleExpand(actionId: String) {
        val current = currentState()
        val updatedActions = current.actions.map { editableAction ->
            if (editableAction.id == actionId) {
                // 如果点击的是当前已展开的，则折叠；否则展开
                editableAction.copy(isExpanded = !editableAction.isExpanded)
            } else {
                // 其他所有操作点都折叠
                editableAction.copy(isExpanded = false)
            }
        }
        _uiState.value = current.copy(actions = updatedActions)
    }

    /**
     * 展开指定操作点，并折叠其他所有操作点（用于时间轴点击）
     * @param actionId 要展开的操作点 ID
     * @return 操作点在列表中的索引，-1 表示未找到
     */
    fun expandSingleAction(actionId: String): Int {
        val current = currentState()
        val targetIndex = current.actions.indexOfFirst { it.id == actionId }
        if (targetIndex == -1) return -1

        val updatedActions = current.actions.map { editableAction ->
            editableAction.copy(
                isExpanded = editableAction.id == actionId  // 只展开目标操作点
            )
        }

        _uiState.value = current.copy(actions = updatedActions)

        android.util.Log.d("RecordingEditViewModel",
            "Single action expanded"
        )

        return targetIndex
    }

    /**
     * 全选
     */
    fun selectAll() {
        val current = currentState()
        val updatedActions = current.actions.map {
            it.copy(isSelected = true)
        }
        _uiState.value = current.copy(actions = updatedActions)
    }

    /**
     * 取消全选
     */
    fun deselectAll() {
        val current = currentState()
        val updatedActions = current.actions.map {
            it.copy(isSelected = false)
        }
        _uiState.value = current.copy(
            actions = updatedActions,
            focusedActionId = null,
            focusedPointIndex = null
        )
    }

    /**
     * 批量删除选中的操作
     */
    fun batchDelete() {
        val current = currentState()
        val updatedActions = current.actions.filter { !it.isSelected }
        _uiState.value = current.copy(
            actions = updatedActions,
            isModified = true
        )
    }

    /**
     * 批量更新延迟（统一设置）
     */
    fun batchUpdateDelay(delay: Long) {
        val current = currentState()
        val updatedActions = current.actions.map { editableAction ->
            if (editableAction.isSelected) {
                editableAction.copy(
                    action = editableAction.action.copy(delayBefore = delay)
                )
            } else {
                editableAction
            }
        }
        _uiState.value = current.copy(
            actions = updatedActions,
            isModified = true
        )
    }

    /**
     * 批量缩放延迟
     */
    fun batchScaleDelay(scale: Float) {
        val current = currentState()
        val updatedActions = current.actions.map { editableAction ->
            if (editableAction.isSelected) {
                val newDelay = (editableAction.action.delayBefore * scale).toLong()
                editableAction.copy(
                    action = editableAction.action.copy(delayBefore = newDelay)
                )
            } else {
                editableAction
            }
        }
        _uiState.value = current.copy(
            actions = updatedActions,
            isModified = true
        )
    }

    /**
     * 计算指定操作点距离前一个操作点的间隔
     * @param actionId 操作点 ID
     * @return 间隔值（progress 单位，0-1+ 范围）
     */
    fun getActionInterval(actionId: String): Float {
        val current = currentState()
        val actionIndex = getActionIndex(current.actions, actionId)
        if (actionIndex == -1) return 0f

        val currentProgress = current.actions[actionIndex].action.progress
        val prevProgress = if (actionIndex > 0) {
            current.actions[actionIndex - 1].action.progress
        } else {
            0f
        }

        return currentProgress - prevProgress
    }

    /**
     * 计算指定操作点可以设置的最大间隔（基于后一个操作点位置）
     * @param actionId 操作点 ID
     * @return 最大间隔值（progress 单位），如果是最后一个操作点则返回 1.0
     */
    fun getMaxActionInterval(actionId: String): Float {
        val current = currentState()
        val actionIndex = getActionIndex(current.actions, actionId)
        if (actionIndex == -1) return 1f

        val prevProgress = if (actionIndex > 0) {
            current.actions[actionIndex - 1].action.progress
        } else {
            0f
        }

        // 计算最小间隔（100ms）在 progress 中的值
        val minIntervalProgress = if (current.totalDuration > 0) {
            MIN_ACTION_INTERVAL.toFloat() / current.totalDuration.toFloat()
        } else {
            0.01f
        }

        // 如果不是最后一个操作点，最大间隔受后一个操作点限制
        return if (actionIndex < current.actions.size - 1) {
            val nextProgress = current.actions[actionIndex + 1].action.progress
            // 最大位置 = 后一个操作点位置 - 最小间隔
            // 最大间隔 = 最大位置 - 前一个操作点位置
            (nextProgress - minIntervalProgress - prevProgress).coerceAtLeast(minIntervalProgress)
        } else {
            // 最后一个操作点，最大间隔为到达 1.0
            (1f - prevProgress).coerceAtLeast(0f)
        }
    }

    /**
     * 设置指定操作点距离前一个操作点的间隔
     * 操作点不能超过时间轴长度（progress 限制在 1.0 以内）
     * 第一个操作点可以设置间隔为 0，即从 0s 位置开始
     * 操作点不能超过后一个操作点位置的前 0.1s（100ms）
     * 如果设置后会超过后续操作点，后续操作点会自动后移，但不会超过 1.0
     * @param actionId 操作点 ID
     * @param interval 新的间隔（progress 单位，0 表示紧贴前一个操作点或起点）
     * @param cascadeForward 如果间隔缩短且前移，后续点是否整体前移
     */
    fun setActionInterval(actionId: String, interval: Float, cascadeForward: Boolean = true) {
        var current = currentState()
        val actionIndex = getActionIndex(current.actions, actionId)
        if (actionIndex == -1) return

        // 先按“绝对时间”计算目标间隔，必要时扩展时间轴
        val prevProgress = if (actionIndex > 0) {
            current.actions[actionIndex - 1].action.progress
        } else {
            0f
        }
        val prevAbsTime = prevProgress * current.totalDuration
        val intervalMillis = (interval.coerceAtLeast(0f) * current.totalDuration).coerceAtLeast(0f)
        val desiredAbsTime = prevAbsTime + intervalMillis
        val desiredTotalDuration = (desiredAbsTime + current.endDelay)
            .toLong()
            .coerceAtLeast(current.totalDuration)

        if (desiredTotalDuration > current.totalDuration) {
            updateTotalDuration(desiredTotalDuration)
            current = currentState()
        }

        // 计算新的 progress（基于可能扩展后的总时长）
        val adjustedPrevProgress = if (actionIndex > 0) {
            current.actions[actionIndex - 1].action.progress
        } else {
            0f  // 第一个操作点的前置位置是 0f（时间轴起点）
        }
        val adjustedInterval = if (current.totalDuration > 0) {
            intervalMillis / current.totalDuration
        } else {
            0f
        }

        // 计算最小间隔（100ms）在 progress 中的值
        val minIntervalProgress = if (current.totalDuration > 0) {
            MIN_ACTION_INTERVAL.toFloat() / current.totalDuration.toFloat()
        } else {
            0.01f
        }

        // 计算允许的最大 progress（如果允许级联，则后续点会整体后移，不受下一个点限制）
        val maxAllowedProgress = if (cascadeForward) {
            1f
        } else if (actionIndex < current.actions.size - 1) {
            val nextProgress = current.actions[actionIndex + 1].action.progress
            (nextProgress - minIntervalProgress).coerceAtLeast(prevProgress + minIntervalProgress)
        } else {
            1f  // 最后一个操作点，限制为 1.0
        }

        // 限制在时间轴范围内，并且不能超过后一个操作点的前 0.1s
        // 允许 interval 为 0，第一个操作点可以从 0s 开始
        val targetProgress = adjustedPrevProgress + adjustedInterval
        val newProgress = targetProgress.coerceIn(0f, maxAllowedProgress)
        val oldProgress = current.actions[actionIndex].action.progress

        android.util.Log.d("RecordingEditViewModel",
            "Action interval updated: cascadeForward=$cascadeForward"
        )

        // 更新操作点列表
        val updatedActions = current.actions.toMutableList()
        updatedActions[actionIndex] = updatedActions[actionIndex].copy(
            action = updatedActions[actionIndex].action.copy(progress = newProgress)
        )

        if (cascadeForward && newProgress != oldProgress) {
            // 前移/后移：后续操作点整体平移，保持原有间隔
            val delta = newProgress - oldProgress
            for (i in actionIndex + 1 until updatedActions.size) {
                val shifted = (current.actions[i].action.progress + delta).coerceIn(0f, 1f)
                updatedActions[i] = updatedActions[i].copy(
                    action = updatedActions[i].action.copy(progress = shifted)
                )
                android.util.Log.d("RecordingEditViewModel", "Cascade shift applied")
            }
        } else {
            // 级联后移后续操作点（如果需要），但不超过 1.0
            for (i in actionIndex + 1 until updatedActions.size) {
                val prevActionProgress = updatedActions[i - 1].action.progress
                val currentActionProgress = updatedActions[i].action.progress

                // 如果前一个操作点已经到达 1.0，后续操作点无法后移，停止处理
                if (prevActionProgress >= 1f) {
                    android.util.Log.d("RecordingEditViewModel",
                        "Cannot cascade action: previous action reached max progress"
                    )
                    break
                }

                // 计算原始间隔
                val originalInterval = current.actions[i].action.progress - current.actions[i - 1].action.progress

                // 如果当前操作点会被前一个操作点覆盖或超过，需要后移
                if (currentActionProgress <= prevActionProgress) {
                    // 后移，但不超过 1.0
                    val newActionProgress = (prevActionProgress + originalInterval).coerceAtMost(1f)
                    updatedActions[i] = updatedActions[i].copy(
                        action = updatedActions[i].action.copy(progress = newActionProgress)
                    )
                    android.util.Log.d("RecordingEditViewModel",
                        "Cascading action timing"
                    )
                } else {
                    // 后续操作点没有被影响，停止级联
                    break
                }
            }
        }

        android.util.Log.d("RecordingEditViewModel",
            "setActionInterval complete: all actions within [0, 1.0]"
        )

        if (liveEditActive && liveEditDraftActions != null) {
            liveEditDraftActions = updatedActions.toMutableList()
            liveEditDirty = true
            liveEditBaseActions = updatedActions.map { it.copy(action = it.action.copy()) }
        }
        _uiState.value = current.copy(
            actions = updatedActions,
            isModified = true
        )
    }

    /**
     * 批量设置选中操作点的间隔
     * 每个选中的操作点都会设置相同的间隔（相对于它的前一个操作点）
     * 第一个操作点可以设置间隔为 0，即从 0s 位置开始
     * 未选中的操作点如果被选中的操作点覆盖，会自动后移保持原有间隔
     * 所有操作点不能超过时间轴长度（progress 限制在 1.0 以内）
     * @param interval 新的间隔（progress 单位，0 表示紧贴前一个操作点或起点）
     */
    fun batchSetInterval(interval: Float) {
        var current = currentState()
        val intervalMillis = (interval.coerceAtLeast(0f) * current.totalDuration).coerceAtLeast(0f)
        var prevDesiredAbs = 0f
        var prevOriginalAbs = 0f
        var maxDesiredAbs = 0f

        current.actions.forEachIndexed { index, editableAction ->
            val originalAbs = editableAction.action.progress * current.totalDuration
            val originalInterval = if (index > 0) originalAbs - prevOriginalAbs else originalAbs
            val desiredAbs = if (editableAction.isSelected) {
                prevDesiredAbs + intervalMillis
            } else {
                maxOf(originalAbs, prevDesiredAbs + originalInterval)
            }
            maxDesiredAbs = maxOf(maxDesiredAbs, desiredAbs)
            prevDesiredAbs = desiredAbs
            prevOriginalAbs = originalAbs
        }

        val desiredTotalDuration = (maxDesiredAbs + current.endDelay)
            .toLong()
            .coerceAtLeast(current.totalDuration)
        if (desiredTotalDuration > current.totalDuration) {
            updateTotalDuration(desiredTotalDuration)
            current = currentState()
        }

        val adjustedInterval = if (current.totalDuration > 0) {
            intervalMillis / current.totalDuration
        } else {
            0f
        }
        val updatedActions = mutableListOf<EditableAction>()

        current.actions.forEachIndexed { index, editableAction ->
            val prevProgress = if (index > 0) {
                updatedActions[index - 1].action.progress
            } else {
                0f
            }

            // 如果前一个操作点已经到达 1.0，当前操作点无法设置，保持原位置
            if (prevProgress >= 1f) {
                android.util.Log.d("RecordingEditViewModel",
                    "batchSetInterval: index=$index reached max, keeping original position"
                )
                updatedActions.add(editableAction)
                return@forEachIndexed
            }

            if (editableAction.isSelected) {
                // 选中的操作点：设置新的间隔，但不超过 1.0
                val newProgress = (prevProgress + adjustedInterval).coerceAtMost(1f)

                android.util.Log.d("RecordingEditViewModel", "Selected action interval updated")

                updatedActions.add(
                    editableAction.copy(
                        action = editableAction.action.copy(progress = newProgress)
                    )
                )
            } else {
                // 未选中的操作点：检查是否被前一个操作点覆盖
                val currentProgress = editableAction.action.progress

                if (currentProgress <= prevProgress) {
                    // 被覆盖了，需要后移，保持与前一个操作点的原始间隔，但不超过 1.0
                    val originalInterval = current.actions[index].action.progress -
                                          current.actions[index - 1].action.progress
                    val newProgress = (prevProgress + originalInterval).coerceAtMost(1f)

                android.util.Log.d("RecordingEditViewModel", "Cascading action interval updated")

                    updatedActions.add(
                        editableAction.copy(
                            action = editableAction.action.copy(progress = newProgress)
                        )
                    )
                } else {
                    // 没有被覆盖，保持原位置
                    updatedActions.add(editableAction)
                }
            }
        }

        android.util.Log.d("RecordingEditViewModel",
            "batchSetInterval complete: selectedCount=${current.selectedCount}, " +
            "all actions within [0, 1.0]"
        )

        if (liveEditActive && liveEditDraftActions != null) {
            liveEditDraftActions = updatedActions.toMutableList()
            liveEditDirty = true
            liveEditBaseActions = updatedActions.map { it.copy(action = it.action.copy()) }
        }
        _uiState.value = current.copy(
            actions = updatedActions,
            isModified = true
        )
    }

    /**
     * 批量缩放选中操作点的间隔（相对于前一个操作点）
     * 每个选中操作点的间隔按比例缩放，未选中操作点保持相对间隔，
     * 如有覆盖会自动后移，确保顺序且不超过 1.0
     * @param scale 缩放比例（0.5 表示减半，2.0 表示加倍）
     */
    fun batchScaleInterval(scale: Float) {
        val current = currentState()
        val updatedActions = mutableListOf<EditableAction>()
        val normalizedScale = if (scale <= 0f) 0f else scale

        current.actions.forEachIndexed { index, editableAction ->
            val prevProgress = if (index > 0) {
                updatedActions[index - 1].action.progress
            } else {
                0f
            }

            if (prevProgress >= 1f) {
                updatedActions.add(editableAction)
                return@forEachIndexed
            }

            if (editableAction.isSelected) {
                val originalInterval = if (index > 0) {
                    current.actions[index].action.progress - current.actions[index - 1].action.progress
                } else {
                    current.actions[index].action.progress
                }
                val scaledInterval = (originalInterval * normalizedScale).coerceAtLeast(0f)
                val newProgress = (prevProgress + scaledInterval).coerceAtMost(1f)
                updatedActions.add(
                    editableAction.copy(
                        action = editableAction.action.copy(progress = newProgress)
                    )
                )
            } else {
                val currentProgress = editableAction.action.progress
                if (currentProgress <= prevProgress) {
                    val originalInterval = if (index > 0) {
                        current.actions[index].action.progress - current.actions[index - 1].action.progress
                    } else {
                        current.actions[index].action.progress
                    }
                    val newProgress = (prevProgress + originalInterval).coerceAtMost(1f)
                    updatedActions.add(
                        editableAction.copy(
                            action = editableAction.action.copy(progress = newProgress)
                        )
                    )
                } else {
                    updatedActions.add(editableAction)
                }
            }
        }

        _uiState.value = current.copy(
            actions = updatedActions,
            isModified = true
        )
    }

    /**
     * 重新排序操作
     */
    fun reorderActions(fromIndex: Int, toIndex: Int) {
        val current = currentState()
        val updatedActions = current.actions.toMutableList()
        if (fromIndex in updatedActions.indices && toIndex in updatedActions.indices) {
            val item = updatedActions.removeAt(fromIndex)
            updatedActions.add(toIndex, item)
            _uiState.value = current.copy(
                actions = updatedActions,
                isModified = true
            )
        }
    }

    /**
     * 获取最终的录制结果（用于保存）
     */
    @Suppress("DEPRECATION")
    fun getFinalResult(): RecordingResult {
        val current = currentState()
        val totalDuration = current.totalDuration

        val updatedActions = current.actions.mapIndexed { index, editableAction ->
            val action = editableAction.action
            // 计算基于 progress 的绝对时间戳
            val absoluteTime = (action.progress * totalDuration).toLong()

            // 计算 delayBefore (相对于前一个动作的间隔)
            val prevAbsoluteTime = if (index > 0) {
                (current.actions[index - 1].action.progress * totalDuration).toLong()
            } else {
                0L
            }
            val delayBefore = (absoluteTime - prevAbsoluteTime).coerceAtLeast(0L)

            val normalizedAction = normalizeActionParamsForSave(
                action = action,
                screenWidth = current.screenWidth,
                screenHeight = current.screenHeight
            )
            normalizedAction.copy(
                timestamp = absoluteTime,
                delayBefore = delayBefore
            )
        }
        return RecordingResult(
            id = initialResult.id,
            title = current.title,
            screenshotPath = current.screenshotPath,
            actions = updatedActions,
            screenWidth = current.screenWidth,
            screenHeight = current.screenHeight,
            totalDuration = totalDuration // 包含总时长
        )
    }

    /**
     * 验证数据
     */
    fun validate(): ValidationResult {
        val current = currentState()
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (current.title.isBlank()) {
            errors.add("标题不能为空")
        }

        if (current.actions.isEmpty()) {
            errors.add("至少需要一个操作")
        }

        current.actions.forEachIndexed { index, editableAction ->
            if (editableAction.action.delayBefore > 3000) {
                warnings.add("操作 #${index + 1} 延迟过长 (${editableAction.action.delayBefore}ms)")
            }
        }

        if (current.actions.size > 50) {
            warnings.add("操作数量较多 (${current.actions.size}个)，播放可能较慢")
        }

        return ValidationResult(errors, warnings)
    }

    /**
     * Factory for creating ViewModel with initial result
     */
    class Factory(
        private val initialResult: RecordingResult
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(RecordingEditViewModel::class.java)) {
                return RecordingEditViewModel(initialResult) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

/**
 * 验证结果
 */
data class ValidationResult(
    val errors: List<String>,
    val warnings: List<String>
) {
    val isValid: Boolean
        get() = errors.isEmpty()
}
