package org.connecttag.lib.mvi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.TestTimeSource

class MviStoreTest {

    @Test
    fun actionsAreProcessedSequentiallyThroughTheQueue() = runTest {
        val storeScope = testStoreScope()
        val observedValues = mutableListOf<Int>()
        val store = MviStore<TestState, TestAction, TestEffect>(
            initialState = TestState(),
            scope = storeScope,
            reducer = ::reduce,
            middleware = listOf(
                MviMiddleware<TestState, TestAction, TestEffect> { context, dispatcher, _ ->
                    observedValues += context.currentState.value
                    if (context.action == TestAction.Add(1)) {
                        dispatcher.dispatch(TestAction.Add(10))
                    }
                },
            ),
        )

        store.onAction(TestAction.Add(1))
        store.onAction(TestAction.Add(2))
        advanceUntilIdle()

        assertEquals(13, store.state.value.value)
        assertEquals(listOf(1, 3, 13), observedValues)
        storeScope.cancel()
    }

    @Test
    fun middlewareReceivesPreviousAndCurrentState() = runTest {
        val storeScope = testStoreScope()
        val transitions = mutableListOf<Pair<Int, Int>>()
        val store = MviStore<TestState, TestAction, TestEffect>(
            initialState = TestState(value = 4),
            scope = storeScope,
            reducer = ::reduce,
            middleware = listOf(
                MviMiddleware<TestState, TestAction, TestEffect> { context, _, _ ->
                    transitions += context.previousState.value to context.currentState.value
                },
            ),
        )

        store.onAction(TestAction.Add(3))
        advanceUntilIdle()

        assertEquals(listOf(4 to 7), transitions)
        storeScope.cancel()
    }

    @Test
    fun internalStateReductionUsesTheStoreWithoutSyntheticAction() = runTest {
        val storeScope = testStoreScope()
        var middlewareCalls = 0
        val store = MviStore<TestState, TestAction, TestEffect>(
            initialState = TestState(value = 2),
            scope = storeScope,
            reducer = ::reduce,
            middleware = listOf(
                MviMiddleware { _, _, _ -> middlewareCalls += 1 },
            ),
        )

        store.reduceState { it.copy(value = it.value * 3) }

        assertEquals(6, store.state.value.value)
        assertEquals(0, middlewareCalls)
        storeScope.cancel()
    }

    @Test
    fun rejectedActionDoesNotChangeStateAndCanEmitEffect() = runTest {
        val storeScope = testStoreScope()
        val store = rejectedActionStore(storeScope)
        val effect = async { store.effect.first() }

        store.onAction(TestAction.Invalid)
        advanceUntilIdle()

        assertEquals(TestState(), store.state.value)
        assertEquals(TestEffect.Message("mvi.action.invalid"), effect.await())
        storeScope.cancel()
    }

    @Test
    fun replacePolicyProcessesOnlyLatestPendingAction() = runTest {
        val storeScope = testStoreScope()
        val store = MviStore<TestState, TestAction, TestEffect>(
            initialState = TestState(),
            scope = storeScope,
            reducer = ::reduce,
            actionPolicyResolver = ActionPolicyResolver {
                ActionPolicy(strategy = ActionExecutionStrategy.ReplaceRunning)
            },
        )

        store.onAction(TestAction.Set(1))
        store.onAction(TestAction.Set(2))
        advanceUntilIdle()

        assertEquals(2, store.state.value.value)
        storeScope.cancel()
    }

    @Test
    fun debouncePolicyProcessesLatestActionAfterWindow() = runTest {
        val storeScope = testStoreScope()
        val store = MviStore<TestState, TestAction, TestEffect>(
            initialState = TestState(),
            scope = storeScope,
            reducer = ::reduce,
            actionPolicyResolver = ActionPolicyResolver {
                ActionPolicy(strategy = ActionExecutionStrategy.Debounce, window = 100.milliseconds)
            },
        )

        store.onAction(TestAction.Set(1))
        advanceTimeBy(50.milliseconds)
        store.onAction(TestAction.Set(2))
        advanceTimeBy(99.milliseconds)

        assertEquals(0, store.state.value.value)

        advanceTimeBy(1.milliseconds)
        advanceUntilIdle()

        assertEquals(2, store.state.value.value)
        storeScope.cancel()
    }

    @Test
    @OptIn(ExperimentalTime::class)
    fun throttlePolicyDropsActionsInsideWindow() = runTest {
        val storeScope = testStoreScope()
        val timeSource = TestTimeSource()
        val store = MviStore<TestState, TestAction, TestEffect>(
            initialState = TestState(),
            scope = storeScope,
            reducer = ::reduce,
            actionPolicyResolver = ActionPolicyResolver {
                ActionPolicy(strategy = ActionExecutionStrategy.Throttle, window = 100.milliseconds)
            },
            timeSource = timeSource,
        )

        val firstAccepted = store.dispatch(TestAction.Add(1))
        val secondAccepted = store.dispatch(TestAction.Add(10))
        timeSource += 100.milliseconds
        val thirdAccepted = store.dispatch(TestAction.Add(100))
        advanceUntilIdle()

        assertEquals(true, firstAccepted)
        assertEquals(false, secondAccepted)
        assertEquals(true, thirdAccepted)
        assertEquals(101, store.state.value.value)
        storeScope.cancel()
    }

    @Test
    fun dropIfRunningPolicyDropsDuplicateActionUntilProcessingFinishes() = runTest {
        val storeScope = testStoreScope()
        val store = MviStore(
            initialState = TestState(),
            scope = storeScope,
            reducer = ::reduce,
            middleware = listOf(
                MviMiddleware<TestState, TestAction, TestEffect> { _, _, _ ->
                    delay(100.milliseconds)
                },
            ),
            actionPolicyResolver = ActionPolicyResolver {
                ActionPolicy(strategy = ActionExecutionStrategy.DropIfRunning)
            },
        )

        val firstAccepted = store.dispatch(TestAction.Add(1))
        val secondAccepted = store.dispatch(TestAction.Add(10))
        advanceUntilIdle()

        assertEquals(true, firstAccepted)
        assertEquals(false, secondAccepted)
        assertEquals(1, store.state.value.value)
        storeScope.cancel()
    }

    private fun rejectedActionStore(
        scope: CoroutineScope,
    ): MviStore<TestState, TestAction, TestEffect> {
        return MviStore(
            initialState = TestState(),
            scope = scope,
            reducer = ::reduce,
            validators = listOf(
                ActionValidator { action ->
                    if (action == TestAction.Invalid) {
                        ActionValidationResult.Rejected(messageKey = "mvi.action.invalid")
                    } else {
                        ActionValidationResult.Accepted
                    }
                },
            ),
            validationFailureEffect = { rejection, _ -> TestEffect.Message(rejection.messageKey) },
        )
    }

    private fun TestScope.testStoreScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
    }

    private data class TestState(val value: Int = 0) : MviState

    private sealed interface TestAction : MviAction {
        data class Add(val amount: Int) : TestAction
        data class Set(val value: Int) : TestAction
        data object Invalid : TestAction
    }

    private sealed interface TestEffect : MviEffect {
        data class Message(val value: String) : TestEffect
    }

    private fun reduce(state: TestState, action: TestAction): TestState {
        return when (action) {
            is TestAction.Add -> state.copy(value = state.value + action.amount)
            is TestAction.Set -> state.copy(value = action.value)
            TestAction.Invalid -> state
        }
    }
}
