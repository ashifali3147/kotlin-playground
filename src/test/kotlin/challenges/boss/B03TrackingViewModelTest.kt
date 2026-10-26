@file:OptIn(ExperimentalCoroutinesApi::class)

package challenges.boss

import app.cash.turbine.test
import challenges.boss.b03_tracking.Connection
import challenges.boss.b03_tracking.EtaCalculator
import challenges.boss.b03_tracking.Location
import challenges.boss.b03_tracking.OrderStatus
import challenges.boss.b03_tracking.RiderListener
import challenges.boss.b03_tracking.RiderSocket
import challenges.boss.b03_tracking.RiderUpdate
import challenges.boss.b03_tracking.TrackingApi
import challenges.boss.b03_tracking.TrackingEvent
import challenges.boss.b03_tracking.TrackingUiState
import challenges.boss.b03_tracking.TrackingViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Timeout(60, unit = TimeUnit.SECONDS)
class B03TrackingViewModelTest {

    private val home = Location(12.9700, 77.5900)

    /** A rider [minutes] away from home, for the fake ETA (1 minute per 0.001 lat). */
    private fun riderAt(minutes: Int) = Location(home.lat + minutes * 0.001, home.lng)

    private fun update(status: OrderStatus, minutes: Int) = RiderUpdate(status, riderAt(minutes))

    /** Broadcasts each update to every open connection. */
    private class FakeSocket : RiderSocket {
        val listeners = CopyOnWriteArrayList<RiderListener>()
        val connects = AtomicInteger()
        val disconnects = AtomicInteger()

        override fun connect(orderId: Int, listener: RiderListener): Connection {
            connects.incrementAndGet()
            listeners += listener
            return Connection {
                disconnects.incrementAndGet()
                listeners -= listener
            }
        }

        val open get() = connects.get() - disconnects.get()
        fun push(update: RiderUpdate) = listeners.forEach { it.onUpdate(update) }
        fun fail(error: Throwable) = listeners.forEach { it.onError(error) }
    }

    private class FakeApi(private val scope: TestScope?, private val script: List<RiderUpdate>) : TrackingApi {
        val pollTimes = mutableListOf<Long>()

        override suspend fun latest(orderId: Int): RiderUpdate {
            pollTimes += scope?.currentTime ?: 0
            delay(100)
            return script[minOf(pollTimes.size - 1, script.lastIndex)]
        }
    }

    private inner class FakeEta : EtaCalculator {
        val threads = ConcurrentLinkedQueue<String>()

        override fun etaMinutes(rider: Location, customer: Location): Int {
            threads += Thread.currentThread().name
            return Math.round(abs(rider.lat - customer.lat) * 1000).toInt()
        }
    }

    private fun TestScope.childOfBackground() =
        CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job]))

    private inner class Setup(test: TestScope, apiScript: List<RiderUpdate> = listOf(update(OrderStatus.ON_THE_WAY, 9))) {
        val socket = FakeSocket()
        val api = FakeApi(test, apiScript)
        val eta = FakeEta()
        val dispatcher = StandardTestDispatcher(test.testScheduler)

        // Children of backgroundScope: on the test's virtual clock, and always cancelled when the test ends,
        // so an endless loop in unfinished code can't hang or crash the test run.
        val vmScope = test.childOfBackground()
        val vm = TrackingViewModel(42, home, socket, api, eta, dispatcher, vmScope)
        val states = mutableListOf<TrackingUiState>()
        val screenScope = test.childOfBackground()

        init {
            screenScope.launch { vm.uiState.collect { states += it } }
        }

        val shown get() = states.filter { it.status != null }
        fun close() {
            screenScope.cancel()
            vmScope.cancel()
        }
    }

    @Test
    fun `spec 1 - updates are shown with status, position and ETA`() = runTest {
        val s = Setup(this)
        runCurrent()

        s.socket.push(update(OrderStatus.ON_THE_WAY, 7))
        runCurrent()

        assertEquals(TrackingUiState(OrderStatus.ON_THE_WAY, riderAt(7), 7, usingFallback = false), s.shown.last())
        s.close()
    }

    @Test
    fun `spec 2 - one socket connection, however many collectors`() = runTest {
        val s = Setup(this)
        s.screenScope.launch { s.vm.uiState.collect { } } // the ETA banner
        s.screenScope.launch { s.vm.uiState.collect { } } // the status stepper
        runCurrent()

        assertEquals(1, s.socket.connects.get(), "Opened ${s.socket.connects.get()} socket connections for one screen")
        s.close()
    }

    @Test
    fun `spec 3 - screen gone - socket disconnected`() = runTest {
        val s = Setup(this)
        runCurrent()
        s.socket.push(update(OrderStatus.ON_THE_WAY, 7))
        runCurrent()

        s.close()
        runCurrent()

        assertEquals(0, s.socket.open, "Socket connections left open after the screen was destroyed")
    }

    @Test
    fun `spec 6 - socket fails - polls every POLL_MS with the fallback flag, until delivered`() = runTest {
        val s = Setup(
            this,
            apiScript = listOf(
                update(OrderStatus.ON_THE_WAY, 9),
                update(OrderStatus.ON_THE_WAY, 6),
                update(OrderStatus.ON_THE_WAY, 3),
                update(OrderStatus.DELIVERED, 0),
            ),
        )
        runCurrent()
        val failedAt = currentTime

        s.socket.fail(IOException("socket closed"))
        advanceTimeBy(5 * TrackingViewModel.POLL_MS)
        runCurrent()

        val p = TrackingViewModel.POLL_MS
        assertEquals(listOf(0L, p + 100, 2 * p + 200, 3 * p + 300).map { it + failedAt }, s.api.pollTimes, "Polls should repeat every POLL_MS and stop after DELIVERED")
        assertEquals(OrderStatus.DELIVERED, s.shown.last().status)
        assertTrue(s.shown.all { it.usingFallback }, "Fallback updates should have usingFallback = true")
        s.close()
    }

    @Test
    fun `spec 7 - rider nearby fires once, even when the ETA goes up and down`() = runTest {
        val s = Setup(this)
        runCurrent()

        s.vm.events.test {
            for (minutes in listOf(5, 2, 1, 3, 2, 1, 1)) {
                s.socket.push(update(OrderStatus.ON_THE_WAY, minutes))
                runCurrent()
            }
            assertEquals(TrackingEvent.RiderNearby, awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        s.close()
    }

    @Test
    fun `spec 8 - events while backgrounded are delivered in order, and not replayed after rotation`() = runTest {
        val s = Setup(this)
        runCurrent()

        // App in the background: nobody collects events.
        s.socket.push(update(OrderStatus.ON_THE_WAY, 1))
        runCurrent()
        s.socket.push(update(OrderStatus.DELIVERED, 0))
        runCurrent()

        s.vm.events.test {
            assertEquals(TrackingEvent.RiderNearby, awaitItem(), "The 'rider nearby' notification was lost")
            assertEquals(TrackingEvent.OpenRatingScreen, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        s.vm.events.test { // rotation: a new collector
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        s.close()
    }

    @Test
    fun `spec 9 - delivered - rating screen once, socket disconnected`() = runTest {
        val s = Setup(this)
        runCurrent()

        s.vm.events.test {
            s.socket.push(update(OrderStatus.ON_THE_WAY, 4))
            runCurrent()
            s.socket.push(update(OrderStatus.DELIVERED, 0))
            runCurrent()
            assertEquals(TrackingEvent.RiderNearby, awaitItem()) // 0 minutes away counts as nearby (spec 7)
            assertEquals(TrackingEvent.OpenRatingScreen, awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, s.socket.open, "Still connected hours after delivery")
        s.close()
    }

    @Test
    fun `spec 4 and 5 - real threads - socket never blocks, ETA runs on cpu threads, latest position wins`() = runBlocking<Unit> {
        val n = AtomicInteger()
        val cpu = Executors.newFixedThreadPool(2) { Thread(it, "track-cpu-${n.incrementAndGet()}").apply { isDaemon = true } }
            .asCoroutineDispatcher()
        val main = Executors.newSingleThreadExecutor { Thread(it, "main-ui").apply { isDaemon = true } }.asCoroutineDispatcher()
        val socket = FakeSocket()
        val eta = FakeEta()
        val vmScope = CoroutineScope(SupervisorJob() + main)
        val vm = TrackingViewModel(42, home, socket, FakeApi(null, listOf(update(OrderStatus.ON_THE_WAY, 9))), eta, cpu, vmScope)
        val lastShown = AtomicLong(-1)
        val screen = launch(main) {
            vm.uiState.collect { state ->
                delay(50) // slow map animation
                state.rider?.let { lastShown.set(Math.round((it.lat - home.lat) * 1000)) }
            }
        }
        try {
            withTimeoutOrNull(2_000) { while (socket.listeners.isEmpty()) delay(5) }

            val slowestCallbackMs = AtomicLong()
            val reader = thread(name = "socket-reader", isDaemon = true) {
                for (minutes in 300 downTo 100) {
                    val start = System.nanoTime()
                    socket.push(update(OrderStatus.ON_THE_WAY, minutes))
                    slowestCallbackMs.accumulateAndGet(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), ::maxOf)
                }
            }
            reader.join(10_000)
            val reachedLatest = withTimeoutOrNull(3_000) { while (lastShown.get() != 100L) delay(10); true }

            assertTrue(slowestCallbackMs.get() < 20, "A socket callback blocked for ${slowestCallbackMs.get()}ms")
            assertTrue(eta.threads.isNotEmpty() && eta.threads.all { it.startsWith("track-cpu") }, "ETA ran on: ${eta.threads.toSet()}")
            assertNotNull(reachedLatest, "The map never showed the latest rider position (last shown: ${lastShown.get()})")
        } finally {
            screen.cancel()
            vmScope.cancel()
            cpu.close()
            main.close()
        }
    }
}
