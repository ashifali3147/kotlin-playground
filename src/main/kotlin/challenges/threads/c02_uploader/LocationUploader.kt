package challenges.threads.c02_uploader

import kotlin.concurrent.thread

/**
 * Streams the rider's GPS fixes to the backend while a delivery is in progress.
 *
 * The delivery screen calls [start] in onStart() and [stop] in onStop().
 * GPS and network are simulated to keep the challenge self-contained: each loop iteration
 * is "read one fix into the batch", and every [BATCH_SIZE] fixes the batch is uploaded.
 */
class LocationUploader {

    private var isRunning = false
    private var uploadedFixes = 0L

    fun start() {
        isRunning = true
        thread(name = WORKER_NAME, isDaemon = true) {
            var batch = 0
            while (isRunning) {
                batch++
                if (batch == BATCH_SIZE) {
                    uploadedFixes += batch
                    batch = 0
                }
            }
            // Don't lose the last partial batch.
            uploadFinalBatch(batch)
        }
    }

    private fun uploadFinalBatch(size: Int) {
        Thread.sleep(20) // network round trip
        uploadedFixes += size
    }

    /**
     * Stops uploading and returns how many fixes were uploaded in total.
     * Contract: once this returns, no more fixes are uploaded.
     */
    fun stop(): Long {
        isRunning = false
        return uploadedFixes
    }

    fun uploadedFixes(): Long = uploadedFixes

    companion object {
        const val WORKER_NAME = "location-uploader"
        private const val BATCH_SIZE = 1_000
    }
}
