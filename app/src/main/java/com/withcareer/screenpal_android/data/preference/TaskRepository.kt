package com.withcareer.screenpal_android.data.preference

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * 任务仓库
 */
class TaskRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tasks_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    private val lock = Any()

    init {
        loadTasks()
    }

    private fun loadTasks() {
        synchronized(lock) {
            val json = prefs.getString("task_list", null)
            if (json != null) {
                try {
                    val type = object : TypeToken<List<Task>>() {}.type
                    val list: List<Task> = gson.fromJson(json, type)
                    _tasks.value = list.sortedByDescending { it.createdAt }
                } catch (e: Exception) {
                    // 如果解析失败（例如数据结构变更），则清空旧数据
                    Log.e("TaskRepository", "Failed to load tasks", e)
                    _tasks.value = emptyList()
                    saveTasks(emptyList())
                }
            }
        }
    }

    fun createTask(prompt: String): String {
        synchronized(lock) {
            val taskId = UUID.randomUUID().toString()
            val newTask = Task(
                id = taskId,
                prompt = prompt,
                steps = emptyList(),
                createdAt = System.currentTimeMillis()
            )
            val currentList = _tasks.value.toMutableList()
            currentList.add(0, newTask)
            val newList = currentList.toList()
            _tasks.value = newList
            saveTasks(newList)
            return taskId
        }
    }

    fun updateTaskSteps(taskId: String, steps: List<TaskStep>) {
        val stepsCopy = ArrayList(steps)
        var listToSave: List<Task>? = null
        synchronized(lock) {
            val currentList = _tasks.value.toMutableList()
            val index = currentList.indexOfFirst { it.id == taskId }
            if (index != -1) {
                val oldTask = currentList[index]
                val newTask = oldTask.copy(steps = stepsCopy)
                currentList[index] = newTask

                _tasks.value = currentList.toList()
                listToSave = _tasks.value
            }
        }

        if (listToSave != null) {
            saveTasks(listToSave!!)
        }
    }

    fun updateTaskState(taskId: String, state: TaskState) {
        var listToSave: List<Task>? = null
        synchronized(lock) {
            val currentList = _tasks.value.toMutableList()
            val index = currentList.indexOfFirst { it.id == taskId }
            if (index != -1) {
                val oldTask = currentList[index]
                val newTask = oldTask.copy(state = state)
                currentList[index] = newTask

                _tasks.value = currentList.toList()
                listToSave = _tasks.value
            }
        }

        if (listToSave != null) {
            saveTasks(listToSave!!)
        }
    }

    fun updateTaskNote(taskId: String, note: String) {
        var listToSave: List<Task>? = null
        synchronized(lock) {
            val currentList = _tasks.value.toMutableList()
            val index = currentList.indexOfFirst { it.id == taskId }
            if (index != -1) {
                val oldTask = currentList[index]

                // 更新 State 中的 note (追加模式)
                val oldState = oldTask.state
                val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                val newNoteEntry = "[$timestamp] $note"

                val newState = if (oldState != null) {
                    val currentNote = oldState.note
                    val updatedNoteRaw = if (currentNote.isEmpty()) {
                        newNoteEntry
                    } else {
                        "$currentNote\n$newNoteEntry"
                    }
                    val maxNoteChars = 2000
                    val updatedNote =
                        if (updatedNoteRaw.length <= maxNoteChars) updatedNoteRaw
                        else updatedNoteRaw.takeLast(maxNoteChars)
                    oldState.copy(note = updatedNote)
                } else {
                    // 如果 state 为空，初始化一个
                    TaskState(
                        taskId = taskId,
                        goal = oldTask.prompt,
                        note = newNoteEntry
                    )
                }

                val newTask = oldTask.copy(state = newState)
                currentList[index] = newTask

                _tasks.value = currentList.toList()
                listToSave = _tasks.value
            }
        }

        if (listToSave != null) {
            saveTasks(listToSave!!)
        }
    }



    fun deleteTask(taskId: String) {
        synchronized(lock) {
            val currentList = _tasks.value.toMutableList()
            currentList.removeAll { it.id == taskId }

            val newList = currentList.toList()
            _tasks.value = newList
            saveTasks(newList)
        }
    }

    fun deleteTasks(taskIds: Set<String>) {
        if (taskIds.isEmpty()) return
        synchronized(lock) {
            val currentList = _tasks.value.toMutableList()
            currentList.removeAll { it.id in taskIds }

            val newList = currentList.toList()
            _tasks.value = newList
            saveTasks(newList)
        }
    }

    fun updateTaskPrompt(taskId: String, prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isBlank()) return
        var listToSave: List<Task>? = null
        synchronized(lock) {
            val currentList = _tasks.value.toMutableList()
            val index = currentList.indexOfFirst { it.id == taskId }
            if (index != -1) {
                val oldTask = currentList[index]
                val newTask = oldTask.copy(prompt = trimmed)
                currentList[index] = newTask
                _tasks.value = currentList.toList()
                listToSave = _tasks.value
            }
        }
        if (listToSave != null) {
            saveTasks(listToSave!!)
        }
    }

    private fun saveTasks(list: List<Task>) {
        val json = gson.toJson(list)
        prefs.edit().putString("task_list", json).apply()
    }
}
