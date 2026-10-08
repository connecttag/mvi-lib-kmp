package org.connecttag.lib.mvi

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Marker for immutable UI state models.
 */
interface MviState

/**
 * Marker for one-time UI effects such as navigation and snackbars.
 */
interface MviEffect

/**
 * Marker for user actions or UI events.
 */
interface MviAction

typealias IViewState = MviState
typealias IViewAction = MviAction
typealias IViewEffect = MviEffect

/**
 * Reusable empty state/effect contracts for lightweight state holders.
 */
data object EmptyMviState : MviState
data object NoMviEffect : MviEffect

/**
 * A container that holds the MVI components for a specific feature or component.
 */
interface MviContainer<State : MviState, Action : MviAction, Effect : MviEffect> {
    val state: StateFlow<State>
    val effect: Flow<Effect>
    fun onAction(action: Action)
}
