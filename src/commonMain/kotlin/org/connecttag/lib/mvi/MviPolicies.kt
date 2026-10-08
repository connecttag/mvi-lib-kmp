package org.connecttag.lib.mvi

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds

enum class ActionExecutionStrategy {
    Sequential,
    /**
     * Keeps only the latest pending action for the same policy key.
     *
     * The currently executing action is allowed to finish so reducer and middleware
     * transitions remain deterministic.
     */
    ReplaceRunning,
    /**
     * Drops a new action while another action with the same policy key is queued
     * or executing.
     */
    DropIfRunning,
    /**
     * Runs only the latest action dispatched inside [ActionPolicy.window].
     */
    Debounce,
    /**
     * Accepts the first action for a policy key and drops subsequent actions until
     * [ActionPolicy.window] has elapsed.
     */
    Throttle,
}

data class ActionPolicy(
    val strategy: ActionExecutionStrategy = ActionExecutionStrategy.Sequential,
    val window: Duration = ZERO,
    val key: String? = null,
)

fun interface ActionPolicyResolver<Action : MviAction> {
    fun resolve(action: Action): ActionPolicy
}

data class RetryPolicy(
    val maxAttempts: Int = 1,
    val initialDelay: Duration = ZERO,
    val backoffMultiplier: Double = 1.0,
    val maxDelay: Duration? = null,
) {
    init {
        require(maxAttempts > 0) { "maxAttempts must be greater than zero." }
        require(backoffMultiplier >= 1.0) { "backoffMultiplier must be at least 1.0." }
    }

    companion object {
        val None = RetryPolicy()
    }
}

fun interface RetryDecision {
    fun shouldRetry(error: Throwable, attempt: Int): Boolean
}

suspend fun <T> retryWithPolicy(
    policy: RetryPolicy,
    decision: RetryDecision = RetryDecision { _, _ -> true },
    block: suspend (attempt: Int) -> T,
): T {
    var attempt = 1
    var nextDelay = policy.initialDelay

    while (true) {
        try {
            return block(attempt)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (attempt >= policy.maxAttempts || !decision.shouldRetry(error, attempt)) {
                throw error
            }

            if (nextDelay > ZERO) delay(nextDelay)
            nextDelay = nextDelay.scale(policy.backoffMultiplier, policy.maxDelay)
            attempt += 1
        }
    }
}

private fun Duration.scale(multiplier: Double, maxDelay: Duration?): Duration {
    if (this == ZERO) return this
    val scaled = max(0L, (inWholeMilliseconds * multiplier).toLong()).milliseconds
    return if (maxDelay != null && scaled > maxDelay) maxDelay else scaled
}
