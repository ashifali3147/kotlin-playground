package challenges.threads.c08_connectionpool

/** A connection to the order database. Opening one is expensive (TLS handshake, auth). */
class Connection(val id: Int) {
    @Volatile
    var isClosed = false
        private set

    fun close() {
        isClosed = true
    }
}

class PoolExhaustedException(message: String) : Exception(message)

/**
 * A bounded pool of [Connection]s. Read the spec in README.md. The tests check every line of it.
 *
 * @param maxConnections the most connections that may exist (and be in use) at once
 * @param factory opens a new connection. Slow, and it may throw.
 */
class ConnectionPool(
    private val maxConnections: Int,
    private val factory: () -> Connection,
) : AutoCloseable {

    /**
     * Returns an idle connection, or opens a new one if allowed.
     * Waits up to [timeoutMs] for one to become free, then throws [PoolExhaustedException].
     */
    fun acquire(timeoutMs: Long): Connection {
        TODO("T08: implement")
    }

    /** Gives a connection back to the pool. */
    fun release(connection: Connection) {
        TODO("T08: implement")
    }

    /** Acquires a connection, runs [block] with it, and always gives it back. */
    fun <T> withConnection(timeoutMs: Long, block: (Connection) -> T): T {
        TODO("T08: implement")
    }

    override fun close() {
        TODO("T08: implement")
    }
}
