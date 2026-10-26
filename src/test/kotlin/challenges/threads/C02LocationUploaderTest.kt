package challenges.threads

import challenges.findThread
import challenges.threadDump
import challenges.threads.c02_uploader.LocationUploader
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

@Timeout(30, unit = TimeUnit.SECONDS)
class C02LocationUploaderTest {

    private var uploader: LocationUploader? = null

    @AfterEach
    fun cleanup() {
        uploader?.stop()
    }

    @RepeatedTest(3)
    fun `stop() really stops the background thread`() {
        val uploader = LocationUploader().also { this.uploader = it }
        uploader.start()
        Thread.sleep(500) // let it run long enough for the JIT to compile the loop, like in production

        uploader.stop()

        val worker = findThread(LocationUploader.WORKER_NAME)
        worker?.join(2_000)
        if (worker != null && worker.isAlive) {
            fail(
                "stop() was called 2s ago but the worker is still running — battery drain!\n\n" +
                    threadDump(listOf(worker))
            )
        }
    }

    @RepeatedTest(3)
    fun `after stop() returns, the count is final`() {
        val uploader = LocationUploader().also { this.uploader = it }
        uploader.start()
        Thread.sleep(500)

        val reported = uploader.stop()

        assertTrue(reported > 0, "Worker never uploaded anything?")
        Thread.sleep(200)
        assertEquals(
            reported, uploader.uploadedFixes(),
            "stop() returned, but uploads continued afterwards (or stop() reported a stale number)"
        )
    }
}
