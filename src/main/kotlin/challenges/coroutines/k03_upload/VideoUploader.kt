package challenges.coroutines.k03_upload

class Video(val id: String, val frames: Int, val chunks: Int)

interface UploadApi {
    suspend fun uploadChunk(videoId: String, chunk: Int)
    suspend fun finish(videoId: String): String
}

interface TempFileStore {
    suspend fun create(name: String): String
    suspend fun delete(path: String)
}

/**
 * Restaurant partners upload a short video of their kitchen. The screen shows a progress bar
 * and a "Cancel" button. Cancel = the screen's coroutine is cancelled.
 */
class VideoUploader(private val api: UploadApi, private val tempFiles: TempFileStore) {

    suspend fun compressAndUpload(video: Video, onProgress: (percent: Int) -> Unit): Result<String> = runCatching {
        val temp = tempFiles.create(video.id)
        try {
            for (frame in 0 until video.frames) {
                compressFrame(frame)
                onProgress(frame * 50 / video.frames)
            }
            for (chunk in 0 until video.chunks) {
                try {
                    api.uploadChunk(video.id, chunk)
                } catch (e: Exception) {
                    // Flaky mobile network: skip this chunk, the server asks for missing chunks in finish().
                }
                onProgress(50 + (chunk + 1) * 50 / video.chunks)
            }
            api.finish(video.id)
        } finally {
            tempFiles.delete(temp)
        }
    }

    /** Pure CPU work: ~1ms per frame. */
    private fun compressFrame(frame: Int) {
        val end = System.nanoTime() + 1_000_000
        var x = frame.toLong()
        while (System.nanoTime() < end) x = x * 6364136223846793005L + 1
    }
}
