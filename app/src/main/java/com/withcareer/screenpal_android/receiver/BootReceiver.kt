package com.withcareer.screenpal_android.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.withcareer.screenpal_android.ScreenPalApplication
import com.withcareer.screenpal_android.manager.TaskScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 开机自启动广播接收器
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Boot completed, rescheduling tasks...")
            val application = context.applicationContext as ScreenPalApplication
            val repo = application.scheduledTaskRepository
            val scheduler = TaskScheduler(context)

            CoroutineScope(Dispatchers.IO).launch {
                // 等待加载完成?
                // Repo init is synchronous for SharedPreferences (mostly).
                // But just in case, we can collect once.

                // However, StateFlow value access is safe.
                val tasks = repo.tasks.value
                tasks.forEach { task ->
                    if (task.isEnabled) {
                        scheduler.scheduleTask(task)
                    }
                }
            }
        }
    }
}
