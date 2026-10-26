@file:OptIn(ExperimentalCoroutinesApi::class)

package challenges.coroutines

import challenges.coroutines.k04_tracking.OrderStatus
import challenges.coroutines.k04_tracking.OrderTracker
import challenges.coroutines.k04_tracking.OrderTracker.Companion.POLL_INTERVAL_MS
import challenges.coroutines.k04_tracking.TrackingApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.test.assertEquals

class K04OrderTrackerTest {

    /** Each fetch takes 1s. Statuses are served from [script], then the last one repeats. */
    private class FakeTrackingApi(
        private val script: List<OrderStatus> = listOf(OrderStatus.PREPARING),
        private val failOnCalls: Set<Int> = emptySet(),
    ) : TrackingApi {
        val fetchedOrders = mutableListOf<Int>()

        override suspend fun fetchStatus(orderId: Int): OrderStatus {
            fetchedOrders += orderId
            val call = fetchedOrders.size
            delay(1_000)
            if (call in failOnCalls) throw IOException("timeout")
            return script[minOf(call - 1, script.lastIndex)]
        }
    }

    /**
     * The tracker runs on its own virtual clock. Time only moves when a test calls
     * [advanceTimeBy] / [runCurrent], so nothing runs behind the test's back.
     */
    private class Setup(api: FakeTrackingApi) {
        private val scheduler = TestCoroutineScheduler()
        val updates = mutableListOf<OrderStatus>()
        val tracker = OrderTracker(api, StandardTestDispatcher(scheduler)) { updates += it }

        fun advanceTimeBy(ms: Long) = scheduler.advanceTimeBy(ms)
        fun runCurrent() = scheduler.runCurrent()
    }

    @Test
    fun `polls every interval while tracking`() {
        val api = FakeTrackingApi()
        val s = Setup(api)

        s.tracker.startTracking(42)
        s.advanceTimeBy(3 * (1_000 + POLL_INTERVAL_MS) - 1) // 3 full cycles
        s.runCurrent()

        assertEquals(3, api.fetchedOrders.size)
        assertEquals(3, s.updates.size)
        s.tracker.destroy()
    }

    @Test
    fun `stopTracking stops immediately - in-flight request is cancelled`() {
        val api = FakeTrackingApi()
        val s = Setup(api)

        s.tracker.startTracking(42)
        s.advanceTimeBy(500) // first fetch is in flight
        s.tracker.stopTracking()
        s.advanceTimeBy(60_000)

        assertEquals(1, api.fetchedOrders.size, "Polling continued after stopTracking()")
        assertEquals(emptyList(), s.updates, "onUpdate called after stopTracking()")
        s.tracker.destroy()
    }

    @Test
    fun `starting twice never creates a second poller`() {
        val api = FakeTrackingApi()
        val s = Setup(api)

        s.tracker.startTracking(42)
        s.tracker.startTracking(42) // onStart called twice (rotation)
        s.advanceTimeBy(2 * (1_000 + POLL_INTERVAL_MS) - 1)
        s.runCurrent()

        assertEquals(2, api.fetchedOrders.size, "Two pollers are running at the same time")
        s.tracker.destroy()
    }

    @Test
    fun `tracking a different order switches to it`() {
        val api = FakeTrackingApi()
        val s = Setup(api)

        s.tracker.startTracking(1)
        s.advanceTimeBy(1_500)
        s.tracker.startTracking(2)
        s.advanceTimeBy(2 * (1_000 + POLL_INTERVAL_MS) - 1)
        s.runCurrent()

        assertEquals(listOf(1, 2, 2), api.fetchedOrders, "Order 1 should no longer be polled")
        s.tracker.destroy()
    }

    @Test
    fun `stop then start resumes polling`() {
        val api = FakeTrackingApi()
        val s = Setup(api)

        s.tracker.startTracking(42)
        s.advanceTimeBy(1_500)
        s.tracker.stopTracking()
        s.advanceTimeBy(30_000)
        s.tracker.startTracking(42)
        s.advanceTimeBy(1_500)

        assertEquals(2, api.fetchedOrders.size)
        assertEquals(2, s.updates.size)
        s.tracker.destroy()
    }

    @Test
    fun `after destroy nothing runs, even if started again`() {
        val api = FakeTrackingApi()
        val s = Setup(api)

        s.tracker.startTracking(42)
        s.advanceTimeBy(1_500)
        s.tracker.destroy()
        s.tracker.startTracking(42)
        s.advanceTimeBy(60_000)

        assertEquals(1, api.fetchedOrders.size, "Something polled after destroy()")
    }

    @Test
    fun `a failed fetch does not kill tracking`() {
        val api = FakeTrackingApi(failOnCalls = setOf(1))
        val s = Setup(api)

        s.tracker.startTracking(42)
        s.advanceTimeBy(2 * (1_000 + POLL_INTERVAL_MS) - 1)
        s.runCurrent()

        assertEquals(2, api.fetchedOrders.size, "Tracking died after one IOException")
        assertEquals(listOf(OrderStatus.PREPARING), s.updates)
        s.tracker.destroy()
    }

    @Test
    fun `stops by itself when delivered`() {
        val api = FakeTrackingApi(script = listOf(OrderStatus.PICKED_UP, OrderStatus.DELIVERED))
        val s = Setup(api)

        s.tracker.startTracking(42)
        s.advanceTimeBy(60_000)

        assertEquals(listOf(OrderStatus.PICKED_UP, OrderStatus.DELIVERED), s.updates)
        assertEquals(2, api.fetchedOrders.size)
        s.tracker.destroy()
    }
}
