package challenges.threads

import challenges.runConcurrently
import challenges.threadDump
import challenges.threads.c06_listeners.ConnectivityMonitor
import challenges.threads.c06_listeners.NetworkListener
import challenges.threads.c06_listeners.NetworkState
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.fail

@Timeout(60, unit = TimeUnit.SECONDS)
class C06ConnectivityMonitorTest {

    @Test
    fun `basic dispatch reaches every listener`() {
        val monitor = ConnectivityMonitor()
        val received = mutableListOf<NetworkState>()
        monitor.addListener { received += it }
        monitor.addListener { received += it }

        monitor.dispatch(NetworkState.OFFLINE)

        assertEquals(listOf(NetworkState.OFFLINE, NetworkState.OFFLINE), received)
    }

    @Test
    fun `main thread only - one-shot listener removes itself during dispatch`() {
        val monitor = ConnectivityMonitor()
        val othersCalled = AtomicInteger()
        monitor.addListener(object : NetworkListener {
            override fun onNetworkChanged(state: NetworkState) {
                monitor.removeListener(this) // "show the 'back online' toast once"
            }
        })
        monitor.addListener { othersCalled.incrementAndGet() }
        monitor.addListener { othersCalled.incrementAndGet() }

        monitor.dispatch(NetworkState.ONLINE)

        assertEquals(2, othersCalled.get(), "Other listeners should each be called once")
        assertEquals(2, monitor.listenerCount(), "The one-shot listener should be gone")
    }

    @Test
    fun `listener added during dispatch gets the next event, not the current one`() {
        val monitor = ConnectivityMonitor()
        val lateEvents = mutableListOf<NetworkState>()
        val late = NetworkListener { lateEvents += it }
        var registered = false
        monitor.addListener {
            if (!registered) {
                registered = true
                monitor.addListener(late)
            }
        }

        monitor.dispatch(NetworkState.OFFLINE)
        monitor.dispatch(NetworkState.ONLINE)

        assertEquals(listOf(NetworkState.ONLINE), lateEvents)
    }

    @RepeatedTest(3)
    fun `screens and sync engine add and remove while the OS dispatches`() {
        val monitor = ConnectivityMonitor()
        val permanentCalls = AtomicInteger()
        repeat(3) { monitor.addListener { permanentCalls.incrementAndGet() } }
        val dispatches = AtomicInteger()
        val churnDone = AtomicBoolean(false)
        var dispatcherError: Throwable? = null

        val binder = thread(isDaemon = true, name = "binder") {
            try {
                while (!churnDone.get()) {
                    monitor.dispatch(NetworkState.METERED)
                    dispatches.incrementAndGet()
                }
            } catch (t: Throwable) {
                dispatcherError = t
            }
        }
        runConcurrently(threadCount = 4, namePrefix = "screen") {
            repeat(5_000) {
                val temp = NetworkListener { }
                monitor.addListener(temp)
                monitor.removeListener(temp)
            }
        }
        churnDone.set(true)
        binder.join()

        dispatcherError?.let { throw AssertionError("dispatch() crashed: $it", it) }
        assertEquals(3, monitor.listenerCount(), "Some add/remove calls were lost")
        assertEquals(3 * dispatches.get(), permanentCalls.get(), "Permanent listeners missed some events")
    }

    @Test
    fun `slow listener does not block other threads from using the monitor`() {
        val monitor = ConnectivityMonitor()
        var stuckDump: String? = null
        monitor.addListener {
            // e.g. the listener hands work to the sync thread and waits for it briefly
            val sync = thread(isDaemon = true, name = "sync-engine") { monitor.addListener { } }
            sync.join(2_000)
            if (sync.isAlive) stuckDump = threadDump(listOf(sync))
        }

        monitor.dispatch(NetworkState.ONLINE)

        stuckDump?.let {
            fail(
                "While a listener callback was running, another thread could not call addListener() " +
                    "for 2 seconds. In production, this is how ANRs and deadlocks start.\n\n$it"
            )
        }
    }
}
