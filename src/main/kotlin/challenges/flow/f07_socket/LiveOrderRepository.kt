package challenges.flow.f07_socket

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.runBlocking

data class OrderUpdate(val orderId: Int, val status: String, val seq: Int)

interface OrderSocketListener {
    fun onUpdate(update: OrderUpdate)
    /** The server closed the stream normally (e.g. the order was delivered). */
    fun onClosed()
    fun onFailure(error: Throwable)
}

fun interface Subscription {
    fun unsubscribe()
}

/**
 * WebSocket client (third-party). Listener callbacks run on the socket's single reader thread.
 * **If a callback blocks, the reader stops reading for every subscription in the app.**
 */
interface OrderSocket {
    fun subscribe(orderId: Int, listener: OrderSocketListener): Subscription
}

class LiveOrderRepository(private val socket: OrderSocket) {

    /** Live status updates for one order. The UI only cares about the latest status. */
    fun updates(orderId: Int): Flow<OrderUpdate> = callbackFlow {
        socket.subscribe(orderId, object : OrderSocketListener {
            override fun onUpdate(update: OrderUpdate) {
                runBlocking { send(update) }
            }

            override fun onClosed() {
            }

            override fun onFailure(error: Throwable) {
                println("socket failure: $error")
            }
        })
        awaitClose { }
    }
}
