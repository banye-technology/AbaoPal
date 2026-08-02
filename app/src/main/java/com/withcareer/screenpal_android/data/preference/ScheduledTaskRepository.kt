package com.withcareer.screenpal_android.data.preference

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 定时任务仓库
 */
class ScheduledTaskRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("scheduled_tasks_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val _tasks = MutableStateFlow<List<ScheduledTask>>(emptyList())
    val tasks: StateFlow<List<ScheduledTask>> = _tasks.asStateFlow()

    private val lock = Any()

    init {
        loadTasks()
    }

    private fun loadTasks() {
        synchronized(lock) {
            val json = prefs.getString("scheduled_task_list", null)
            if (json != null) {
                try {
                    val type = object : TypeToken<List<ScheduledTask>>() {}.type
                    val list: List<ScheduledTask> = gson.fromJson(json, type)
                    _tasks.value = list.sortedByDescending { it.createdAt }
                } catch (e: Exception) {
                    Log.e("ScheduledTaskRepo", "Failed to load scheduled tasks", e)
                    _tasks.value = emptyList()
                    saveTasks(emptyList())
                }
            }
        }
    }

    fun addScheduledTask(task: ScheduledTask) {
        synchronized(lock) {
            val currentList = _tasks.value.toMutableList()
            currentList.add(0, task)
            val newList = currentList.toList()
            _tasks.value = newList
            saveTasks(newList)
        }
    }

    fun updateScheduledTask(task: ScheduledTask) {
        synchronized(lock) {
            val currentList = _tasks.value.toMutableList()
            val index = currentList.indexOfFirst { it.id == task.id }
            if (index != -1) {
                currentList[index] = task
                val newList = currentList.toList()
                _tasks.value = newList
                saveTasks(newList)
            }
        }
    }

    fun deleteScheduledTask(taskId: String) {
        synchronized(lock) {
            val currentList = _tasks.value.toMutableList()
            currentList.removeIf { it.id == taskId }
            val newList = currentList.toList()
            _tasks.value = newList
            saveTasks(newList)
        }
    }

    fun getTaskById(taskId: String): ScheduledTask? {
        return _tasks.value.find { it.id == taskId }
    }

    private fun saveTasks(tasks: List<ScheduledTask>) {
        val json = gson.toJson(tasks)
        prefs.edit().putString("scheduled_task_list", json).apply()
    }
}
