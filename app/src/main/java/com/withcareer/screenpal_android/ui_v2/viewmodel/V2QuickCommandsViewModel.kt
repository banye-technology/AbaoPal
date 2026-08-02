package com.withcareer.screenpal_android.ui_v2.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.withcareer.screenpal_android.ScreenPalApplication
import com.withcareer.screenpal_android.data.room.InstructionSetEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class V2QuickCommandsUiState(
    val instructionSets: List<InstructionSetEntity> = emptyList()
)

class V2QuickCommandsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ScreenPalApplication
    private val instructionRepository = app.instructionRepository
    private val agentRuntime = app.agentRuntime

    val uiState: StateFlow<V2QuickCommandsUiState> = instructionRepository.allInstructionSets
        .map { sets -> V2QuickCommandsUiState(instructionSets = sets) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), V2QuickCommandsUiState())

    fun runInstruction(instructionSetId: String) {
        agentRuntime.runInstruction(instructionSetId)
    }
}
