package challenges.flow.f09_predict

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * 🔍 Interview round: "What does this flow print?"
 *
 * Each puzzle runs inside `runTest { }`: a single thread, with virtual time (delays are instant but keep their order).
 * Predict the exact log lines, in order, and write them in FlowPredictions.kt.
 *
 * DON'T run these first. Reason it out on paper.
 */
@OptIn(ExperimentalCoroutinesApi::class)
object FlowPuzzles {

    suspend fun CoroutineScope.puzzle1(log: (String) -> Unit) {
        val numbers = flow {
            log("start")
            emit(1)
            emit(2)
        }
        numbers.collect { log("got $it") }
        numbers.collect { log("again $it") }
    }

    suspend fun CoroutineScope.puzzle2(log: (String) -> Unit) {
        flowOf(1, 2)
            .onEach { log("onEach $it") }
            .map { log("map $it"); it * 10 }
            .collect { log("collect $it") }
    }

    suspend fun CoroutineScope.puzzle3(log: (String) -> Unit) {
        flow {
            repeat(3) {
                log("emit $it")
                emit(it)
            }
        }
            .buffer()
            .collect { log("collect $it") }
    }

    suspend fun CoroutineScope.puzzle4(log: (String) -> Unit) {
        val state = MutableStateFlow(0)
        val job = launch { state.collect { log("got $it") } }
        state.value = 1
        state.value = 2
        state.value = 2
        delay(10)
        state.value = 3
        delay(10)
        job.cancel()
    }

    suspend fun CoroutineScope.puzzle5(log: (String) -> Unit) {
        val events = MutableSharedFlow<Int>()
        events.emit(1)
        val job = launch { events.collect { log("got $it") } }
        delay(10)
        events.emit(2)
        delay(10)
        job.cancel()
    }

    suspend fun CoroutineScope.puzzle6(log: (String) -> Unit) {
        flowOf("a", "b")
            .flatMapLatest { v ->
                flow {
                    emit("$v-1")
                    delay(10)
                    emit("$v-2")
                }
            }
            .collect { log(it) }
    }

    suspend fun CoroutineScope.puzzle7(log: (String) -> Unit) {
        try {
            flow {
                emit(1)
                emit(2)
            }
                .catch { log("caught ${it.message}") }
                .collect {
                    if (it == 2) throw IllegalStateException("collector failed")
                    log("got $it")
                }
        } catch (e: IllegalStateException) {
            log("outer ${e.message}")
        }
    }

    suspend fun CoroutineScope.puzzle8(log: (String) -> Unit) {
        val numbers = flow {
            emit(1)
            delay(100)
            emit(2)
        }
        val letters = flow {
            delay(50)
            emit("a")
            delay(100)
            emit("b")
        }
        numbers.combine(letters) { n, l -> "$n$l" }.collect { log(it) }
    }
}
