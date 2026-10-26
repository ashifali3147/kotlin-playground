@file:OptIn(ExperimentalCoroutinesApi::class)

package challenges.coroutines

import challenges.coroutines.k06_token.AuthApi
import challenges.coroutines.k06_token.AuthTokenProvider
import challenges.coroutines.k06_token.Token
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Timeout(30, unit = TimeUnit.SECONDS)
class K06AuthTokenProviderTest {

    private class FakeAuthApi(
        private val refreshMs: Long = 50,
        private val clock: () -> Long = { 0 },
        private val failFirst: Boolean = false,
    ) : AuthApi {
        val refreshes = AtomicInteger()

        override suspend fun refresh(): Token {
            val n = refreshes.incrementAndGet()
            delay(refreshMs)
            if (failFirst && n == 1) throw IOException("auth server timeout")
            return Token("token-$n", expiresAtMs = clock() + 60_000)
        }
    }

    @Test
    fun `single caller - refreshes once, caches until expiry`() = runTest {
        val now = AtomicLong(0)
        val api = FakeAuthApi(clock = now::get)
        val provider = AuthTokenProvider(api, now::get)

        assertEquals("token-1", provider.validToken())
        assertEquals("token-1", provider.validToken())
        now.set(61_000)
        assertEquals("token-2", provider.validToken())
        assertEquals(2, api.refreshes.get())
    }

    @RepeatedTest(5)
    fun `50 requests at once on many threads - exactly one refresh`() = runBlocking {
        val api = FakeAuthApi()
        val provider = AuthTokenProvider(api) { 0 }

        val tokens = List(50) { async(Dispatchers.Default) { provider.validToken() } }.awaitAll()

        assertEquals(1, api.refreshes.get(), "Refresh stampede: ${api.refreshes.get()} refresh calls")
        assertEquals(setOf("token-1"), tokens.toSet(), "Callers received different tokens")
    }

    @RepeatedTest(5)
    fun `50 requests rejected with the same old token - exactly one more refresh`() = runBlocking {
        val api = FakeAuthApi()
        val provider = AuthTokenProvider(api) { 0 }
        val old = provider.validToken() // token-1

        val retried = List(50) {
            async(Dispatchers.Default) {
                provider.invalidate(old) // 401 for the old token
                provider.validToken()
            }
        }.awaitAll()

        assertEquals(2, api.refreshes.get(), "Expected 1 initial + 1 refresh, got ${api.refreshes.get()}")
        assertEquals(setOf("token-2"), retried.toSet())
    }

    @Test
    fun `invalidating a token that was already replaced does nothing`() = runTest {
        val api = FakeAuthApi()
        val provider = AuthTokenProvider(api) { 0 }
        val old = provider.validToken()
        provider.invalidate(old)
        val current = provider.validToken() // token-2

        provider.invalidate(old) // a slow request finally got its 401 for token-1

        assertEquals(current, provider.validToken())
        assertEquals(2, api.refreshes.get())
    }

    @Test
    fun `failed refresh is not cached - next call retries`() = runTest {
        val api = FakeAuthApi(failFirst = true)
        val provider = AuthTokenProvider(api) { 0 }

        assertFailsWith<IOException> { provider.validToken() }
        assertEquals("token-2", provider.validToken())
    }

    @Test
    fun `waiting for the refresh does not block the thread`() = runTest {
        // runTest is single-threaded: if waiting blocked the thread, nothing else could run.
        val provider = AuthTokenProvider(FakeAuthApi(refreshMs = 1_000)) { 0 }
        val waiters = List(5) { async { provider.validToken() } }
        var uiTicks = 0
        val ui = launch { repeat(5) { delay(100); uiTicks++ } }

        advanceTimeBy(600)

        assertEquals(5, uiTicks, "Other coroutines couldn't run while callers waited for the refresh")
        assertEquals(setOf("token-1"), waiters.awaitAll().toSet())
        ui.join()
    }
}
