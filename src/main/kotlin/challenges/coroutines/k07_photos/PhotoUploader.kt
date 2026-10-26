package challenges.coroutines.k07_photos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class Photo(val id: Int)

interface PhotoApi {
    /** Uploads one photo and returns its CDN url. Main-safe, suspends while uploading. */
    suspend fun upload(photo: Photo): String
}

/**
 * A customer review can have up to 200 photos. The CDN rate-limits us:
 * more than [MAX_PARALLEL_UPLOADS] uploads at once from one device returns HTTP 429.
 */
class PhotoUploader(private val api: PhotoApi) {

    /** Returns the CDN urls in the same order as [photos]. */
    suspend fun uploadAll(photos: List<Photo>, onProgress: (done: Int, total: Int) -> Unit): List<String> =
        coroutineScope {
            var done = 0
            val limited = Dispatchers.IO.limitedParallelism(MAX_PARALLEL_UPLOADS)
            photos.map { photo ->
                async(limited) {
                    val url = api.upload(photo)
                    done++
                    onProgress(done, photos.size)
                    url
                }
            }.awaitAll()
        }

    companion object {
        const val MAX_PARALLEL_UPLOADS = 4
    }
}
