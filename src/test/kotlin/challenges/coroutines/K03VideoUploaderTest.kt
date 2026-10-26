package challenges.coroutines

import challenges.coroutines.k03_upload.TempFileStore
import challenges.coroutines.k03_upload.UploadApi
import challenges.coroutines.k03_upload.Video
import challenges.coroutines.k03_upload.VideoUploader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Timeout(30, unit = TimeUnit.SECONDS)
class K03VideoUploaderTest {

    private class FakeUploadApi(private val failingChunks: Set<Int> = emptySet()) : UploadApi {
        val chunkAttempts = AtomicInteger()
        val finished = AtomicBoolean(false)

        override suspend fun uploadChunk(videoId: String, chunk: Int) {
            chunkAttempts.incrementAndGet()
            delay(20)
            if (chunk in failingChunks) throw IOException("connection reset")
        }

        override suspend fun finish(videoId: String): String {
            delay(20)
            finished.set(true)
            return "https://cdn.example.com/$videoId.mp4"
        }
    }

    private class FakeTempFiles : TempFileStore {
        val existing: MutableSet<String> = ConcurrentHashMap.newKeySet()

        override suspend fun create(name: String): String {
            delay(5)
            return "/cache/$name.tmp".also { existing += it }
        }

        override suspend fun delete(path: String) {
            delay(5) // runs on the IO dispatcher in the real app
            existing -= path
        }
    }

    @Test
    fun `happy path - uploads, finishes, cleans up`() = runBlocking {
        val api = FakeUploadApi()
        val temp = FakeTempFiles()
        val progress = mutableListOf<Int>()

        val result = VideoUploader(api, temp).compressAndUpload(Video("v1", frames = 20, chunks = 5)) { progress += it }

        assertEquals("https://cdn.example.com/v1.mp4", result.getOrNull())
        assertEquals(5, api.chunkAttempts.get())
        assertEquals(100, progress.last())
        assertTrue(temp.existing.isEmpty(), "Temp file left behind: ${temp.existing}")
    }

    @Test
    fun `flaky chunk is skipped, real failures still come back as Result failure`() = runBlocking {
        val api = FakeUploadApi(failingChunks = setOf(2))
        val result = VideoUploader(api, FakeTempFiles()).compressAndUpload(Video("v2", frames = 5, chunks = 5)) {}

        assertTrue(result.isSuccess, "A single failed chunk should be skipped, not fail the upload")
        assertEquals(5, api.chunkAttempts.get())

        val brokenTemp = object : TempFileStore {
            override suspend fun create(name: String): String = throw IOException("disk full")
            override suspend fun delete(path: String) {}
        }
        val failed = VideoUploader(FakeUploadApi(), brokenTemp).compressAndUpload(Video("v3", 5, 5)) {}
        assertTrue(failed.exceptionOrNull() is IOException, "A real failure should be returned as Result.failure")
    }

    @Test
    fun `cancel during compression - stops promptly`() = runBlocking {
        val api = FakeUploadApi()
        val framesSeen = AtomicInteger()
        val job = launch(Dispatchers.Default) {
            VideoUploader(api, FakeTempFiles()).compressAndUpload(Video("v4", frames = 5_000, chunks = 5)) {
                framesSeen.incrementAndGet()
            }
        }
        delay(100)
        val start = System.nanoTime()
        job.cancel()
        val stopped = withTimeoutOrNull(1_000) { job.join() }
        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)

        assertNotNull(stopped, "Compression ignored the cancel and kept the CPU busy (frames done: ${framesSeen.get()} of 5000)")
        assertTrue(tookMs < 300, "Took ${tookMs}ms to react to cancel")
        assertEquals(0, api.chunkAttempts.get(), "Nothing should be uploaded after cancelling during compression")
    }

    @Test
    fun `cancel during upload - no more chunks are sent and finish is never called`() = runBlocking {
        val api = FakeUploadApi()
        val job = launch(Dispatchers.Default) {
            VideoUploader(api, FakeTempFiles()).compressAndUpload(Video("v5", frames = 10, chunks = 200)) {}
        }
        delay(300)
        job.cancel()
        job.join()
        val attemptsAtCancel = api.chunkAttempts.get()
        delay(200)

        assertTrue(attemptsAtCancel < 200, "All $attemptsAtCancel chunks were 'uploaded' even though we cancelled")
        assertEquals(attemptsAtCancel, api.chunkAttempts.get(), "Chunks kept uploading after cancel")
        assertFalse(api.finished.get(), "finish() was called after cancel")
    }

    @Test
    fun `cancelled upload does not return a result - no 'Upload failed' dialog`() = runBlocking {
        val dialogShown = AtomicBoolean(false)
        val job = launch(Dispatchers.Default) {
            val result = VideoUploader(FakeUploadApi(), FakeTempFiles())
                .compressAndUpload(Video("v6", frames = 10, chunks = 200)) {}
            if (result.isFailure) dialogShown.set(true)
        }
        delay(200)
        job.cancel()
        job.join()

        assertFalse(dialogShown.get(), "The screen showed 'Upload failed' for a cancelled upload")
    }

    @Test
    fun `temp file is deleted even when cancelled`() = runBlocking {
        for (cancelAfterMs in listOf(50L, 300L)) { // during compression, then during upload
            val temp = FakeTempFiles()
            val job = launch(Dispatchers.Default) {
                VideoUploader(FakeUploadApi(), temp).compressAndUpload(Video("v7", frames = 200, chunks = 200)) {}
            }
            delay(cancelAfterMs)
            job.cancel()
            job.join()

            assertTrue(temp.existing.isEmpty(), "Cancelled after ${cancelAfterMs}ms: temp file leaked: ${temp.existing}")
        }
    }
}
