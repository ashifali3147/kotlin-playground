@file:OptIn(ExperimentalCoroutinesApi::class)

package challenges.flow

import challenges.flow.f05_menu.Menu
import challenges.flow.f05_menu.MenuApi
import challenges.flow.f05_menu.MenuCache
import challenges.flow.f05_menu.MenuRepository
import challenges.flow.f05_menu.MenuState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class F05MenuRepositoryTest {

    private val cachedMenu = Menu(listOf("dal (cached)"))
    private val freshMenu = Menu(listOf("dal", "naan"))

    private class FakeCache(private val menu: Menu) : MenuCache {
        val threads = ConcurrentLinkedQueue<String>()
        var reads = 0
        var written: Menu? = null

        override fun read(restaurantId: Int): Menu {
            threads += Thread.currentThread().name
            reads++
            return menu
        }

        override fun write(restaurantId: Int, menu: Menu) {
            threads += Thread.currentThread().name
            written = menu
        }
    }

    /** Fails the first [failures] calls with [error], then succeeds. Each call takes 100ms. */
    private class FakeApi(
        private val menu: Menu,
        private val failures: Int = 0,
        private val error: () -> Exception = { IOException("network unreachable") },
    ) : MenuApi {
        val callTimes = mutableListOf<Long>()
        var clock: () -> Long = { 0 }

        override suspend fun fetchMenu(restaurantId: Int): Menu {
            callTimes += clock()
            delay(100)
            if (callTimes.size <= failures) throw error()
            return menu
        }
    }

    @Test
    fun `happy path - cached first, then fresh, cache updated`() = runTest {
        val cache = FakeCache(cachedMenu)
        val repo = MenuRepository(cache, FakeApi(freshMenu), StandardTestDispatcher(testScheduler))

        val states = repo.menu(1).toList()

        assertEquals(listOf(MenuState.Cached(cachedMenu), MenuState.Fresh(freshMenu)), states)
        assertEquals(freshMenu, cache.written)
    }

    @Test
    fun `disk access never runs on the collector's thread`() = runBlocking {
        val main = Executors.newSingleThreadExecutor { Thread(it, "main-ui") }.asCoroutineDispatcher()
        val io = Executors.newSingleThreadExecutor { Thread(it, "disk-io") }.asCoroutineDispatcher()
        val cache = FakeCache(cachedMenu)
        try {
            val repo = MenuRepository(cache, FakeApi(freshMenu), io)
            withContext(main) { repo.menu(1).collect() }
        } finally {
            main.close()
            io.close()
        }

        assertTrue(cache.threads.isNotEmpty())
        assertTrue(
            cache.threads.all { it.startsWith("disk-io") },
            "StrictMode: disk access on the wrong thread: ${cache.threads}"
        )
    }

    @Test
    fun `flaky network - retried with backoff, cache emitted only once`() = runTest {
        val cache = FakeCache(cachedMenu)
        val api = FakeApi(freshMenu, failures = 2).also { it.clock = { currentTime } }
        val repo = MenuRepository(cache, api, StandardTestDispatcher(testScheduler))

        val states = repo.menu(1).toList()

        assertEquals(listOf(MenuState.Cached(cachedMenu), MenuState.Fresh(freshMenu)), states)
        assertEquals(listOf(0L, 1_100L, 3_200L), api.callTimes, "Expected attempts at 0, +100+1000, +100+2000")
        assertEquals(1, cache.reads, "Retrying the network re-read the cache")
    }

    @Test
    fun `network keeps failing - offline with the cached menu`() = runTest {
        val api = FakeApi(freshMenu, failures = 99)
        val repo = MenuRepository(FakeCache(cachedMenu), api, StandardTestDispatcher(testScheduler))

        val states = repo.menu(1).toList()

        assertEquals(
            listOf(MenuState.Cached(cachedMenu), MenuState.Offline(cachedMenu, "network unreachable")),
            states,
        )
        assertEquals(MenuRepository.MAX_NETWORK_ATTEMPTS, api.callTimes.size)
    }

    @Test
    fun `a bug (non-IO error) is not retried, but still shown as offline`() = runTest {
        val api = FakeApi(freshMenu, failures = 99, error = { IllegalStateException("bad JSON") })
        val repo = MenuRepository(FakeCache(cachedMenu), api, StandardTestDispatcher(testScheduler))

        val states = repo.menu(1).toList()

        assertEquals(1, api.callTimes.size, "Retried an error that will never succeed")
        assertEquals(MenuState.Offline(cachedMenu, "bad JSON"), states.last())
    }

    @Test
    fun `a crash in the screen's own code reaches the screen unchanged`() = runTest {
        val repo = MenuRepository(FakeCache(cachedMenu), FakeApi(freshMenu), StandardTestDispatcher(testScheduler))
        val uiBug = IllegalArgumentException("RecyclerView: inconsistency detected")

        val thrown = assertFailsWith<IllegalArgumentException> {
            repo.menu(1).collect { state -> if (state is MenuState.Cached) throw uiBug }
        }

        // Debug mode may copy the exception for better stack traces, so compare by type and message.
        assertEquals(uiBug.message, thrown.message, "The UI's exception was swallowed or replaced")
    }

    @Test
    fun `leaving the screen is not reported as offline`() = runTest {
        val api = FakeApi(freshMenu, failures = 99)
        val repo = MenuRepository(FakeCache(cachedMenu), api, StandardTestDispatcher(testScheduler))
        val states = mutableListOf<MenuState>()

        val screen = launch { repo.menu(1).toList(states) }
        advanceTimeBy(500) // in the middle of retrying
        screen.cancel()
        advanceUntilIdle()

        assertTrue(states.none { it is MenuState.Offline }, "Emitted Offline after the screen was closed: $states")
    }
}
