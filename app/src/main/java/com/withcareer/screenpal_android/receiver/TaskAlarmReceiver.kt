package com.withcareer.screenpal_android.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.withcareer.screenpal_android.ScreenPalApplication
import com.withcareer.screenpal_android.data.preference.RepeatType
import com.withcareer.screenpal_android.manager.TaskScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 定时任务广播接收器
 */
class TaskAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra("taskId") ?: return
        val prompt = intent.getStringExtra("prompt") ?: return

        Log.d("TaskAlarmReceiver", "Received scheduled task alarm")

        val application = context.applicationContext as ScreenPalApplication
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                // 执行任务
                application.agentRuntime.startTask(prompt)

                // 重新调度下一次（如果是重复任务）
                withContext(Dispatchers.IO) {
                    val repo = application.scheduledTaskRepository
                    val task = repo.getTaskById(taskId)
                    if (task != null) {
                        if (task.isEnabled && task.repeatType != RepeatType.ONCE) {
                            TaskScheduler(context).scheduleNextAlarm(task)
                        } else if (task.repeatType == RepeatType.ONCE) {
                            task.isEnabled = false
                            repo.updateScheduledTask(task)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
