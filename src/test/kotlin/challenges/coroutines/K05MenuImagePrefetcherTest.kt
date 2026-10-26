@file:OptIn(ExperimentalCoroutinesApi::class)

package challenges.coroutines

import challenges.coroutines.k05_prefetch.ImageDownloader
import challenges.coroutines.k05_prefetch.MenuImagePrefetcher
import challenges.coroutines.k05_prefetch.PrefetchReport
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
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class K05MenuImagePrefetcherTest {

    private class FakeDownloader(
        private val delays: Map<String, Long>,
        private val failing: Map<String, Throwable> = emptyMap(),
    ) : ImageDownloader {
        val completed = mutableListOf<String>()
        val cancelled = mutableListOf<String>()

        override suspend fun download(url: String) {
            try {
                delay(delays.getValue(url))
            } catch (e: CancellationException) {
                cancelled += url
                throw e
            }
            failing[url]?.let { throw it }
            completed += url
        }
    }

    private val urls = listOf("biryani.jpg", "paneer.jpg", "naan.jpg", "lassi.jpg")
    private val delays = mapOf("biryani.jpg" to 400L, "paneer.jpg" to 100L, "naan.jpg" to 300L, "lassi.jpg" to 200L)

    @Test
    fun `all photos download - accurate report`() = runTest {
        val report = MenuImagePrefetcher(FakeDownloader(delays)).prefetch(urls)

        assertEquals(PrefetchReport(succeeded = urls, failed = emptyList()), report)
    }

    @Test
    fun `downloads run in parallel`() = runTest {
        MenuImagePrefetcher(FakeDownloader(delays)).prefetch(urls)

        assertEquals(400, currentTime, "Expected the time of the slowest photo (400ms)")
    }

    @Test
    fun `one broken photo does not affect the others`() = runTest {
        val downloader = FakeDownloader(delays, failing = mapOf("paneer.jpg" to IOException("HTTP 404")))

        val report = MenuImagePrefetcher(downloader).prefetch(urls)

        assertEquals(listOf("biryani.jpg", "naan.jpg", "lassi.jpg"), report.succeeded)
        assertEquals(listOf("paneer.jpg"), report.failed)
        assertTrue(downloader.cancelled.isEmpty(), "These downloads were killed by someone else's failure: ${downloader.cancelled}")
    }

    @Test
    fun `any kind of failure is reported, not only IOException`() = runTest {
        val downloader = FakeDownloader(
            delays,
            failing = mapOf("naan.jpg" to IllegalStateException("corrupt JPEG"), "lassi.jpg" to IOException("timeout")),
        )

        val report = MenuImagePrefetcher(downloader).prefetch(urls)

        assertEquals(listOf("biryani.jpg", "paneer.jpg"), report.succeeded)
        assertEquals(listOf("naan.jpg", "lassi.jpg"), report.failed)
    }

    @Test
    fun `user leaves before prefetch finishes - everything is cancelled, no report`() = runTest {
        val downloader = FakeDownloader(delays)
        var report: PrefetchReport? = null
        val page = launch { report = MenuImagePrefetcher(downloader).prefetch(urls) }

        advanceTimeBy(150) // paneer done, others in flight
        page.cancel()
        advanceUntilIdle()

        assertEquals(listOf("paneer.jpg"), downloader.completed)
        assertEquals(setOf("biryani.jpg", "naan.jpg", "lassi.jpg"), downloader.cancelled.toSet())
        assertNull(report, "A cancelled prefetch should not produce a report")
    }
}
