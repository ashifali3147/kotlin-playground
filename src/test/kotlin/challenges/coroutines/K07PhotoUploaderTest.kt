package challenges.coroutines

import challenges.coroutines.k07_photos.Photo
import challenges.coroutines.k07_photos.PhotoApi
import challenges.coroutines.k07_photos.PhotoUploader
import challenges.coroutines.k07_photos.PhotoUploader.Companion.MAX_PARALLEL_UPLOADS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Timeout(60, unit = TimeUnit.SECONDS)
class K07PhotoUploaderTest {

    private class FakeCdn(
        private val uploadMs: (Photo) -> Long = { 30 },
        private val failing: Set<Int> = emptySet(),
    ) : PhotoApi {
        private val inFlight = AtomicInteger()
        val peak = AtomicInteger()
        val started = AtomicInteger()
        val cancelled = AtomicInteger()

        override suspend fun upload(photo: Photo): String {
            started.incrementAndGet()
            peak.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
            try {
                delay(uploadMs(photo))
                if (photo.id in failing) throw IOException("HTTP 500 for photo ${photo.id}")
                return "https://cdn.example.com/${photo.id}.jpg"
            } catch (e: CancellationException) {
                cancelled.incrementAndGet()
                throw e
            } finally {
                inFlight.decrementAndGet()
            }
        }
    }

    private val photos = List(60) { Photo(it) }

    @RepeatedTest(3)
    fun `never more than 4 uploads at once`() = runBlocking {
        val cdn = FakeCdn()
        val urls = PhotoUploader(cdn).uploadAll(photos) { _, _ -> }

        assertTrue(cdn.peak.get() <= MAX_PARALLEL_UPLOADS, "${cdn.peak.get()} uploads ran at once. CDN says 429!")
        assertEquals(photos.map { "https://cdn.example.com/${it.id}.jpg" }, urls, "URLs must be in input order")
    }

    @Test
    fun `uses all 4 slots - a slow photo does not hold up the others`() = runBlocking {
        // Every 4th photo is slow. A good solution starts the next upload as soon as ANY slot frees up.
        val cdn = FakeCdn(uploadMs = { if (it.id % 4 == 0) 200 else 20 })
        val start = System.nanoTime()

        PhotoUploader(cdn).uploadAll(List(32) { Photo(it) }) { _, _ -> }

        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
        assertEquals(MAX_PARALLEL_UPLOADS, cdn.peak.get(), "Never reached $MAX_PARALLEL_UPLOADS uploads at once")
        // Ideal is ~8 slow x 200ms / 4 slots + fast ones in between ≈ 500-700ms. Batches of 4 ≈ 1600ms.
        assertTrue(tookMs < 1_200, "Took ${tookMs}ms. Slots sit idle while waiting for slow uploads.")
    }

    @RepeatedTest(5)
    fun `progress reports every count exactly once and reaches the total`() = runBlocking {
        val reported = ConcurrentLinkedQueue<Int>()
        val manyPhotos = List(200) { Photo(it) }

        PhotoUploader(FakeCdn(uploadMs = { 1 })).uploadAll(manyPhotos) { done, total ->
            assertEquals(200, total)
            reported += done
        }

        assertEquals((1..200).toList(), reported.sorted(), "Progress counts were lost or duplicated")
    }

    @Test
    fun `one failure cancels the remaining uploads`() = runBlocking {
        val cdn = FakeCdn(failing = setOf(5))

        assertFailsWith<IOException> { PhotoUploader(cdn).uploadAll(photos) { _, _ -> } }

        assertTrue(cdn.started.get() < photos.size, "All ${photos.size} uploads started despite the early failure")
    }
}
