package challenges.boss.b03_tracking

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

data class Location(val lat: Double, val lng: Double)

enum class OrderStatus { PREPARING, ON_THE_WAY, DELIVERED }

data class RiderUpdate(val status: OrderStatus, val rider: Location)

interface RiderListener {
    fun onUpdate(update: RiderUpdate)
    fun onError(error: Throwable)
}

fun interface Connection {
    fun disconnect()
}

/** Live rider socket (third-party). Callbacks run on the socket's reader thread and must never block. */
interface RiderSocket {
    fun connect(orderId: Int, listener: RiderListener): Connection
}

/** REST fallback for when the socket is down. */
interface TrackingApi {
    suspend fun latest(orderId: Int): RiderUpdate
}

/** Road-network ETA. Blocking and CPU-heavy (~50ms). */
fun interface EtaCalculator {
    fun etaMinutes(rider: Location, customer: Location): Int
}

data class TrackingUiState(
    val status: OrderStatus?,
    val rider: Location?,
    val etaMinutes: Int?,
    val usingFallback: Boolean,
)

sealed interface TrackingEvent {
    /** "Your rider is almost there!" notification. */
    data object RiderNearby : TrackingEvent
    data object OpenRatingScreen : TrackingEvent
}

/**
 * Live order tracking screen.
 *
 * @param cpu dispatcher for CPU-heavy work
 * @param scope the ViewModel's scope (viewModelScope, which runs on the main thread in the app)
 */
class TrackingViewModel(
    private val orderId: Int,
    private val customer: Location,
    private val socket: RiderSocket,
    private val api: TrackingApi,
    private val eta: EtaCalculator,
    private val cpu: CoroutineDispatcher,
    private val scope: CoroutineScope,
) {

    private fun riderUpdates(): Flow<RiderUpdate> = callbackFlow {
        socket.connect(orderId, object : RiderListener {
            override fun onUpdate(update: RiderUpdate) {
                runBlocking { send(update) }
            }

            override fun onError(error: Throwable) {
                close(error)
            }
        })
        awaitClose { }
    }.catch {
        emit(api.latest(orderId)) // socket is down: fall back to REST
    }

    /** Collected by the map, the ETA banner and the status stepper. */
    val uiState: Flow<TrackingUiState> = riderUpdates().map { update ->
        TrackingUiState(
            status = update.status,
            rider = update.rider,
            etaMinutes = eta.etaMinutes(update.rider, customer),
            usingFallback = false,
        )
    }

    private val _events = MutableStateFlow<TrackingEvent?>(null)

    /** One-shot events. Collected while the screen is STARTED. */
    val events: Flow<TrackingEvent> = _events.filterNotNull()

    init {
        scope.launch {
            uiState.collect { state ->
                if (state.etaMinutes != null && state.etaMinutes <= NEARBY_MINUTES) {
                    _events.value = TrackingEvent.RiderNearby
                }
                if (state.status == OrderStatus.DELIVERED) {
                    _events.value = TrackingEvent.OpenRatingScreen
                }
            }
        }
    }

    companion object {
        const val NEARBY_MINUTES = 2
        const val POLL_MS = 10_000L
    }
}
