package com.withcareer.screenpal_android.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.withcareer.screenpal_android.ScreenPalApplication
import com.withcareer.screenpal_android.data.preference.ScheduledTask
import com.withcareer.screenpal_android.manager.TaskScheduler
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 定时任务 ViewModel
 */
class ScheduledTaskViewModel(application: Application) : AndroidViewModel(application) {
    private val scheduledTaskRepository = getApplication<ScreenPalApplication>().scheduledTaskRepository
    private val taskScheduler = TaskScheduler(application)

    val tasks: StateFlow<List<ScheduledTask>> = scheduledTaskRepository.tasks

    fun addScheduledTask(task: ScheduledTask) {
        viewModelScope.launch {
            scheduledTaskRepository.addScheduledTask(task)
            taskScheduler.scheduleTask(task)
        }
    }

    fun updateScheduledTask(task: ScheduledTask) {
        viewModelScope.launch {
            scheduledTaskRepository.updateScheduledTask(task)
            taskScheduler.scheduleTask(task)
        }
    }

    fun deleteScheduledTask(taskId: String) {
        viewModelScope.launch {
            val task = scheduledTaskRepository.getTaskById(taskId)
            if (task != null) {
                taskScheduler.cancelTask(task)
                scheduledTaskRepository.deleteScheduledTask(taskId)
            }
        }
    }

    fun toggleTaskEnabled(task: ScheduledTask, isEnabled: Boolean) {
        viewModelScope.launch {
            val updatedTask = task.copy(isEnabled = isEnabled)
            scheduledTaskRepository.updateScheduledTask(updatedTask)
            if (isEnabled) {
                taskScheduler.scheduleTask(updatedTask)
            } else {
                taskScheduler.cancelTask(updatedTask)
            }
        }
    }

    fun executeTaskNow(task: ScheduledTask) {
        viewModelScope.launch {
            getApplication<ScreenPalApplication>().agentRuntime.startTask(task.prompt)
        }
    }
}
