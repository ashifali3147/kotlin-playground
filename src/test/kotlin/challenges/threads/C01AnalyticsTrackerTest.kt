package challenges.threads

import challenges.runConcurrently
import challenges.threads.c01_analytics.AnalyticsTracker
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.test.assertEquals

@Timeout(30, unit = TimeUnit.SECONDS)
class C01AnalyticsTrackerTest {

    private val events = listOf("screen_view", "add_to_cart", "checkout", "search")

    @Test
    fun `single thread - counts are correct`() {
        val tracker = AnalyticsTracker()
        repeat(3) { tracker.track("screen_view") }
        tracker.track("checkout")

        assertEquals(4, tracker.lifetimeEvents())
        assertEquals(3, tracker.pendingCountOf("screen_view"))
        assertEquals(mapOf("screen_view" to 3, "checkout" to 1), tracker.drain())
        assertEquals(0, tracker.pendingCountOf("screen_view"))
        assertEquals(4, tracker.lifetimeEvents())
    }

    @RepeatedTest(3)
    fun `8 threads tracking at once - no event is lost`() {
        val tracker = AnalyticsTracker()
        val perThread = 25_000

        runConcurrently(threadCount = 8) { i ->
            repeat(perThread) { n -> tracker.track(events[(i + n) % events.size]) }
        }

        val total = 8 * perThread
        assertEquals(total, tracker.lifetimeEvents(), "lifetimeEvents lost updates")
        val perEvent = total / events.size
        events.forEach {
            assertEquals(perEvent, tracker.pendingCountOf(it), "pending count for '$it' lost updates")
        }
    }

    @Test
    fun `debug screen reading snapshot while tracking - never crashes`() {
        val tracker = AnalyticsTracker()
        val writersDone = AtomicBoolean(false)
        val readerErrors = ConcurrentLinkedQueue<Throwable>()

        val reader = thread(isDaemon = true, name = "debug-screen") {
            while (!writersDone.get()) {
                try {
                    tracker.snapshot()
                } catch (t: Throwable) {
                    readerErrors += t
                    return@thread
                }
                Thread.sleep(0, 200_000) // the screen refreshes often, but not non-stop
            }
        }
        // Many distinct names force the map to grow (and resize) while it's being read.
        runConcurrently(threadCount = 4, timeoutMs = 15_000) { i ->
            repeat(50_000) { n -> tracker.track("event_${i}_${n % 500}") }
        }
        writersDone.set(true)
        reader.join()

        readerErrors.firstOrNull()?.let { throw AssertionError("snapshot() crashed: $it", it) }
    }

    @RepeatedTest(3)
    fun `upload job draining while tracking - every event is uploaded exactly once`() {
        val tracker = AnalyticsTracker()
        val uploaded = ConcurrentLinkedQueue<Map<String, Int>>()
        val writersDone = AtomicBoolean(false)

        val uploader = thread(isDaemon = true, name = "upload-job") {
            while (!writersDone.get()) uploaded += tracker.drain()
        }
        runConcurrently(threadCount = 6) { i ->
            repeat(30_000) { n -> tracker.track(events[(i + n) % events.size]) }
        }
        writersDone.set(true)
        uploader.join()
        uploaded += tracker.drain() // final flush

        val uploadedTotal = uploaded.sumOf { batch -> batch.values.sum() }
        assertEquals(
            6 * 30_000, uploadedTotal,
            "Events tracked != events uploaded. Some were lost (or counted twice) around drain()."
        )
    }
}
