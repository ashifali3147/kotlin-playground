package challenges.boss.b01_imageloader

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async

class Bitmap(val url: String, val sizeBytes: Int)

interface Network {
    /** Downloads the image bytes. Suspends; may throw IOException. */
    suspend fun download(url: String): ByteArray
}

/** Blocking disk cache (legacy Java library). */
interface DiskCache {
    fun read(url: String): ByteArray?
    fun write(url: String, data: ByteArray)
}

/** Blocking, CPU-heavy image decoder. */
interface Decoder {
    fun decode(url: String, data: ByteArray): Bitmap
}

/** In-memory LRU cache of decoded bitmaps. */
class MemoryCache(private val maxEntries: Int) {

    private val lru = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>) = size > maxEntries
    }

    fun get(url: String): Bitmap? = lru[url]

    fun put(url: String, bitmap: Bitmap) {
        lru[url] = bitmap
    }

    fun size(): Int = lru.size
}

/**
 * The app's image loader. Every list row calls [load] from its own coroutine (many threads at once),
 * and that coroutine is cancelled when the row scrolls off screen and is recycled.
 *
 * @param io dispatcher for blocking disk work
 * @param cpu dispatcher for decoding
 * @param appScope application-wide scope
 */
class ImageLoader(
    private val network: Network,
    private val disk: DiskCache,
    private val decoder: Decoder,
    private val memory: MemoryCache,
    private val io: CoroutineDispatcher,
    private val cpu: CoroutineDispatcher,
    private val appScope: CoroutineScope,
) {

    private val inFlight = HashMap<String, Deferred<Bitmap>>()

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun load(url: String): Bitmap {
        memory.get(url)?.let { return it }
        inFlight[url]?.let { return it.await() }

        val request = GlobalScope.async {
            val data = disk.read(url) ?: network.download(url).also { disk.write(url, it) }
            decoder.decode(url, data)
        }
        inFlight[url] = request
        val bitmap = request.await()
        memory.put(url, bitmap)
        inFlight.remove(url)
        return bitmap
    }

    companion object {
        const val MAX_PARALLEL_DOWNLOADS = 4
    }
}
