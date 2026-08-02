package com.withcareer.screenpal_android.data.preference

/**
 * 任务状态管理
 * 用于跟踪复杂任务的进度、子任务和记忆
 *
 */
data class TaskState(
    val taskId: String,
    val goal: String, // 用户的原始目标
    val subtasks: MutableList<Subtask> = mutableListOf(), // 子任务列表
    val memory: MutableMap<String, String> = mutableMapOf(), // 关键记忆 (Key-Value)
    var status: TaskStatus = TaskStatus.IN_PROGRESS,
    var summary: String = "", // 当前进展的文本摘要
    var note: String = "" // 运行时笔记
)

/**
 * 子任务
 */
data class Subtask(
    val id: String,
    var description: String,
    var status: TaskStatus = TaskStatus.PENDING,
    var result: String? = null // 子任务执行结果
)

/**
 * 任务状态枚举
 */
enum class TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    SKIPPED
}
