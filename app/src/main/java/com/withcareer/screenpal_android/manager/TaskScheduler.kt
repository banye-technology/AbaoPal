package com.withcareer.screenpal_android.manager

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.withcareer.screenpal_android.data.preference.RepeatType
import com.withcareer.screenpal_android.data.preference.ScheduledTask
import com.withcareer.screenpal_android.receiver.TaskAlarmReceiver
import java.util.Calendar

/**
 * 定时任务调度管理器
 */
class TaskScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleTask(task: ScheduledTask) {
        if (!task.isEnabled) {
            cancelTask(task)
            return
        }

        val triggerTime = calculateNextTriggerTime(task)
        if (triggerTime == null) {
            Log.w("TaskScheduler", "Cannot schedule task: no valid trigger time")
            return
        }

        scheduleAlarm(task, triggerTime)
    }

    fun scheduleNextAlarm(task: ScheduledTask) {
         scheduleTask(task)
    }

    fun cancelTask(task: ScheduledTask) {
        val intent = Intent(context, TaskAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d("TaskScheduler", "Cancelled scheduled task")
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleAlarm(task: ScheduledTask, triggerTime: Long) {
        val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
            putExtra("taskId", task.id)
            putExtra("prompt", task.prompt)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                     alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                     alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else {
                 alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
            Log.d("TaskScheduler", "Scheduled task")
        } catch (e: SecurityException) {
             Log.e("TaskScheduler", "Failed to schedule alarm", e)
        }
    }

    private fun calculateNextTriggerTime(task: ScheduledTask): Long? {
        val now = Calendar.getInstance()

        if (task.repeatType == RepeatType.ONCE) {
            if (task.targetTimestamp != null) {
                val target = Calendar.getInstance()
                target.timeInMillis = task.targetTimestamp!!
                target.set(Calendar.HOUR_OF_DAY, task.hour)
                target.set(Calendar.MINUTE, task.minute)
                target.set(Calendar.SECOND, 0)
                target.set(Calendar.MILLISECOND, 0)

                if (target.timeInMillis > now.timeInMillis) {
                    return target.timeInMillis
                }
                return null
            } else {
                // Fallback logic if timestamp not set
                val target = Calendar.getInstance()
                target.set(Calendar.HOUR_OF_DAY, task.hour)
                target.set(Calendar.MINUTE, task.minute)
                target.set(Calendar.SECOND, 0)
                target.set(Calendar.MILLISECOND, 0)

                if (target.before(now)) {
                    target.add(Calendar.DAY_OF_YEAR, 1)
                }
                return target.timeInMillis
            }
        }

        val target = Calendar.getInstance()
        target.set(Calendar.HOUR_OF_DAY, task.hour)
        target.set(Calendar.MINUTE, task.minute)
        target.set(Calendar.SECOND, 0)
        target.set(Calendar.MILLISECOND, 0)

        // If time has passed for today, start checking from tomorrow
        if (target.before(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        // Find the next valid day
        for (i in 0..365) {
            if (isDayValid(task, target)) {
                return target.timeInMillis
            }
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        return null
    }

    private fun isDayValid(task: ScheduledTask, calendar: Calendar): Boolean {
        return when (task.repeatType) {
            RepeatType.DAILY -> true
            RepeatType.WEEKLY -> {
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                task.weekDays.contains(dayOfWeek)
            }
            RepeatType.WORKDAYS -> {
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                dayOfWeek != Calendar.SATURDAY && dayOfWeek != Calendar.SUNDAY
            }
            RepeatType.MONTHLY -> {
                calendar.get(Calendar.DAY_OF_MONTH) == task.dayOfMonth
            }
            else -> false
        }
    }
}
