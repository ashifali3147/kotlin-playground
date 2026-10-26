package challenges.coroutines.k05_prefetch

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Collections

interface ImageDownloader {
    /** Downloads and caches one image. May throw (404, timeout, corrupt file...). */
    suspend fun download(url: String)
}

data class PrefetchReport(val succeeded: List<String>, val failed: List<String>)

/**
 * Before the restaurant page opens, prefetch all the menu photos so they show instantly.
 * A broken photo is normal (restaurants upload weird files) and must never break the others.
 */
class MenuImagePrefetcher(private val downloader: ImageDownloader) {

    private val logErrors = CoroutineExceptionHandler { _, e -> println("prefetch failed: $e") }

    suspend fun prefetch(urls: List<String>): PrefetchReport = coroutineScope {
        val succeeded = Collections.synchronizedList(mutableListOf<String>())
        val failed = Collections.synchronizedList(mutableListOf<String>())

        urls.forEach { url ->
            launch(logErrors) {
                downloader.download(url)
                succeeded += url
            }
        }

        PrefetchReport(succeeded.toList(), failed.toList())
    }
}
