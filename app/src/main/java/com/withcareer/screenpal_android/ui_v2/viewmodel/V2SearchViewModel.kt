package com.withcareer.screenpal_android.ui_v2.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.withcareer.screenpal_android.ScreenPalApplication
import com.withcareer.screenpal_android.ui_v2.search.TaskSearchResult
import com.withcareer.screenpal_android.ui_v2.search.searchTasks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class V2SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<TaskSearchResult> = emptyList()
)

class V2SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ScreenPalApplication
    private val taskRepository = app.taskRepository
    private val agentRuntime = app.agentRuntime

    private val query = MutableStateFlow("")

    val uiState: StateFlow<V2SearchUiState> = combine(
        taskRepository.tasks,
        query
    ) { tasks, q ->
        val trimmed = q.trim()
        val results = if (trimmed.isEmpty()) emptyList() else searchTasks(tasks, trimmed, maxResults = 80)
        V2SearchUiState(
            query = q,
            isSearching = trimmed.isNotEmpty(),
            results = results
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), V2SearchUiState())

    fun updateQuery(value: String) {
        query.value = value
    }

    fun clearQuery() {
        query.value = ""
    }

    fun openTask(taskId: String) {
        viewModelScope.launch {
            agentRuntime.replayTask(taskId)
        }
    }
}
