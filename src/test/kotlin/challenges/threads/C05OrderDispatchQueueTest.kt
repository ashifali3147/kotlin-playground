package challenges.threads

import challenges.runConcurrently
import challenges.threads.c05_dispatchqueue.Order
import challenges.threads.c05_dispatchqueue.OrderDispatchQueue
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Timeout(60, unit = TimeUnit.SECONDS)
class C05OrderDispatchQueueTest {

    @Test
    fun `single thread - FIFO`() {
        val queue = OrderDispatchQueue(capacity = 3)
        queue.put(Order(1)); queue.put(Order(2)); queue.put(Order(3))

        assertEquals(listOf(1, 2, 3), List(3) { queue.take().id })
    }

    @Test
    fun `staging setup - 1 producer, 1 consumer, order preserved`() {
        val queue = OrderDispatchQueue(capacity = 1)
        val received = ArrayList<Int>()

        runConcurrently(threadCount = 2, timeoutMs = 10_000) { i ->
            if (i == 0) repeat(20_000) { queue.put(Order(it)) }
            else repeat(20_000) { received += queue.take().id }
        }

        assertEquals(List(20_000) { it }, received)
    }

    @RepeatedTest(5)
    fun `friday night - 4 producers, 4 consumers, tiny capacity`() {
        val capacity = 2
        val queue = OrderDispatchQueue(capacity)
        val perProducer = 10_000
        val taken = ConcurrentHashMap.newKeySet<Int>()
        val duplicates = ConcurrentHashMap.newKeySet<Int>()

        runConcurrently(threadCount = 8, timeoutMs = 10_000, namePrefix = "dispatch") { i ->
            if (i < 4) {
                repeat(perProducer) { n -> queue.put(Order(i * perProducer + n)) }
            } else {
                repeat(perProducer) {
                    val id = queue.take().id
                    if (!taken.add(id)) duplicates += id
                }
            }
        }

        assertTrue(duplicates.isEmpty(), "Some orders were taken twice: ${duplicates.take(5)}")
        assertEquals(4 * perProducer, taken.size, "Some orders were never taken")
        assertTrue(queue.peakSize() <= capacity, "Queue grew to ${queue.peakSize()}, capacity is $capacity")
    }

    @Test
    fun `waiting consumer sleeps without burning CPU, and wakes up for a new order`() {
        val queue = OrderDispatchQueue(capacity = 4)
        var received: Order? = null
        val consumer = thread(isDaemon = true, name = "idle-worker") { received = queue.take() }

        Thread.sleep(300)
        assertEquals(
            Thread.State.WAITING, consumer.state,
            "A consumer on an empty queue should be parked (WAITING), not spinning or sleeping in a loop"
        )

        queue.put(Order(42))
        consumer.join(2_000)
        assertEquals(42, received?.id, "The waiting consumer never received the order")
    }
}
