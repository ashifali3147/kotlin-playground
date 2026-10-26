@file:OptIn(ExperimentalCoroutinesApi::class)

package challenges.coroutines

import challenges.coroutines.k01_home.HomeApi
import challenges.coroutines.k01_home.HomeRepository
import challenges.coroutines.k01_home.Offer
import challenges.coroutines.k01_home.OrderSummary
import challenges.coroutines.k01_home.Profile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class K01HomeRepositoryTest {

    private class FakeHomeApi(
        private val delays: Map<String, Long> = mapOf("profile" to 300, "offers" to 500, "orders" to 400),
        private val failing: Set<String> = emptySet(),
    ) : HomeApi {
        val started: MutableSet<String> = ConcurrentHashMap.newKeySet()
        val completed: MutableSet<String> = ConcurrentHashMap.newKeySet()
        val cancelled: MutableSet<String> = ConcurrentHashMap.newKeySet()
        private val inFlight = AtomicInteger()
        val peakInFlight = AtomicInteger()

        private suspend fun <T> call(name: String, result: T): T {
            started += name
            peakInFlight.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
            try {
                delay(delays.getValue(name))
                if (name in failing) throw IOException("$name: HTTP 503")
                completed += name
                return result
            } catch (e: CancellationException) {
                cancelled += name
                throw e
            } finally {
                inFlight.decrementAndGet()
            }
        }

        override suspend fun profile() = call("profile", Profile("Ashif"))
        override suspend fun offers() = call("offers", listOf(Offer("50% off biryani")))
        override suspend fun recentOrders() = call("orders", listOf(OrderSummary(1), OrderSummary(2)))
    }

    @Test
    fun `loads all three parts of the home screen`() = runTest {
        val home = HomeRepository(FakeHomeApi()).loadHome()

        assertEquals("Ashif", home.profile.name)
        assertEquals(1, home.offers.size)
        assertEquals(2, home.recentOrders.size)
    }

    @Test
    fun `loads in parallel - takes as long as the slowest call, not the sum`() = runTest {
        val api = FakeHomeApi()
        HomeRepository(api).loadHome()

        assertEquals(3, api.peakInFlight.get(), "Only ${api.peakInFlight.get()} of 3 calls ran at the same time")
        assertEquals(
            500, currentTime,
            "Expected 500ms of virtual time (the slowest call), got ${currentTime}ms. " +
                "If it's 0, the work escaped the test's coroutine tree."
        )
    }

    @Test
    fun `user leaves the screen - all in-flight requests are cancelled`() = runTest {
        val api = FakeHomeApi()
        val screen = launch { HomeRepository(api).loadHome() }

        advanceTimeBy(100)
        screen.cancel()
        advanceUntilIdle()

        assertTrue(api.started.isNotEmpty(), "No request was even started")
        assertEquals(api.started, api.cancelled, "Requests that kept running after the user left")
        assertTrue(api.completed.isEmpty(), "These requests finished for nobody: ${api.completed}")
    }

    @Test
    fun `one call fails - error is thrown and the other calls are cancelled`() = runTest {
        val api = FakeHomeApi(
            delays = mapOf("profile" to 300, "offers" to 100, "orders" to 400),
            failing = setOf("offers"),
        )

        assertFailsWith<IOException> { HomeRepository(api).loadHome() }
        advanceUntilIdle()

        assertEquals(setOf("profile", "orders"), api.cancelled, "Sibling calls should be cancelled after the failure")
        assertTrue(api.completed.isEmpty(), "These requests kept downloading after the failure: ${api.completed}")
    }
}
