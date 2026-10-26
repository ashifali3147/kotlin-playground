package challenges.threads.c06_listeners

enum class NetworkState { ONLINE, OFFLINE, METERED }

fun interface NetworkListener {
    fun onNetworkChanged(state: NetworkState)
}

/**
 * App-wide connectivity monitor. Screens, the sync engine and the image loader register listeners.
 *
 * Threads involved:
 * - the OS delivers network changes on a binder thread, which calls [dispatch]
 * - screens add/remove listeners on the main thread
 * - the sync engine adds/removes from its own background thread
 */
class ConnectivityMonitor {

    private val listeners = mutableListOf<NetworkListener>()

    fun addListener(listener: NetworkListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: NetworkListener) {
        listeners.remove(listener)
    }

    fun dispatch(state: NetworkState) {
        for (listener in listeners) {
            listener.onNetworkChanged(state)
        }
    }

    fun listenerCount(): Int = listeners.size
}
