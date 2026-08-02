package com.withcareer.screenpal_android.overlay.state

/**
 * Represents the visual state of the Floating Overlay system.
 */
data class OverlayState(
    // Visibility flags
    val isBubbleVisible: Boolean = false,
    val isPanelVisible: Boolean = false,
    val isStepsVisible: Boolean = false,

    // Appearance
    val bubbleAlpha: Float = 1f,
    val panelAlpha: Float = 1f,
    val stepsAlpha: Float = 1f,

    // Logical State (used by managers to decide internal behavior)
    val isTaskRunning: Boolean = false,
    val isBreathing: Boolean = false, // For Bubble breathing animation
    val shouldHideBubbleDelayed: Boolean = false // Whether bubble should try to auto-hide
)
