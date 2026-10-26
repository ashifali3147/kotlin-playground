package challenges.coroutines.k04_tracking

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class OrderStatus { PLACED, PREPARING, PICKED_UP, DELIVERED }

interface TrackingApi {
    /** Network call. May throw IOException. */
    suspend fun fetchStatus(orderId: Int): OrderStatus
}

/**
 * Polls the live status of an order while the tracking screen is visible.
 *
 * Lifecycle (called on the main thread):
 * - onStart   -> [startTracking]
 * - onStop    -> [stopTracking]
 * - onCleared -> [destroy]
 *
 * @param dispatcher where polling runs (injected so tests can control time)
 */
class OrderTracker(
    private val api: TrackingApi,
    private val dispatcher: CoroutineDispatcher,
    private val onUpdate: (OrderStatus) -> Unit,
) {

    @OptIn(DelicateCoroutinesApi::class)
    fun startTracking(orderId: Int) {
        GlobalScope.launch(dispatcher) {
            while (true) {
                val status = api.fetchStatus(orderId)
                onUpdate(status)
                if (status == OrderStatus.DELIVERED) break
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stopTracking() {
        // TODO: how do we stop it?
    }

    fun destroy() {
    }

    companion object {
        const val POLL_INTERVAL_MS = 5_000L
    }
}
