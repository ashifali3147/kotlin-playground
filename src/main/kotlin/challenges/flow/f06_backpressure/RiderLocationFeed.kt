package challenges.flow.f06_backpressure

import kotlinx.coroutines.flow.Flow

data class LatLng(val lat: Double, val lng: Double)

/**
 * Live rider position on the order tracking screen.
 *
 * [gps] emits a fix every 100ms. It is a cold flow over the rider's GPS stream. If a collector is slow,
 * `emit` suspends and **the GPS reader falls behind** (stale timestamps, dropped fixes in the SDK).
 */
class RiderLocationFeed(private val gps: Flow<LatLng>) {

    /**
     * Moves the rider marker on the map. [render] animates the marker and takes ~300ms.
     */
    suspend fun showOnMap(render: suspend (LatLng) -> Unit) {
        gps.collect { render(it) }
    }

    /**
     * Records the full route for delivery-time analytics. [record] writes to a local DB and takes ~150ms.
     */
    suspend fun recordRoute(record: suspend (LatLng) -> Unit) {
        gps.collect { record(it) }
    }
}
