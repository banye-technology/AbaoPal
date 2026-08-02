package com.withcareer.screenpal_android.data.preference

/**
 * 任务实体类
 */
data class Task(
    val id: String,
    val prompt: String,
    val steps: List<TaskStep>, // 存储执行步骤
    val createdAt: Long,
    val state: TaskState? = null // 任务状态上下文
)

/**
 * 任务步骤实体类
 */
data class TaskStep(
    val thought: String,
    val action: String,
    val observation: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val screenshotPath: String? = null,
    val aiPrompt: String? = null,
    val aiResponse: String? = null
)
