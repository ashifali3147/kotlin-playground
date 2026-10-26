@file:OptIn(ExperimentalCoroutinesApi::class)

package challenges.flow

import challenges.flow.f01_cart.Cart
import challenges.flow.f01_cart.CartApi
import challenges.flow.f01_cart.CartRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class F01CartRepositoryTest {

    /** Each fetch takes 100ms and returns a new version. */
    private class FakeCartApi : CartApi {
        var fetches = 0

        override suspend fun fetchCart(): Cart {
            fetches++
            val version = fetches
            delay(100)
            return Cart(items = 2, version = version)
        }
    }

    private class Collector(val job: Job, val received: MutableList<Cart>) {
        val versions get() = received.map { it.version }
    }

    private fun TestScope.collect(flow: Flow<Cart>): Collector {
        val received = mutableListOf<Cart>()
        val job = backgroundScope.launch { flow.toList(received) }
        return Collector(job, received)
    }

    @Test
    fun `three collectors on one screen - one fetch`() = runTest {
        val api = FakeCartApi()
        val repo = CartRepository(api, backgroundScope)

        val collectors = List(3) { collect(repo.cart) }
        advanceTimeBy(150)

        assertEquals(1, api.fetches, "The cart was fetched ${api.fetches} times for one screen")
        collectors.forEach { assertEquals(listOf(1), it.versions) }
    }

    @Test
    fun `late collector gets the latest cart immediately, without a new fetch`() = runTest {
        val api = FakeCartApi()
        val repo = CartRepository(api, backgroundScope)
        collect(repo.cart)
        advanceTimeBy(1_000)

        val late = collect(repo.cart)
        runCurrent() // no time passes

        assertEquals(listOf(1), late.versions, "A late collector should get the current cart instantly")
        assertEquals(1, api.fetches)
    }

    @Test
    fun `refresh loop is shared too`() = runTest {
        val api = FakeCartApi()
        val repo = CartRepository(api, backgroundScope)

        val collectors = List(3) { collect(repo.cart) }
        advanceTimeBy(CartRepository.REFRESH_MS + 300)

        assertEquals(2, api.fetches, "Each refresh should be one fetch, not one per collector")
        collectors.forEach { assertEquals(listOf(1, 2), it.versions) }
    }

    @Test
    fun `rotation - collectors leave and come back quickly - nothing restarts`() = runTest {
        val api = FakeCartApi()
        val repo = CartRepository(api, backgroundScope)
        val before = List(3) { collect(repo.cart) }
        advanceTimeBy(1_000)

        before.forEach { it.job.cancel() } // activity destroyed
        advanceTimeBy(2_000) // ...and recreated 2s later
        val after = collect(repo.cart)
        runCurrent()

        assertEquals(listOf(1), after.versions, "After rotation, the cart should be there instantly")
        assertEquals(1, api.fetches, "Rotation caused a new fetch")
    }

    @Test
    fun `nobody collecting for a while - refresh loop stops, and no ghost cart on return`() = runTest {
        val api = FakeCartApi()
        val repo = CartRepository(api, backgroundScope)
        val screen = collect(repo.cart)
        advanceTimeBy(1_000)

        screen.job.cancel() // user leaves the app
        advanceTimeBy(60_000)
        assertEquals(1, api.fetches, "Kept refreshing the cart while nobody was looking")

        val back = collect(repo.cart) // user comes back
        runCurrent()
        assertEquals(emptyList(), back.versions, "Ghost cart: the stale cart was shown before the fresh fetch")

        advanceTimeBy(150)
        assertEquals(listOf(2), back.versions)
        assertEquals(2, api.fetches)
    }
}
