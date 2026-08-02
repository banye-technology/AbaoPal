package com.withcareer.screenpal_android.core.agent.component

import com.withcareer.screenpal_android.data.preference.TaskRepository
import com.withcareer.screenpal_android.data.preference.TaskState
import com.withcareer.screenpal_android.data.preference.TaskStep
import com.withcareer.screenpal_android.data.preference.TaskStatus

/**
 * Manages the state and lifecycle of tasks, interacting with the TaskRepository.
 */
class TaskManager(private val taskRepository: TaskRepository) {

    var currentTaskId: String? = null
        private set

    private val _currentTaskSteps = mutableListOf<TaskStep>()
    val currentTaskSteps: List<TaskStep> get() = _currentTaskSteps

    /**
     * Creates a new task and sets it as current.
     */
    suspend fun createTask(prompt: String): String {
        val id = taskRepository.createTask(prompt)
        currentTaskId = id
        _currentTaskSteps.clear()
        return id
    }

    /**
     * Manually sets the current task ID (e.g. for Instructions or FastTrack).
     */
    fun setCurrentTask(id: String) {
        currentTaskId = id
        _currentTaskSteps.clear()
    }

    /**
     * Adds a step to the current task.
     */
    suspend fun addStep(step: TaskStep) {
        _currentTaskSteps.add(step)
        syncSteps()
    }

    /**
     * Adds a step to the beginning of the list (e.g. Planning step).
     */
    suspend fun addStepToBeginning(step: TaskStep) {
        _currentTaskSteps.add(0, step)
        syncSteps()
    }

    /**
     * Updates the last step of the current task.
     */
    suspend fun updateLastStep(update: (TaskStep) -> TaskStep) {
        if (_currentTaskSteps.isNotEmpty()) {
            val index = _currentTaskSteps.lastIndex
            val updated = update(_currentTaskSteps[index])
            _currentTaskSteps[index] = updated
            syncSteps()
        }
    }

    private suspend fun syncSteps() {
        currentTaskId?.let { id ->
            taskRepository.updateTaskSteps(id, _currentTaskSteps)
        }
    }

    /**
     * Updates the full task state.
     */
    suspend fun updateTaskState(state: TaskState) {
        currentTaskId?.let { id ->
            // Ensure ID consistency if the state doesn't have it set correctly (though it should)
            val stateWithId = if (state.taskId != id) state.copy(taskId = id) else state
            taskRepository.updateTaskState(id, stateWithId)
        }
    }

    /**
     * Marks the current task as completed.
     */
    suspend fun completeTask(summary: String) {
        currentTaskId?.let { id ->
            val currentTask = taskRepository.tasks.value.find { it.id == id }
            val oldState = currentTask?.state

            val newState = oldState?.copy(
                status = TaskStatus.COMPLETED,
                summary = summary
            ) ?: TaskState(
                taskId = id,
                goal = currentTask?.prompt ?: "",
                status = TaskStatus.COMPLETED,
                summary = summary
            )
            taskRepository.updateTaskState(id, newState)
        }
    }

    /**
     * Marks the current task as failed.
     */
    suspend fun failTask(reason: String) {
         currentTaskId?.let { id ->
            val currentTask = taskRepository.tasks.value.find { it.id == id }
            val oldState = currentTask?.state

            val newState = oldState?.copy(
                status = TaskStatus.FAILED,
                summary = reason
            ) ?: TaskState(
                taskId = id,
                goal = currentTask?.prompt ?: "",
                status = TaskStatus.FAILED,
                summary = reason
            )
            taskRepository.updateTaskState(id, newState)
        }
    }

    /**
     * Clears the current task context.
     */
    fun clear() {
        currentTaskId = null
        _currentTaskSteps.clear()
    }

    /**
     * Retrieves a task by ID.
     */
    fun getTask(taskId: String) = taskRepository.tasks.value.find { it.id == taskId }
}
