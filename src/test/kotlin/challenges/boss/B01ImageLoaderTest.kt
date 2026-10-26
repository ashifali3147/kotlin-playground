package challenges.boss

import challenges.boss.b01_imageloader.Bitmap
import challenges.boss.b01_imageloader.Decoder
import challenges.boss.b01_imageloader.DiskCache
import challenges.boss.b01_imageloader.ImageLoader
import challenges.boss.b01_imageloader.MemoryCache
import challenges.boss.b01_imageloader.Network
import challenges.runConcurrently
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Timeout(60, unit = TimeUnit.SECONDS)
class B01ImageLoaderTest {

    private fun pool(name: String, threads: Int): ExecutorCoroutineDispatcher {
        val n = AtomicInteger()
        return Executors.newFixedThreadPool(threads) { Thread(it, "$name-${n.incrementAndGet()}").apply { isDaemon = true } }
            .asCoroutineDispatcher()
    }

    private val io = pool("img-io", 4)
    private val cpu = pool("img-cpu", 2)
    private val main = pool("main-ui", 1)
    private val appScope = CoroutineScope(SupervisorJob())

    @AfterEach
    fun tearDown() {
        appScope.cancel()
        io.close(); cpu.close(); main.close()
    }

    private class FakeNetwork(
        private val delayMs: (String) -> Long = { 50 },
        private val failOnce: Set<String> = emptySet(),
    ) : Network {
        val downloads = ConcurrentHashMap<String, AtomicInteger>()
        val cancelled = ConcurrentLinkedQueue<String>()
        private val inFlight = AtomicInteger()
        val peakInFlight = AtomicInteger()
        private val failedAlready = ConcurrentHashMap.newKeySet<String>()

        fun count(url: String) = downloads[url]?.get() ?: 0
        val total get() = downloads.values.sumOf { it.get() }

        override suspend fun download(url: String): ByteArray {
            downloads.computeIfAbsent(url) { AtomicInteger() }.incrementAndGet()
            peakInFlight.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
            try {
                delay(delayMs(url))
                if (url in failOnce && failedAlready.add(url)) throw IOException("HTTP 503 for $url")
                return ByteArray(64)
            } catch (e: CancellationException) {
                cancelled += url
                throw e
            } finally {
                inFlight.decrementAndGet()
            }
        }
    }

    private class FakeDisk(preloaded: Set<String> = emptySet()) : DiskCache {
        val files = ConcurrentHashMap<String, ByteArray>().apply { preloaded.forEach { put(it, ByteArray(64)) } }
        val threads = ConcurrentLinkedQueue<String>()

        override fun read(url: String): ByteArray? {
            threads += Thread.currentThread().name
            return files[url]
        }

        override fun write(url: String, data: ByteArray) {
            threads += Thread.currentThread().name
            files[url] = data
        }
    }

    private class FakeDecoder : Decoder {
        val decodes = AtomicInteger()
        val threads = ConcurrentLinkedQueue<String>()

        override fun decode(url: String, data: ByteArray): Bitmap {
            threads += Thread.currentThread().name
            decodes.incrementAndGet()
            Thread.sleep(2) // pretend to decode
            return Bitmap(url, data.size * 4)
        }
    }

    private fun loader(
        network: Network = FakeNetwork(),
        disk: DiskCache = FakeDisk(),
        decoder: Decoder = FakeDecoder(),
        memory: MemoryCache = MemoryCache(50),
    ) = ImageLoader(network, disk, decoder, memory, io, cpu, appScope)

    @Test
    fun `spec 1 - download, disk, decode, memory - second load is a memory hit`() = runBlocking<Unit> {
        val network = FakeNetwork()
        val disk = FakeDisk()
        val memory = MemoryCache(50)
        val images = loader(network, disk, memory = memory)

        val first = images.load("biryani.jpg")
        val second = images.load("biryani.jpg")

        assertTrue(first === second, "Second load should come from memory (same instance)")
        assertEquals(1, network.count("biryani.jpg"))
        assertTrue(disk.files.containsKey("biryani.jpg"), "Downloaded image was not written to disk")
        assertEquals(1, memory.size())
    }

    @Test
    fun `spec 1 - disk hit skips the network`() = runBlocking<Unit> {
        val network = FakeNetwork()
        val images = loader(network, FakeDisk(preloaded = setOf("naan.jpg")))

        images.load("naan.jpg")

        assertEquals(0, network.total)
    }

    @Test
    fun `spec 2 - fast scrolling on many threads - no crash, cache stays bounded`() = runBlocking<Unit> {
        val memory = MemoryCache(10)
        val images = loader(FakeNetwork(delayMs = { 1 }), memory = memory)

        withTimeout(20_000) {
            List(400) { i -> async(Dispatchers.Default) { images.load("dish-${i % 60}.jpg") } }.awaitAll()
        }

        assertTrue(memory.size() <= 10, "Memory cache grew to ${memory.size()} (max 10)")
    }

    @Test
    fun `spec 2 - memory cache hammered directly from 8 threads - consistent and bounded`() {
        val memory = MemoryCache(10)
        val bitmaps = List(30) { Bitmap("img-$it.jpg", 1) }

        runConcurrently(threadCount = 8, timeoutMs = 10_000) { t ->
            repeat(50_000) { i ->
                val b = bitmaps[(i * 7 + t) % bitmaps.size]
                if (i % 3 == 0) memory.put(b.url, b) else memory.get(b.url)
            }
        }

        assertTrue(memory.size() <= 10, "Memory cache grew to ${memory.size()} (max 10)")
        bitmaps.forEach { memory.get(it.url) } // must not throw on a corrupted map
    }

    @Test
    fun `spec 3 - 40 rows want the same image - one download, one decode, same bitmap`() = runBlocking<Unit> {
        val network = FakeNetwork()
        val decoder = FakeDecoder()
        val images = loader(network, decoder = decoder)

        val bitmaps = List(40) { async(Dispatchers.Default) { images.load("thali.jpg") } }.awaitAll()

        assertEquals(1, network.count("thali.jpg"), "Downloaded ${network.count("thali.jpg")} times")
        assertEquals(1, decoder.decodes.get(), "Decoded ${decoder.decodes.get()} times")
        assertTrue(bitmaps.all { it === bitmaps.first() }, "Rows received different Bitmap instances")
    }

    @Test
    fun `spec 4 - disk and decoding never run on the caller's thread`() = runBlocking<Unit> {
        val disk = FakeDisk()
        val decoder = FakeDecoder()
        val images = loader(disk = disk, decoder = decoder)

        withContext(main) { images.load("dosa.jpg") }

        assertTrue(disk.threads.isNotEmpty() && disk.threads.all { it.startsWith("img-io") }, "Disk ran on: ${disk.threads}")
        assertTrue(decoder.threads.all { it.startsWith("img-cpu") }, "Decode ran on: ${decoder.threads}")
    }

    @Test
    fun `spec 5 - never more than 4 downloads at once`() = runBlocking<Unit> {
        val network = FakeNetwork(delayMs = { 50 })
        val images = loader(network)

        List(20) { i -> async(Dispatchers.Default) { images.load("img-$i.jpg") } }.awaitAll()

        assertTrue(network.peakInFlight.get() <= ImageLoader.MAX_PARALLEL_DOWNLOADS, "${network.peakInFlight.get()} downloads at once → HTTP 429")
        assertEquals(ImageLoader.MAX_PARALLEL_DOWNLOADS, network.peakInFlight.get(), "Download slots were not fully used")
    }

    @Test
    fun `spec 5 - disk hits do not wait for download slots`() = runBlocking<Unit> {
        val images = loader(FakeNetwork(delayMs = { 2_000 }), FakeDisk(preloaded = setOf("cached.jpg")))
        val slow = List(4) { i -> async(Dispatchers.Default) { images.load("slow-$i.jpg") } }
        delay(100) // all 4 slots busy

        val start = System.nanoTime()
        images.load("cached.jpg")
        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)

        assertTrue(tookMs < 500, "A disk hit waited ${tookMs}ms behind downloads")
        slow.awaitAll()
    }

    @Test
    fun `spec 6 - failed load - every waiter gets the error, next load retries`() = runBlocking<Unit> {
        val network = FakeNetwork(failOnce = setOf("paneer.jpg"))
        val images = loader(network)

        val attempts = List(5) { async(Dispatchers.Default) { runCatching { images.load("paneer.jpg") } } }.awaitAll()
        assertTrue(attempts.all { it.exceptionOrNull() is IOException }, "Every waiter should get the IOException: $attempts")

        val retry = images.load("paneer.jpg")
        assertEquals("paneer.jpg", retry.url)
        assertEquals(2, network.count("paneer.jpg"), "The failure was cached forever")
    }

    @Test
    fun `spec 7 - one row recycled - other rows still get the image, no second download`() = runBlocking<Unit> {
        val network = FakeNetwork(delayMs = { 300 })
        val images = loader(network)

        val recycled = async(Dispatchers.Default) { images.load("lassi.jpg") }
        val visible = async(Dispatchers.Default) { images.load("lassi.jpg") }
        delay(100)
        recycled.cancel()

        assertEquals("lassi.jpg", visible.await().url)
        assertEquals(1, network.count("lassi.jpg"))
        assertTrue(network.cancelled.isEmpty(), "The shared download was cancelled because one row went away")
    }

    @Test
    fun `spec 7 - every row recycled - download cancelled, a later load starts fresh`() = runBlocking<Unit> {
        val network = FakeNetwork(delayMs = { 500 })
        val images = loader(network)

        val rows = List(3) { async(Dispatchers.Default) { images.load("kulfi.jpg") } }
        delay(100)
        rows.forEach { it.cancel() }
        delay(200)

        assertEquals(listOf("kulfi.jpg"), network.cancelled.toList(), "Nobody wants the image any more, but it kept downloading")

        val later = images.load("kulfi.jpg")
        assertEquals("kulfi.jpg", later.url)
        assertEquals(2, network.count("kulfi.jpg"))
    }

    @Test
    fun `spec 6 and 3 - an image that failed for one row is not stuck for the others`() = runBlocking<Unit> {
        val network = FakeNetwork(failOnce = setOf("roti.jpg"))
        val images = loader(network)

        assertFailsWith<IOException> { images.load("roti.jpg") }
        val bitmaps = List(10) { async(Dispatchers.Default) { images.load("roti.jpg") } }.awaitAll()

        assertTrue(bitmaps.all { it === bitmaps.first() })
        assertEquals(2, network.count("roti.jpg"))
    }
}
