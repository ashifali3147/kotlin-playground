package challenges.threads.c01_analytics

/**
 * Collects analytics events from all over the app — OkHttp interceptors, WorkManager workers,
 * the main thread, background executors. Any thread can call [track] at any time.
 *
 * Every few minutes an upload job calls [drain], sends the batch to the backend,
 * and starts counting the next batch from zero.
 */
class AnalyticsTracker {

    private var lifetimeEvents = 0
    private val pending = HashMap<String, Int>()

    fun track(eventName: String) {
        lifetimeEvents++
        val current = pending[eventName] ?: 0
        pending[eventName] = current + 1
    }

    /** Every event ever tracked, including ones already drained. Never resets. */
    fun lifetimeEvents(): Int = lifetimeEvents

    /** How many [eventName] events are waiting for the next upload. */
    fun pendingCountOf(eventName: String): Int = pending[eventName] ?: 0

    /** Read-only copy of the pending batch, for the debug screen. */
    fun snapshot(): Map<String, Int> = pending.toMap()

    /** Hands the pending batch to the uploader and starts a fresh batch. */
    fun drain(): Map<String, Int> {
        val batch = pending.toMap()
        pending.clear()
        return batch
    }
}
