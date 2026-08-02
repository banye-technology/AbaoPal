package com.withcareer.screenpal_android.core.action

import com.google.gson.JsonObject
import com.withcareer.screenpal_android.data.preference.TaskState

/**
 * 动作执行结果
 *
 */
data class ExecuteResult(
    val success: Boolean,
    val message: String? = null,
    val actionType: String? = null,
    val actionData: JsonObject? = null,
    val updatedTaskState: TaskState? = null
)
