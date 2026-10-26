@file:OptIn(ExperimentalCoroutinesApi::class)

package challenges.coroutines

import challenges.coroutines.k09_retry.retryWithBackoff
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class K09RetryWithBackoffTest {

    /** Records the virtual time of each attempt. */
    private class Recorder(private val scope: TestScope) {
        val attemptTimes = mutableListOf<Long>()
        val attemptNumbers = mutableListOf<Int>()

        fun record(attempt: Int) {
            attemptTimes += scope.currentTime
            attemptNumbers += attempt
        }
    }

    @Test
    fun `spec 1 - succeeds on the third attempt`() = runTest {
        val rec = Recorder(this)

        val result = retryWithBackoff(maxAttempts = 5, initialDelayMs = 100, maxDelayMs = 10_000) { attempt ->
            rec.record(attempt)
            if (attempt < 3) throw IOException("flaky") else "paid"
        }

        assertEquals("paid", result)
        assertEquals(listOf(1, 2, 3), rec.attemptNumbers)
        assertEquals(listOf(0L, 100L, 300L), rec.attemptTimes)
    }

    @Test
    fun `spec 1 - success on the first try does not wait`() = runTest {
        assertEquals(42, retryWithBackoff(3, 100, 1_000) { 42 })
        assertEquals(0, currentTime)
    }

    @Test
    fun `spec 2 - exponential waits, capped at maxDelay`() = runTest {
        val rec = Recorder(this)

        assertFailsWith<IOException> {
            retryWithBackoff(maxAttempts = 7, initialDelayMs = 100, maxDelayMs = 800) { attempt ->
                rec.record(attempt)
                throw IOException("down")
            }
        }

        // waits: 100, 200, 400, 800, 800, 800
        assertEquals(listOf(0L, 100L, 300L, 700L, 1_500L, 2_300L, 3_100L), rec.attemptTimes)
    }

    @Test
    fun `spec 2 - custom factor`() = runTest {
        val rec = Recorder(this)

        assertFailsWith<IOException> {
            retryWithBackoff(maxAttempts = 4, initialDelayMs = 100, maxDelayMs = 10_000, factor = 1.5) { attempt ->
                rec.record(attempt)
                throw IOException("down")
            }
        }

        // waits: 100, 150, 225
        assertEquals(listOf(0L, 100L, 250L, 475L), rec.attemptTimes)
    }

    @Test
    fun `spec 3 - throws the last error, no wait after the last attempt`() = runTest {
        val errors = List(3) { IOException("failure #${it + 1}") }

        val thrown = assertFailsWith<IOException> {
            retryWithBackoff(maxAttempts = 3, initialDelayMs = 100, maxDelayMs = 1_000) { attempt ->
                throw errors[attempt - 1]
            }
        }

        assertSame(errors.last(), thrown, "Should throw the last error itself")
        assertEquals(300, currentTime, "Waited after the last attempt (100 + 200 = 300 expected)")
    }

    @Test
    fun `spec 3 - maxAttempts 1 means a single try`() = runTest {
        var calls = 0
        assertFailsWith<IOException> {
            retryWithBackoff(1, 100, 1_000) { calls++; throw IOException("x") }
        }
        assertEquals(1, calls)
        assertEquals(0, currentTime)
    }

    @Test
    fun `spec 4 - non-retryable error is thrown immediately`() = runTest {
        var calls = 0
        val declined = IllegalStateException("card declined")

        val thrown = assertFailsWith<IllegalStateException> {
            retryWithBackoff(5, 100, 1_000) { calls++; throw declined }
        }

        assertSame(declined, thrown)
        assertEquals(1, calls)
        assertEquals(0, currentTime)
    }

    @Test
    fun `spec 4 - custom shouldRetry`() = runTest {
        var calls = 0
        assertFailsWith<IllegalStateException> {
            retryWithBackoff(3, 100, 1_000, shouldRetry = { it is IllegalStateException }) {
                calls++
                throw IllegalStateException("HTTP 503")
            }
        }
        assertEquals(3, calls)
    }

    @Test
    fun `spec 5 - CancellationException is never retried`() = runTest {
        var calls = 0

        assertFailsWith<CancellationException> {
            retryWithBackoff(5, 100, 1_000, shouldRetry = { true }) {
                calls++
                throw CancellationException("screen closed")
            }
        }

        assertEquals(1, calls, "Retried a cancellation")
    }

    @Test
    fun `spec 6 - caller cancelled during a wait - stops immediately`() = runTest {
        var calls = 0
        val payment = launch {
            retryWithBackoff(5, 1_000, 10_000) {
                calls++
                throw IOException("down")
            }
        }

        advanceTimeBy(500) // in the first 1000ms wait
        payment.cancel()
        advanceUntilIdle()

        assertEquals(1, calls, "Kept retrying after the caller was cancelled")
        assertEquals(500, currentTime, "Didn't stop immediately")
    }

    @Test
    fun `spec 7 - invalid maxAttempts`() = runTest {
        assertFailsWith<IllegalArgumentException> { retryWithBackoff(0, 100, 1_000) { 1 } }
    }
}
