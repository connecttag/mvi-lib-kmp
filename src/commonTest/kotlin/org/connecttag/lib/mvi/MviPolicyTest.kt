package org.connecttag.lib.mvi

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MviPolicyTest {

    @Test
    fun retryPolicyRetriesUntilSuccess() = runTest {
        var attempts = 0

        val value = retryWithPolicy(RetryPolicy(maxAttempts = 3)) {
            attempts += 1
            if (attempts < 3) error("retry")
            "ok"
        }

        assertEquals("ok", value)
        assertEquals(3, attempts)
    }

    @Test
    fun boundedHistoryDropsOldestSnapshots() {
        val history = BoundedStateHistory<HistoryState>(capacity = 2)
        history.record(HistoryState(1))
        history.record(HistoryState(2))
        history.record(HistoryState(3))

        assertEquals(HistoryState(3), history.undo(HistoryState(4)))
        assertEquals(HistoryState(2), history.undo(HistoryState(3)))
    }

    private data class HistoryState(val value: Int) : MviState
}
