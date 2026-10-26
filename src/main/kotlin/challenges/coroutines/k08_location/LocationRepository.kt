package challenges.coroutines.k08_location

import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class Location(val lat: Double, val lng: Double)

interface LocationCallback {
    fun onLocation(location: Location)
    fun onError(error: Exception)
}

fun interface CancellationHandle {
    fun cancel()
}

/**
 * Third-party location SDK (we can't change it). Callbacks arrive on the SDK's own thread.
 *
 * Documented: exactly one of the callbacks is called per request.
 * Reality (known SDK bug on some Xiaomi/Samsung devices): `onLocation` can fire twice,
 * and can even fire after the request was cancelled.
 */
interface LocationClient {
    /** Turns on GPS until a fix arrives or [CancellationHandle.cancel] is called. */
    fun requestSingleUpdate(callback: LocationCallback): CancellationHandle
}

class LocationRepository(private val client: LocationClient) {

    /** Suspends until the SDK delivers a location. */
    suspend fun currentLocation(): Location = suspendCoroutine { continuation ->
        client.requestSingleUpdate(object : LocationCallback {
            override fun onLocation(location: Location) = continuation.resume(location)
            override fun onError(error: Exception) = continuation.resumeWithException(error)
        })
    }

    /**
     * For "restaurants near me": wait at most [timeoutMs] for a fix, else return null and use the
     * last known city instead. SDK errors must still be thrown to the caller.
     */
    suspend fun currentLocationOrNull(timeoutMs: Long): Location? =
        try {
            withTimeout(timeoutMs) { currentLocation() }
        } catch (e: Exception) {
            null
        }
}
