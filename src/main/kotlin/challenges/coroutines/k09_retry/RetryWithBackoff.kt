package challenges.coroutines.k09_retry

import java.io.IOException

/**
 * Runs [block] and retries it with exponential backoff when it fails with a retryable error.
 * Read the spec in README.md. The tests check every line of it.
 *
 * @param maxAttempts total attempts, including the first one (>= 1)
 * @param initialDelayMs wait before the 2nd attempt
 * @param maxDelayMs upper bound for any single wait
 * @param factor each wait is the previous one times this
 * @param shouldRetry decides whether a failure is worth retrying
 * @param block the work; receives the attempt number, starting at 1
 */
suspend fun <T> retryWithBackoff(
    maxAttempts: Int,
    initialDelayMs: Long,
    maxDelayMs: Long,
    factor: Double = 2.0,
    shouldRetry: (Throwable) -> Boolean = { it is IOException },
    block: suspend (attempt: Int) -> T,
): T {
    TODO("K09: implement")
}
