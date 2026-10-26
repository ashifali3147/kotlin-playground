package challenges.coroutines.k10_predict

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.yield

/**
 * 🔍 Interview round: "What does this print?"
 *
 * Each puzzle runs inside `runBlocking { }` (a single thread) and calls `log(...)` instead of println.
 * Predict the exact log lines, in order, and write them in Predictions.kt.
 *
 * DON'T run these first. Reason it out on paper, like in a real interview.
 */
object Puzzles {

    suspend fun CoroutineScope.puzzle1(log: (String) -> Unit) {
        log("A")
        launch { log("B") }
        log("C")
    }

    suspend fun CoroutineScope.puzzle2(log: (String) -> Unit) {
        launch {
            delay(100)
            log("1")
        }
        coroutineScope {
            launch {
                delay(50)
                log("2")
            }
            log("3")
        }
        log("4")
    }

    suspend fun CoroutineScope.puzzle3(log: (String) -> Unit) {
        val answer = async {
            log("computing")
            42
        }
        log("before await")
        log("got ${answer.await()}")
    }

    suspend fun CoroutineScope.puzzle4(log: (String) -> Unit) {
        launch { log("A1"); yield(); log("A2") }
        launch { log("B1"); yield(); log("B2") }
        log("main")
    }

    suspend fun CoroutineScope.puzzle5(log: (String) -> Unit) {
        try {
            coroutineScope {
                launch {
                    try {
                        delay(1_000)
                        log("sibling done")
                    } finally {
                        log("sibling finally")
                    }
                }
                launch {
                    delay(10)
                    throw IllegalStateException("boom")
                }
            }
        } catch (e: IllegalStateException) {
            log("caught ${e.message}")
        }
        log("end")
    }

    suspend fun CoroutineScope.puzzle6(log: (String) -> Unit) {
        val job = launch {
            try {
                repeat(5) {
                    log("tick $it")
                    delay(100)
                }
            } finally {
                log("finally")
                delay(10)
                log("after delay in finally")
            }
        }
        delay(250)
        job.cancelAndJoin()
        log("done")
    }

    suspend fun CoroutineScope.puzzle7(log: (String) -> Unit) {
        supervisorScope {
            async { throw RuntimeException("nobody awaits me") }
            launch {
                delay(10)
                log("other child ok")
            }
        }
        log("end")
    }

    suspend fun CoroutineScope.puzzle8(log: (String) -> Unit) {
        var count = 0
        coroutineScope {
            repeat(1_000) { launch { count++ } }
        }
        log("count=$count")
    }
}
