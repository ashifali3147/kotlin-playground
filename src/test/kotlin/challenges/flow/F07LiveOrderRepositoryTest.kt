@file:OptIn(ExperimentalCoroutinesApi::class)

package challenges.flow

import challenges.flow.f07_socket.LiveOrderRepository
import challenges.flow.f07_socket.OrderSocket
import challenges.flow.f07_socket.OrderSocketListener
import challenges.flow.f07_socket.OrderUpdate
import challenges.flow.f07_socket.Subscription
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Timeout(30, unit = TimeUnit.SECONDS)
class F07LiveOrderRepositoryTest {

    private class FakeSocket : OrderSocket {
        @Volatile var listener: OrderSocketListener? = null
        @Volatile var subscribed = 0
        @Volatile var unsubscribed = 0

        override fun subscribe(orderId: Int, listener: OrderSocketListener): Subscription {
            this.listener = listener
            subscribed++
            return Subscription { unsubscribed++ }
        }

        val isOpen get() = subscribed > unsubscribed
    }

    private fun update(seq: Int) = OrderUpdate(orderId = 7, status = "status-$seq", seq = seq)

    private class Screen(val job: Job, val received: MutableList<OrderUpdate>) {
        var error: Throwable? = null
        var completedNormally = false
    }

    /** Collects in a scope that isn't a child of the test, so a flow that never ends can't hang the test. */
    private fun TestScope.open(repo: LiveOrderRepository): Screen {
        val received = mutableListOf<OrderUpdate>()
        lateinit var screen: Screen
        val job = CoroutineScope(Job() + StandardTestDispatcher(testScheduler)).launch {
            try {
                repo.updates(7).toList(received)
                screen.completedNormally = true
            } catch (e: Throwable) {
                screen.error = e
            }
        }
        screen = Screen(job, received)
        return screen
    }

    @Test
    fun `updates are delivered`() = runTest {
        val socket = FakeSocket()
        val screen = open(LiveOrderRepository(socket))
        runCurrent()

        socket.listener!!.onUpdate(update(1))
        runCurrent()

        assertEquals(listOf(update(1)), screen.received)
        screen.job.cancel()
    }

    @Test
    fun `screen closed - unsubscribes from the socket`() = runTest {
        val socket = FakeSocket()
        val screen = open(LiveOrderRepository(socket))
        runCurrent()
        assertTrue(socket.isOpen)

        screen.job.cancel()
        runCurrent()

        assertEquals(1, socket.unsubscribed, "Subscription leaked after the collector was cancelled")
    }

    @Test
    fun `server closes the stream - flow completes normally and unsubscribes`() = runTest {
        val socket = FakeSocket()
        val screen = open(LiveOrderRepository(socket))
        runCurrent()

        socket.listener!!.onUpdate(update(1))
        socket.listener!!.onClosed()
        runCurrent()

        assertTrue(screen.completedNormally, "collect() never finished after the server closed the stream")
        assertEquals(update(1), screen.received.last())
        assertEquals(1, socket.unsubscribed)
    }

    @Test
    fun `socket failure - flow fails with that error and unsubscribes`() = runTest {
        val socket = FakeSocket()
        val screen = open(LiveOrderRepository(socket))
        runCurrent()
        val failure = java.io.IOException("connection reset by peer")

        socket.listener!!.onFailure(failure)
        runCurrent()

        // Debug mode may copy the exception for better stack traces, so compare by type and message.
        val error = screen.error
        assertTrue(error is java.io.IOException && error.message == failure.message, "The collector should fail with the socket's error, got $error")
        assertEquals(1, socket.unsubscribed)
    }

    @Test
    fun `socket thread never blocks on a slow collector, and the latest update wins`() = runBlocking<Unit> {
        val socket = FakeSocket()
        val received = mutableListOf<OrderUpdate>()
        val screen = launch(Dispatchers.Default) {
            LiveOrderRepository(socket).updates(7).collect {
                delay(50) // slow UI
                synchronized(received) { received += it }
            }
        }
        while (socket.listener == null) delay(5)

        val slowestCallbackMs = AtomicLong()
        val reader = thread(name = "socket-reader", isDaemon = true) {
            for (seq in 1..200) {
                val start = System.nanoTime()
                socket.listener!!.onUpdate(update(seq))
                slowestCallbackMs.accumulateAndGet(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), ::maxOf)
            }
        }
        reader.join(5_000)
        val lastDelivered = withTimeoutOrNull(2_000) {
            while (synchronized(received) { received.lastOrNull()?.seq } != 200) delay(10)
            true
        }
        screen.cancel()

        assertTrue(slowestCallbackMs.get() < 20, "A socket callback blocked for ${slowestCallbackMs.get()}ms. Every live feature froze.")
        assertNotNull(lastDelivered, "The latest update (seq 200) never reached the UI")
    }
}
