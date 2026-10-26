package challenges.threads

import challenges.runConcurrently
import challenges.threads.c08_connectionpool.Connection
import challenges.threads.c08_connectionpool.ConnectionPool
import challenges.threads.c08_connectionpool.PoolExhaustedException
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@Timeout(60, unit = TimeUnit.SECONDS)
class C08ConnectionPoolTest {

    private val created = AtomicInteger()
    private val fastFactory = { Connection(created.incrementAndGet()) }

    private fun elapsedMs(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
    }

    @Test
    fun `spec 2 and 3 - lazy creation and reuse`() {
        ConnectionPool(4, fastFactory).use { pool ->
            val a = pool.acquire(100)
            val b = pool.acquire(100)
            assertEquals(2, created.get(), "Only create connections when needed")

            pool.release(a)
            val again = pool.acquire(100)
            assertSame(a, again, "A released connection should be reused")
            assertEquals(2, created.get())
            pool.release(again)
            pool.release(b)
        }
    }

    @RepeatedTest(3)
    fun `spec 1 and 2 - lunch rush - never more than max in use or created`() {
        val max = 4
        val inUse = AtomicInteger()
        val peak = AtomicInteger()

        ConnectionPool(max, fastFactory).use { pool ->
            runConcurrently(threadCount = 16, timeoutMs = 20_000, namePrefix = "request") {
                repeat(2_000) {
                    pool.withConnection(5_000) {
                        peak.accumulateAndGet(inUse.incrementAndGet(), ::maxOf)
                        inUse.decrementAndGet()
                    }
                }
            }
        }

        assertTrue(peak.get() <= max, "${peak.get()} connections were in use at once (max $max)")
        assertTrue(created.get() <= max, "${created.get()} connections were created (max $max)")
    }

    @Test
    fun `spec 4 - acquire times out instead of hanging`() {
        ConnectionPool(1, fastFactory).use { pool ->
            val held = pool.acquire(100)

            val took = elapsedMs { assertFailsWith<PoolExhaustedException> { pool.acquire(300) } }

            assertTrue(took >= 250, "Gave up too early: ${took}ms of a 300ms timeout")
            assertTrue(took < 1_500, "Took ${took}ms for a 300ms timeout")
            pool.release(held)
        }
    }

    @Test
    fun `spec 4 - a waiting request gets the connection as soon as it is released`() {
        ConnectionPool(1, fastFactory).use { pool ->
            val held = pool.acquire(100)
            thread(isDaemon = true) { Thread.sleep(150); pool.release(held) }

            var got: Connection? = null
            val took = elapsedMs { got = pool.acquire(3_000) }

            assertSame(held, got)
            assertTrue(took < 1_000, "Waited ${took}ms although the connection was released after 150ms")
            pool.release(got!!)
        }
    }

    @Test
    fun `spec 5 - double release is rejected and does not grow the pool`() {
        ConnectionPool(1, fastFactory).use { pool ->
            val c = pool.acquire(100)
            pool.release(c)
            assertFailsWith<IllegalArgumentException> { pool.release(c) }

            val first = pool.acquire(100)
            assertFailsWith<PoolExhaustedException>("The double release secretly grew the pool to 2") {
                pool.acquire(100)
            }
            pool.release(first)
        }
    }

    @Test
    fun `spec 5 - connection from somewhere else is rejected`() {
        ConnectionPool(2, fastFactory).use { pool ->
            assertFailsWith<IllegalArgumentException> { pool.release(Connection(999)) }
        }
    }

    @Test
    fun `spec 6 - withConnection releases even when the block throws`() {
        ConnectionPool(1, fastFactory).use { pool ->
            assertFailsWith<IllegalStateException> { pool.withConnection<Unit>(100) { error("query failed") } }
            assertEquals(42, pool.withConnection(100) { 42 }, "The connection was not released after the failure")
        }
    }

    @Test
    fun `spec 7 - factory failure does not leak capacity`() {
        val attempts = AtomicInteger()
        val flakyFactory = {
            if (attempts.incrementAndGet() == 1) throw IllegalStateException("TLS handshake failed")
            Connection(attempts.get())
        }
        ConnectionPool(1, flakyFactory).use { pool ->
            assertFailsWith<IllegalStateException> { pool.acquire(100) }
            val c = pool.acquire(100) // capacity must still be 1, not 0
            pool.release(c)
        }
    }

    @Test
    fun `spec 8 - slow handshake does not block release and reuse of idle connections`() {
        val handshakeStarted = CountDownLatch(1)
        val finishHandshake = CountDownLatch(1)
        val slow = AtomicInteger(0)
        val factory = {
            if (slow.get() == 1) {
                handshakeStarted.countDown()
                finishHandshake.await(10, TimeUnit.SECONDS)
            }
            Connection(created.incrementAndGet())
        }

        ConnectionPool(2, factory).use { pool ->
            val idle = pool.acquire(100)
            slow.set(1)
            val opener = thread(isDaemon = true, name = "opener") { pool.release(pool.acquire(5_000)) }
            assertTrue(handshakeStarted.await(2, TimeUnit.SECONDS), "Second acquire should be opening a connection")

            val took = elapsedMs {
                pool.release(idle)
                assertSame(idle, pool.acquire(500))
            }
            assertTrue(took < 300, "release/acquire of an idle connection waited ${took}ms for someone else's handshake")

            finishHandshake.countDown()
            opener.join(2_000)
            assertFalse(opener.isAlive)
            pool.release(idle)
        }
    }

    @Test
    fun `spec 9 - close shuts idle connections, rejects new acquires, closes in-use on release`() {
        val pool = ConnectionPool(2, fastFactory)
        val idle = pool.acquire(100)
        val busy = pool.acquire(100)
        pool.release(idle)

        pool.close()

        assertTrue(idle.isClosed, "Idle connection should be closed by close()")
        assertFalse(busy.isClosed, "A connection in use must not be closed under the caller's feet")
        assertFailsWith<IllegalStateException> { pool.acquire(100) }
        pool.release(busy)
        assertTrue(busy.isClosed, "A connection released after close() should be closed")
    }
}
