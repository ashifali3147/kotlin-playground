package challenges.boss.b02_outbox

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class ActionType { PLACE_ORDER, ADD_NOTE, CANCEL_ORDER }

data class PendingAction(val id: String, val orderId: Int, val type: ActionType)

interface OrdersApi {
    /**
     * Sends one action to the server. Suspends until the server confirms.
     * **Not idempotent**: if the same action is received twice, the server creates a duplicate.
     * May throw IOException.
     */
    suspend fun send(action: PendingAction)
}

/**
 * Offline-first "outbox": the user can place, annotate and cancel orders with no network.
 * Actions are stored locally and synced to the server when the device is online.
 *
 * @param online connectivity: emits the current state first, then every change (true = online)
 * @param scope application-wide scope
 */
class OutboxSyncEngine(
    private val api: OrdersApi,
    private val online: Flow<Boolean>,
    private val scope: CoroutineScope,
) {

    private val outbox = mutableListOf<PendingAction>()
    private val _pendingCount = MutableStateFlow(0)

    /** Shown as a badge: "3 changes waiting to sync". */
    val pendingCount: StateFlow<Int> = _pendingCount

    fun start() {
        scope.launch {
            online.collect { isOnline ->
                if (isOnline) launch { syncAll() }
            }
        }
    }

    /** Called from any thread: UI, WorkManager, push-notification handler. */
    fun enqueue(action: PendingAction) {
        outbox.add(action)
        _pendingCount.value = _pendingCount.value + 1
    }

    private suspend fun syncAll() {
        val batch = outbox.toList()
        coroutineScope {
            batch.forEach { action ->
                launch {
                    try {
                        api.send(action)
                        outbox.remove(action)
                        _pendingCount.value = _pendingCount.value - 1
                    } catch (e: Exception) {
                        // Will retry the next time we come online.
                    }
                }
            }
        }
    }

    companion object {
        const val RETRY_INITIAL_MS = 1_000L
        const val RETRY_MAX_MS = 30_000L
    }
}
