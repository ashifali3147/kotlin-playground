package challenges.threads.c05_dispatchqueue

class Order(val id: Int)

/**
 * Hands incoming orders from the socket reader threads to the rider-assignment workers.
 *
 * Bounded on purpose: during a dinner rush, if workers fall behind, producers must
 * wait instead of filling up memory. [take] waits while the queue is empty.
 */
@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
class OrderDispatchQueue(private val capacity: Int) {

    private val orders = ArrayDeque<Order>()
    private val lock = Object()
    private var peakSize = 0

    fun put(order: Order) {
        synchronized(lock) {
            if (orders.size == capacity) lock.wait()
            orders.addLast(order)
            peakSize = maxOf(peakSize, orders.size)
            lock.notify()
        }
    }

    fun take(): Order {
        synchronized(lock) {
            if (orders.isEmpty()) lock.wait()
            val order = orders.removeFirst()
            lock.notify()
            return order
        }
    }

    /** Largest number of orders that were ever in the queue at once. */
    fun peakSize(): Int = synchronized(lock) { peakSize }
}
