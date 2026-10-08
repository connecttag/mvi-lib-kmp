package org.connecttag.lib.mvi

/**
 * Explicit classification for the small set of lifecycle holders that do not
 * own an action/reducer state machine. Direct ViewModels must declare one of
 * these roles instead of silently creating a parallel presentation pattern.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class DirectViewModel(
    val role: DirectViewModelRole,
)

enum class DirectViewModelRole {
    DERIVED_FLOW_STATE,
    EFFECT_COORDINATOR,
    REPOSITORY_FLOW_COORDINATOR,
    FORM_ORCHESTRATOR,
}
