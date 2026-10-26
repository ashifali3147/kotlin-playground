package challenges.threads

import challenges.runConcurrently
import challenges.threads.c04_avatarcache.Avatar
import challenges.threads.c04_avatarcache.AvatarCache
import challenges.threads.c04_avatarcache.AvatarLoadException
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

@Timeout(60, unit = TimeUnit.SECONDS)
class C04AvatarCacheTest {

    private fun avatar(id: String) = Avatar(id, ByteArray(16))

    @Test
    fun `single thread - loads once and caches`() {
        val loads = AtomicInteger()
        val cache = AvatarCache { id -> loads.incrementAndGet(); avatar(id) }

        val first = cache.get("rahul")
        val second = cache.get("rahul")
        cache.get("priya")

        assertSame(first, second)
        assertEquals(2, loads.get())
    }

    @RepeatedTest(3)
    fun `40 chat rows asking for the same avatar - downloaded exactly once`() {
        val loads = AtomicInteger()
        val cache = AvatarCache { id ->
            loads.incrementAndGet()
            Thread.sleep(50) // network + decode
            avatar(id)
        }
        val results = ConcurrentLinkedQueue<Avatar>()

        runConcurrently(threadCount = 40) { results += cache.get("rahul") }

        assertEquals(1, loads.get(), "The same avatar was downloaded ${loads.get()} times")
        val first = results.first()
        assertTrue(results.all { it === first }, "Callers received different Avatar instances")
    }

    @Test
    fun `different users load in parallel - never wait on each other`() {
        val users = 32
        val allLoadsRunning = CyclicBarrier(users)
        val inFlight = AtomicInteger()
        val peakInFlight = AtomicInteger()

        val cache = AvatarCache { id ->
            peakInFlight.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
            try {
                // Every load waits here until all 32 loads are running at the same time.
                allLoadsRunning.await(3, TimeUnit.SECONDS)
            } catch (e: Exception) {
                throw AssertionError(
                    "Loads for different users are waiting on each other: at most ${peakInFlight.get()} " +
                        "of $users loads were ever running at the same time."
                )
            } finally {
                inFlight.decrementAndGet()
            }
            avatar(id)
        }

        runConcurrently(threadCount = users, timeoutMs = 10_000) { i -> cache.get("user-$i") }
    }

    @Test
    fun `failed load - original exception, not cached, next call retries`() {
        val loads = AtomicInteger()
        val cache = AvatarCache { id ->
            if (loads.incrementAndGet() == 1) throw AvatarLoadException("CDN timeout for $id")
            avatar(id)
        }

        assertFailsWith<AvatarLoadException> { cache.get("rahul") }
        val retried = cache.get("rahul")

        assertEquals("rahul", retried.userId)
        assertEquals(2, loads.get())
    }

    @RepeatedTest(3)
    fun `many callers waiting on a failing load - all get the error, nobody hangs`() {
        val cache = AvatarCache { id ->
            Thread.sleep(100)
            throw AvatarLoadException("404 for $id")
        }
        val failures = AtomicInteger()

        runConcurrently(threadCount = 20) {
            try {
                cache.get("deleted-user")
            } catch (_: AvatarLoadException) {
                failures.incrementAndGet()
            }
        }

        assertEquals(20, failures.get(), "Every caller should receive the AvatarLoadException itself")
    }
}
