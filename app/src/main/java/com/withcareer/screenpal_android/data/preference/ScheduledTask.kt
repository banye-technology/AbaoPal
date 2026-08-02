package com.withcareer.screenpal_android.data.preference

import java.util.UUID

/**
 * 定时任务重复类型
 */
enum class RepeatType {
    ONCE,       // 单次
    DAILY,      // 每天
    WEEKLY,     // 每周
    WORKDAYS,   // 工作日 (周一至周五)
    MONTHLY     // 每月
}

/**
 * 定时任务实体类
 */
data class ScheduledTask(
    val id: String = UUID.randomUUID().toString(),
    val prompt: String,

    // 时间配置
    var hour: Int,      // 0-23
    var minute: Int,    // 0-59

    // 日期/重复配置
    var repeatType: RepeatType = RepeatType.ONCE,
    var weekDays: List<Int> = emptyList(), // 1=Sunday, 2=Monday, ... 7=Saturday (Calendar.DAY_OF_WEEK)
    var dayOfMonth: Int = 1,               // 1-31
    var targetTimestamp: Long? = null,     // 单次执行的具体日期时间戳 (用于 ONCE)

    var isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
