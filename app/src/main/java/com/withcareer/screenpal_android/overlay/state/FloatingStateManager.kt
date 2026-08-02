package com.withcareer.screenpal_android.overlay.state

import com.withcareer.screenpal_android.data.preference.PreferencesRepository
import com.withcareer.screenpal_android.core.model.ChatUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Manages the state logic for the Floating Overlay system.
 * Acts as the "ViewModel" for FloatingAssistantService.
 */
class FloatingStateManager(
    private val scope: CoroutineScope,
    private val preferencesRepository: PreferencesRepository
) {

    private val _state = MutableStateFlow(OverlayState())
    val state: StateFlow<OverlayState> = _state.asStateFlow()

    // Internal State Inputs
    private var isTaskLoading: Boolean = false
    private var isFeatureEnabled: Boolean = false // Default to false, will be updated by preference
    private var isTaskListVisible: Boolean = false

    // UI Modes
    private var isPanelExpanded: Boolean = false
    private var isScreenshotMode: Boolean = false
    private var isActionMode: Boolean = false

    // Stored state for screenshot restoration
    private var storedBubbleVisibility: Boolean = false
    private var storedPanelVisibility: Boolean = false
    private var storedStepsVisibility: Boolean = false
    private var storedBubbleAlpha: Float = 1f

    init {
        android.util.Log.d("FloatingStateManager", "StateManager initialized")

        // Observe preference
        scope.launch {
            preferencesRepository.floatingAssistantEnabled.collectLatest { enabled ->
                android.util.Log.d("FloatingStateManager", "Feature enabled changed: $enabled")
                isFeatureEnabled = enabled
                recalculateState()
            }
        }

        recalculateState()
    }

    fun updateRuntimeState(uiState: ChatUiState) {
        val wasLoading = isTaskLoading
        isTaskLoading = uiState.isLoading

        android.util.Log.d("FloatingStateManager", "updateRuntimeState: isLoading=${uiState.isLoading}, wasLoading=$wasLoading")

        // If task just started, close panel (steps will show instead)
        if (isTaskLoading && !wasLoading) {
            android.util.Log.d("FloatingStateManager", "Task just started, closing panel to show steps")
            isPanelExpanded = false
        }

        recalculateState()
    }

    fun setTaskStarted() {
        // Task started, ensure panel is closed so steps window can show
        isPanelExpanded = false
        recalculateState()
    }

    fun setTaskListVisible(visible: Boolean) {
        isTaskListVisible = visible
        recalculateState()
    }

    fun togglePanel() {
        isPanelExpanded = !isPanelExpanded
        recalculateState()
    }

    fun closePanel() {
        isPanelExpanded = false
        recalculateState()
    }

    fun setScreenshotMode(active: Boolean) {
        if (isScreenshotMode == active) return
        isScreenshotMode = active

        if (active) {
            // Save current logical visibility
            val current = _state.value
            storedBubbleVisibility = current.isBubbleVisible
            storedPanelVisibility = current.isPanelVisible
            storedStepsVisibility = current.isStepsVisible
            storedBubbleAlpha = current.bubbleAlpha
        }

        recalculateState()
    }

    fun setActionMode(active: Boolean) {
        if (isActionMode == active) return
        isActionMode = active
        recalculateState()
    }

    private fun recalculateState() {
        android.util.Log.d("FloatingStateManager", "recalculateState: featureEnabled=$isFeatureEnabled, taskLoading=$isTaskLoading, panelExpanded=$isPanelExpanded, actionMode=$isActionMode, taskListVisible=$isTaskListVisible")

        // Base Visibility Logic
        var showBubble = false
        var showPanel = false
        var showSteps = false
        var bubbleAlpha = 1f
        var panelAlpha = 1f
        var stepsAlpha = 1f
        var shouldHideBubbleDelayed = false

        // 1. Task Running State
        if (isTaskLoading) {
            // Task is running - show steps window, hide panel and bubble
            if (isActionMode) {
                // Action Mode (e.g. Inspect Element) -> hide everything during action
                showBubble = false
                showPanel = false
                showSteps = false
            } else {
                // Normal Task Execution - show steps window with progress
                showBubble = false
                showPanel = false
                showSteps = true  // Show steps window during task
            }
        } else {
            // Idle State
            if (isPanelExpanded) {
                showPanel = true
                showBubble = isFeatureEnabled // Bubble remains visible when panel is open only if feature is enabled
                showSteps = false
                shouldHideBubbleDelayed = false // Don't hide while panel is open
            } else {
                showPanel = false
                showSteps = false

                // Bubble Visibility Logic: Only show if feature is enabled AND not in task list page
                if (isFeatureEnabled && !isTaskListVisible) {
                    showBubble = true
                    shouldHideBubbleDelayed = true
                } else {
                    showBubble = false
                }
            }
        }

        // 3. Screenshot Mode Override
        if (isScreenshotMode) {
            // In screenshot mode, everything that WAS visible becomes invisible (alpha 0 or gone)
            // But we maintain the logical state in the background.
            // The Requirement: "Hide all overlays".

            // We set visibilities to FALSE for the View Managers to react.
            // But wait, if we set them to false, the managers might remove the views.
            // The legacy code used `OverlayViewUtils.fadeOut`.
            // We can control this via Alpha.

            // Strategy: Keep logical visibility TRUE if it was true, but set Alpha to 0?
            // Or just tell the UI to HIDE.
            // Let's go with Hiding, as `removeOverlayViews` is safer for screenshots.

            // However, `setScreenshotModeInternal` in legacy code did:
            // storedVisibility = ...
            // fadeOut(...)

            // So we should emit state with isVisible = false.
            showBubble = false
            showPanel = false
            showSteps = false
        } else {
            // Restore?
            // Since we recalculate from base inputs (isTaskLoading, isPanelExpanded),
            // the state will naturally "restore" to what it SHOULD be based on current logic.
            // We don't need `storedBubbleVisibility` to restore, unless there was some transient state not captured here.
            // The `stored` vars in legacy code were needed because it was imperative.
            // Here, declarative state handles it.

            // Exception: storedBubbleAlpha (breathing).
            // If we were breathing, we should resume.
        }

        _state.value = OverlayState(
            isBubbleVisible = showBubble,
            isPanelVisible = showPanel,
            isStepsVisible = showSteps,
            bubbleAlpha = if (isScreenshotMode) 0f else bubbleAlpha,
            panelAlpha = if (isScreenshotMode) 0f else panelAlpha,
            stepsAlpha = if (isScreenshotMode) 0f else stepsAlpha,
            isTaskRunning = isTaskLoading,
            isBreathing = !isScreenshotMode && !isTaskLoading && !isPanelExpanded && showBubble, // Breathe when idle bubble
            shouldHideBubbleDelayed = shouldHideBubbleDelayed
        )
    }
}
