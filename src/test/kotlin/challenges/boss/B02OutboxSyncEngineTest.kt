@file:OptIn(ExperimentalCoroutinesApi::class)

package challenges.boss

import challenges.boss.b02_outbox.ActionType
import challenges.boss.b02_outbox.OrdersApi
import challenges.boss.b02_outbox.OutboxSyncEngine
import challenges.boss.b02_outbox.PendingAction
import challenges.runConcurrently
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class B02OutboxSyncEngineTest {

    /** A fake orders backend. Each send takes [sendMs]. Records what the server actually received. */
    private class FakeServer(private val scope: TestScope, private val sendMs: Long = 100) : OrdersApi {
        val received = mutableListOf<PendingAction>()
        val attemptTimes = mutableMapOf<String, MutableList<Long>>()
        val failuresLeft = mutableMapOf<String, Int>()
        private val sending = mutableSetOf<String>()
        private val sendingOrders = mutableSetOf<Int>()
        val problems = mutableListOf<String>()

        override suspend fun send(action: PendingAction) {
            attemptTimes.getOrPut(action.id) { mutableListOf() } += scope.currentTime
            if (!sending.add(action.id)) problems += "${action.id} was being sent twice at the same time"
            if (!sendingOrders.add(action.orderId)) problems += "two actions of order ${action.orderId} were in flight at once"
            try {
                delay(sendMs)
                val left = failuresLeft[action.id] ?: 0
                if (left > 0) {
                    failuresLeft[action.id] = left - 1
                    throw IOException("timeout")
                }
                if (received.any { it.id == action.id }) problems += "DUPLICATE: ${action.id} received twice"
                received += action
            } catch (e: CancellationException) {
                throw e
            } finally {
                sending -= action.id
                sendingOrders -= action.orderId
            }
        }
    }

    private class Setup(test: TestScope, sendMs: Long = 100, initiallyOnline: Boolean = true) {
        val server = FakeServer(test, sendMs)
        val online = MutableStateFlow(initiallyOnline)
        // A child of backgroundScope: on the test's virtual clock, and always cancelled when the test ends,
        // so an endless sync loop in unfinished code can't hang or crash the test run.
        val appScope = CoroutineScope(test.backgroundScope.coroutineContext + Job(test.backgroundScope.coroutineContext[Job]))
        val engine = OutboxSyncEngine(server, online, appScope).also { it.start() }
    }

    private fun action(id: String, order: Int, type: ActionType = ActionType.PLACE_ORDER) = PendingAction(id, order, type)

    @Test
    fun `spec 2 - online - an enqueued action is sent right away`() = runTest {
        val s = Setup(this)
        advanceTimeBy(1_000) // app has been open for a while

        s.engine.enqueue(action("a1", order = 1))
        advanceTimeBy(150)

        assertEquals(listOf("a1"), s.server.received.map { it.id }, "Action is stuck 'waiting to sync' with perfect Wi-Fi")
        assertEquals(0, s.engine.pendingCount.value)
        s.appScope.cancel()
    }

    @Test
    fun `spec 3 - nothing sent while offline, everything sent once online`() = runTest {
        val s = Setup(this, initiallyOnline = false)
        runCurrent()
        s.engine.enqueue(action("a1", 1))
        s.engine.enqueue(action("a2", 2))
        advanceTimeBy(10_000)
        assertEquals(emptyList(), s.server.received, "Sent while offline")
        assertEquals(2, s.engine.pendingCount.value)

        s.online.value = true
        advanceTimeBy(500)

        assertEquals(setOf("a1", "a2"), s.server.received.map { it.id }.toSet())
        assertEquals(0, s.engine.pendingCount.value)
        s.appScope.cancel()
    }

    @Test
    fun `spec 4 - flaky train connectivity - every order is received exactly once`() = runTest {
        val s = Setup(this, sendMs = 1_000, initiallyOnline = false)
        runCurrent()
        s.engine.enqueue(action("place-1", 1))
        s.engine.enqueue(action("place-2", 2))

        for (t in 0 until 20) { // connectivity flaps every 150ms
            s.online.value = t % 2 == 0
            advanceTimeBy(150)
        }
        s.online.value = true
        advanceTimeBy(10_000)

        assertEquals(emptyList(), s.server.problems)
        assertEquals(setOf("place-1", "place-2"), s.server.received.map { it.id }.toSet())
        assertEquals(0, s.engine.pendingCount.value)
        s.appScope.cancel()
    }

    @Test
    fun `spec 4 - an action that succeeded is never re-sent on reconnect`() = runTest {
        val s = Setup(this)
        s.engine.enqueue(action("a1", 1))
        advanceTimeBy(500)

        s.online.value = false
        advanceTimeBy(100)
        s.online.value = true
        advanceTimeBy(500)

        assertEquals(1, s.server.attemptTimes["a1"]?.size ?: 0, "a1 was sent again after it succeeded")
        s.appScope.cancel()
    }

    @Test
    fun `spec 5 - actions of one order are sent in order, one at a time`() = runTest {
        val s = Setup(this, initiallyOnline = false)
        runCurrent()
        s.engine.enqueue(action("place", 7, ActionType.PLACE_ORDER))
        s.engine.enqueue(action("note", 7, ActionType.ADD_NOTE))
        s.engine.enqueue(action("cancel", 7, ActionType.CANCEL_ORDER))

        s.online.value = true
        advanceTimeBy(1_000)

        assertEquals(emptyList(), s.server.problems)
        assertEquals(listOf("place", "note", "cancel"), s.server.received.map { it.id }, "Restaurant got actions out of order")
        s.appScope.cancel()
    }

    @Test
    fun `spec 5 - different orders sync in parallel`() = runTest {
        val s = Setup(this, sendMs = 1_000, initiallyOnline = false)
        runCurrent()
        (1..3).forEach { s.engine.enqueue(action("place-$it", it)) }

        s.online.value = true
        advanceTimeBy(1_100)

        assertEquals(3, s.server.received.size, "Orders were synced one by one instead of in parallel")
        s.appScope.cancel()
    }

    @Test
    fun `spec 6 - failed send is retried with backoff while online`() = runTest {
        val s = Setup(this)
        s.server.failuresLeft["a1"] = 3
        runCurrent()

        s.engine.enqueue(action("a1", 1))
        advanceTimeBy(20_000)

        // send takes 100ms; waits 1000, 2000, 4000 between attempts
        assertEquals<List<Long>?>(listOf(0L, 1_100L, 3_200L, 7_300L), s.server.attemptTimes["a1"])
        assertEquals<List<String>>(listOf("a1"), s.server.received.map { it.id })
        assertEquals(0, s.engine.pendingCount.value)
        s.appScope.cancel()
    }

    @Test
    fun `spec 6 - backoff is capped`() = runTest {
        val s = Setup(this, sendMs = 0)
        s.server.failuresLeft["a1"] = 8
        runCurrent()

        s.engine.enqueue(action("a1", 1))
        advanceTimeBy(200_000)

        val times = s.server.attemptTimes.getValue("a1")
        val gaps = times.zipWithNext { a, b -> b - a }
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L, 30_000L), gaps)
        s.appScope.cancel()
    }

    @Test
    fun `spec 6 - no attempts while offline, immediate retry when back online`() = runTest {
        val s = Setup(this)
        s.server.failuresLeft["a1"] = 1
        runCurrent()
        s.engine.enqueue(action("a1", 1))
        advanceTimeBy(150) // first attempt failed at 100ms, now backing off

        s.online.value = false
        advanceTimeBy(60_000)
        assertEquals(1, s.server.attemptTimes.getValue("a1").size, "Retried while offline")

        val backOnline = currentTime
        s.online.value = true
        advanceTimeBy(150)
        assertEquals(backOnline, s.server.attemptTimes.getValue("a1")[1], "Should retry immediately when back online")
        assertEquals<List<String>>(listOf("a1"), s.server.received.map { it.id })
        s.appScope.cancel()
    }

    @Test
    fun `spec 1 - enqueue from many threads - nothing lost, badge is exact`() = runTest {
        val s = Setup(this, initiallyOnline = false)
        runCurrent()

        runConcurrently(threadCount = 8) { t ->
            repeat(500) { i -> s.engine.enqueue(action("t$t-$i", order = t * 1000 + i)) }
        }

        assertEquals(4_000, s.engine.pendingCount.value, "The badge lost some changes")
        s.online.value = true
        advanceTimeBy(1_000)
        assertEquals(4_000, s.server.received.size, "Some enqueued actions were lost")
        s.appScope.cancel()
    }
}
