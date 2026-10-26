package challenges

import java.lang.management.ManagementFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.fail

/**
 * Test harness only — not part of any challenge. You don't need to touch this file.
 *
 * Starts [threadCount] threads that all wait on a gate, releases them at the same instant
 * (to maximise contention), and waits up to [timeoutMs] for them to finish.
 * If they don't finish, it prints a thread dump of the stuck workers and fails —
 * the same kind of dump you'd read in an ANR trace.
 */
fun runConcurrently(
    threadCount: Int,
    timeoutMs: Long = 5_000,
    namePrefix: String = "worker",
    block: (index: Int) -> Unit,
) {
    val gate = CountDownLatch(1)
    val errors = ConcurrentLinkedQueue<Throwable>()
    val workers = (0 until threadCount).map { i ->
        thread(name = "$namePrefix-$i", isDaemon = true) {
            gate.await()
            try {
                block(i)
            } catch (t: Throwable) {
                errors += t
            }
        }
    }
    gate.countDown()

    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
    workers.forEach { it.join(maxOf(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()))) }

    val stuck = workers.filter { it.isAlive }
    if (stuck.isNotEmpty()) {
        fail(
            "${stuck.size}/$threadCount threads did not finish within ${timeoutMs}ms. " +
                "The app would be frozen here.\n\n--- Thread dump of stuck threads ---\n" +
                threadDump(stuck)
        )
    }
    errors.firstOrNull()?.let { throw AssertionError("A worker thread crashed: $it", it) }
}

fun threadDump(threads: List<Thread>): String {
    val mx = ManagementFactory.getThreadMXBean()
    return mx.getThreadInfo(threads.map { it.id }.toLongArray(), true, true)
        .filterNotNull()
        .joinToString("\n") { it.toString() }
}

fun findThread(name: String): Thread? = Thread.getAllStackTraces().keys.firstOrNull { it.name == name }
