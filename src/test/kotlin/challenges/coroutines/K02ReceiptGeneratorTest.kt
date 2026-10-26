package challenges.coroutines

import challenges.coroutines.k02_receipt.ReceiptGenerator
import challenges.coroutines.k02_receipt.ReceiptStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Timeout(30, unit = TimeUnit.SECONDS)
class K02ReceiptGeneratorTest {

    private val main: ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor { Thread(it, MAIN_THREAD) }.asCoroutineDispatcher()

    @AfterEach
    fun tearDown() = main.close()

    private class FakeStorage : ReceiptStorage {
        val threadsUsed = ConcurrentLinkedQueue<String>()
        val saved = ConcurrentLinkedQueue<Int>()

        override fun readTemplate(): ByteArray {
            threadsUsed += Thread.currentThread().name
            Thread.sleep(200) // blocking disk read
            return ByteArray(4096)
        }

        override fun save(orderId: Int, pdf: ByteArray) {
            threadsUsed += Thread.currentThread().name
            Thread.sleep(200) // blocking disk write
            saved += orderId
        }
    }

    @Test
    fun `generates and saves the receipt`() = runBlocking {
        val storage = FakeStorage()
        val receipt = withContext(main) { ReceiptGenerator(storage).generate(7) }

        assertEquals(7, receipt.orderId)
        assertEquals(1024, receipt.sizeBytes)
        assertEquals(listOf(7), storage.saved.toList())
    }

    @Test
    fun `blocking disk I-O never runs on the main thread`() = runBlocking {
        val storage = FakeStorage()
        withContext(main) { ReceiptGenerator(storage).generate(1) }

        assertTrue(
            storage.threadsUsed.none { it.startsWith(MAIN_THREAD) },
            "StrictMode violation: disk I/O on the main thread (threads used: ${storage.threadsUsed})"
        )
    }

    @Test
    fun `order placed animation keeps running while the receipt is generated`() = runBlocking {
        val frames = withContext(main) {
            var frames = 0
            val animation = launch {
                while (isActive) {
                    delay(16) // one frame at ~60fps
                    frames++
                }
            }
            val started = System.nanoTime()
            ReceiptGenerator(FakeStorage()).generate(2)
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            animation.cancel()
            frames to elapsedMs
        }

        val (drawn, elapsedMs) = frames
        val expected = elapsedMs / 16
        assertTrue(
            drawn >= expected * 0.75,
            "Animation drew $drawn frames in ${elapsedMs}ms (expected ~$expected). The main thread was frozen."
        )
    }

    @Test
    fun `still works when called from a background thread`() = runBlocking {
        val storage = FakeStorage()
        val receipts = List(3) { id -> async(Dispatchers.Default) { ReceiptGenerator(storage).generate(id) } }
            .map { it.await() }

        assertEquals(listOf(0, 1, 2), receipts.map { it.orderId })
    }

    companion object {
        private const val MAIN_THREAD = "main-ui"
    }
}
