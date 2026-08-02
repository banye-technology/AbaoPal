package com.withcareer.screenpal_android.recording

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.IgnoredOnParcel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 录制的操作数据类
 * 记录用户在录制过程中执行的单个操作
 *
 * @property actionType 操作类型：tap, swipe, longPress, type, scroll, multiTap
 * @property paramsJson 操作参数的JSON字符串（用于序列化）
 * @property progress 操作在时间轴上的相对位置（0.0 到 1.0），时间轴缩放时保持相对位置不变
 * @property timestamp 相对录制开始的时间戳（毫秒），保留用于向后兼容
 * @property delayBefore 与上一步骤的时间间隔（毫秒），可在编辑时调整
 * @property targetPackage 操作时的目标应用包名，用于回放时切换到正确的应用
 *
 * 参数格式示例：
 * - tap: {"x": 100, "y": 200}
 * - swipe: {"path": [{"x":100,"y":200}, {"x":150,"y":250}, {"x":300,"y":400}]}
 * - swipe (旧格式): {"startX": 100, "startY": 200, "endX": 300, "endY": 400}
 * - longPress: {"x": 100, "y": 200, "duration": 800}
 * - type: {"text": "hello"}
 * - scroll: {"direction": "up"}
 * - multiTap: {"points": [{"x":100,"y":200}, {"x":300,"y":400}]}
 *
 */
@Parcelize
data class RecordedAction(
    val actionType: String,
    val paramsJson: String,
    val progress: Float = 0f,  // 0.0 到 1.0 之间的相对位置
    @Deprecated("Use progress instead. Kept for backward compatibility.")
    val timestamp: Long = 0,
    val delayBefore: Long = 0,
    val targetPackage: String? = null  // 目标应用包名
) : Parcelable {

    companion object {
        private val gson = Gson()

        /**
         * 从 params Map 创建 RecordedAction（使用 timestamp）
         */
        fun create(
            actionType: String,
            params: Map<String, Any>,
            timestamp: Long,
            delayBefore: Long = 0,
            targetPackage: String? = null
        ): RecordedAction {
            return RecordedAction(
                actionType = actionType,
                paramsJson = gson.toJson(params),
                progress = 0f,  // 需要后续转换
                timestamp = timestamp,
                delayBefore = delayBefore,
                targetPackage = targetPackage
            )
        }

        /**
         * 从 timestamp 转换创建 RecordedAction
         * @param firstTime 第一个操作的时间戳（用于计算相对位置）
         * @param duration 操作序列的总时长（用于计算相对位置）
         */
        fun fromTimestamp(
            actionType: String,
            paramsJson: String,
            timestamp: Long,
            firstTime: Long,
            duration: Long,
            delayBefore: Long = 0
        ): RecordedAction {
            val progress = if (duration > 0) {
                ((timestamp - firstTime).toFloat() / duration).coerceIn(0f, 1f)
            } else {
                0f
            }
            return RecordedAction(
                actionType = actionType,
                paramsJson = paramsJson,
                progress = progress,
                timestamp = timestamp,
                delayBefore = delayBefore
            )
        }
    }

    @IgnoredOnParcel
    @Transient
    private var paramsCache: Map<String, Any>? = null

    /**
     * 获取参数 Map（缓存解析结果，避免重复 JSON 解析）
     */
    val params: Map<String, Any>
        get() {
            paramsCache?.let { return it }
            val parsed = try {
                val type = object : TypeToken<Map<String, Any>>() {}.type
                gson.fromJson<Map<String, Any>>(paramsJson, type) ?: emptyMap()
            } catch (e: Exception) {
                emptyMap()
            }
            paramsCache = parsed
            return parsed
        }

    /**
     * 纠正异常的 actionType（用于编辑展示/保存）
     * 某些机型或链路可能错误标记为 tap，基于参数进行修正。
     */
    fun normalizeActionType(): RecordedAction {
        val map = params
        val correctedType = when {
            actionType == "tap" && map.containsKey("path") -> "swipe"
            actionType == "tap" && map.containsKey("startX") && map.containsKey("endX") -> "swipe"
            actionType == "tap" && map.containsKey("duration") -> "longPress"
            actionType == "tap" && map.containsKey("text") -> "type"
            actionType == "tap" && map.containsKey("direction") -> "scroll"
            actionType == "tap" && map.containsKey("points") -> "multiTap"
            else -> actionType
        }
        return if (correctedType == actionType) this else copy(actionType = correctedType)
    }
}

/**
 * 录制结果数据类
 * 包含录制的所有操作和录制设备的屏幕尺寸信息
 *
 * @property actions 录制的操作列表
 * @property screenWidth 录制设备的屏幕宽度（像素）
 * @property screenHeight 录制设备的屏幕高度（像素）
 *
 */
@Parcelize
data class RecordingResult(
    val id: String? = null,
    val title: String? = null,
    val screenshotPath: String? = null,
    val actions: List<RecordedAction>,
    val screenWidth: Int,
    val screenHeight: Int,
    val totalDuration: Long = 0L
) : Parcelable

/**
 * 可编辑的操作项
 * 包含原始操作数据和UI状态
 */
data class EditableAction(
    val id: String = java.util.UUID.randomUUID().toString(),
    val action: RecordedAction,
    val isSelected: Boolean = false,
    val isExpanded: Boolean = false
)

/**
 * 编辑页面状态
 */
data class RecordingEditState(
    val title: String = "",
    val actions: List<EditableAction> = emptyList(),
    val screenWidth: Int,
    val screenHeight: Int,
    val screenshotPath: String? = null,
    val focusedActionId: String? = null,
    val focusedPointIndex: Int? = null,
    val isModified: Boolean = false,
    val startDelay: Long = 0L,  // 开始前的延迟（毫秒），所有操作会延后这段时间执行
    val endDelay: Long = 1000L,  // 结束后的延迟（毫秒），最后一个操作执行完后等待的时间
    val baseActionsDuration: Long = 5000L  // 操作序列的基准时长（毫秒），用于 progress 转换
) {
    val selectedCount: Int
        get() = actions.count { it.isSelected }

    val actionCount: Int
        get() = actions.size

    // 第一个操作的原始时间戳（向后兼容）
    @Deprecated("Use progress-based calculations instead")
    val firstActionTime: Long
        @Suppress("DEPRECATION")
        get() = actions.firstOrNull()?.action?.timestamp ?: 0L

    // 最后一个操作的原始时间戳（向后兼容）
    @Deprecated("Use progress-based calculations instead")
    val lastActionTime: Long
        @Suppress("DEPRECATION")
        get() = actions.lastOrNull()?.action?.timestamp ?: 0L

    // 操作的实际时长（基于 baseActionsDuration）
    val actionsDuration: Long
        get() = baseActionsDuration

    // 时间轴总长度（包含起始延迟和结束延迟）
    val totalDuration: Long
        get() = startDelay + baseActionsDuration + endDelay
}
