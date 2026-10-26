@file:OptIn(ExperimentalCoroutinesApi::class)

package challenges.flow

import challenges.flow.f08_autosave.DraftApi
import challenges.flow.f08_autosave.DraftAutoSaver
import challenges.flow.f08_autosave.DraftAutoSaver.Companion.DEBOUNCE_MS
import challenges.flow.f08_autosave.SaveStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class F08DraftAutoSaverTest {

    private class FakeDraftApi(private val saveMs: Long = 500) : DraftApi {
        val saved = mutableListOf<String>()
        val started = mutableListOf<String>()
        var failNext = false
        private var inFlight = 0
        var peakInFlight = 0

        override suspend fun save(text: String) {
            started += text
            inFlight++
            peakInFlight = maxOf(peakInFlight, inFlight)
            try {
                delay(saveMs)
                if (failNext) {
                    failNext = false
                    throw IOException("no internet")
                }
                saved += text
            } finally {
                inFlight--
            }
        }
    }

    private class Screen(val saver: DraftAutoSaver, val statuses: MutableList<SaveStatus>, val scope: CoroutineScope)

    private fun TestScope.open(api: FakeDraftApi): Screen {
        val vmScope = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
        val saver = DraftAutoSaver(api, vmScope)
        val statuses = mutableListOf<SaveStatus>()
        backgroundScope.launch { saver.status.toList(statuses) }
        return Screen(saver, statuses, vmScope)
    }

    private fun TestScope.type(screen: Screen, vararg texts: String, gapMs: Long = 200) {
        for (t in texts) {
            screen.saver.onTextChanged(t)
            advanceTimeBy(gapMs)
        }
    }

    @Test
    fun `spec 1 and 4 - saves after the user pauses, status goes Saving then Saved`() = runTest {
        val api = FakeDraftApi()
        val screen = open(api)
        runCurrent()
        assertEquals(SaveStatus.Idle, screen.saver.status.value)

        type(screen, "G", "Go", "Goo", "Good")
        assertEquals(emptyList(), api.started, "Saved while the user was still typing")

        advanceUntilIdle()
        assertEquals(listOf("Good"), api.saved)
        assertEquals(SaveStatus.Saved("Good"), screen.saver.status.value)
        assertTrue(SaveStatus.Saving in screen.statuses, "Never showed 'Saving…'")
        screen.scope.cancel()
    }

    @Test
    fun `spec 1 - continuous typing never saves mid-sentence`() = runTest {
        val api = FakeDraftApi()
        val screen = open(api)

        repeat(20) { type(screen, "text-$it", gapMs = DEBOUNCE_MS - 100) }
        assertEquals(emptyList(), api.started)

        advanceUntilIdle()
        assertEquals(listOf("text-19"), api.saved)
        screen.scope.cancel()
    }

    @Test
    fun `spec 2 - text equal to the last saved text is not saved again`() = runTest {
        val api = FakeDraftApi()
        val screen = open(api)

        type(screen, "Great food", gapMs = 2_000)
        type(screen, "Great food!", "Great food", gapMs = 200) // typed then deleted the "!"
        advanceUntilIdle()

        assertEquals(listOf("Great food"), api.started)
        screen.scope.cancel()
    }

    @Test
    fun `spec 3 - one save at a time, never cancelled, latest text ends up saved`() = runTest {
        val api = FakeDraftApi(saveMs = 3_000)
        val screen = open(api)

        type(screen, "v1", gapMs = 1_500) // save of v1 starts at 1000 and runs until 4000
        type(screen, "v2", gapMs = 1_500) // debounced while v1 is still saving
        type(screen, "v3", gapMs = 100)
        advanceUntilIdle()

        assertEquals(1, api.peakInFlight, "Two saves ran at the same time")
        assertEquals(api.started, api.saved, "A save was cancelled halfway: started=${api.started} saved=${api.saved}")
        assertEquals("v3", api.saved.last(), "The latest text was never saved")
        assertEquals(SaveStatus.Saved("v3"), screen.saver.status.value)
        screen.scope.cancel()
    }

    @Test
    fun `spec 4 and 5 - failure shows Failed, the next change tries again`() = runTest {
        val api = FakeDraftApi().also { it.failNext = true }
        val screen = open(api)

        type(screen, "Loved it", gapMs = 2_000)
        assertEquals(SaveStatus.Failed("Loved it", "no internet"), screen.saver.status.value)
        advanceTimeBy(30_000)
        assertEquals(listOf("Loved it"), api.started, "Retried automatically. The spec says: wait for the next change")

        type(screen, "Loved it.", gapMs = 2_000)
        assertEquals(SaveStatus.Saved("Loved it."), screen.saver.status.value)
        screen.scope.cancel()
    }

    @Test
    fun `spec 6 - flush saves immediately and returns once saved`() = runTest {
        val api = FakeDraftApi()
        val screen = open(api)
        type(screen, "Bye", gapMs = 100) // user closes the screen 100ms after typing

        val start = currentTime
        screen.saver.flush()

        assertEquals(listOf("Bye"), api.saved, "flush() returned before the text was saved")
        assertEquals(500, currentTime - start, "flush() should save right away (500ms save), not wait for the debounce")
        advanceUntilIdle()
        assertEquals(listOf("Bye"), api.started, "The debounce saved the same text again after flush()")
        screen.scope.cancel()
    }

    @Test
    fun `spec 6 - flush with nothing unsaved returns immediately`() = runTest {
        val api = FakeDraftApi()
        val screen = open(api)
        type(screen, "Done", gapMs = 2_000)

        val start = currentTime
        screen.saver.flush()

        assertEquals(0, currentTime - start)
        assertEquals(listOf("Done"), api.started)
        screen.scope.cancel()
    }

    @Test
    fun `spec 6 - flush while the same text is already saving waits instead of saving twice`() = runTest {
        val api = FakeDraftApi(saveMs = 2_000)
        val screen = open(api)
        type(screen, "Tasty", gapMs = 1_500) // save started at 1000, ends at 3000

        val flush = async { screen.saver.flush() }
        advanceUntilIdle()
        flush.await()

        assertEquals(listOf("Tasty"), api.started)
        assertEquals(3_000, currentTime, "flush() should finish when the running save finishes")
        screen.scope.cancel()
    }

    @Test
    fun `spec 7 - screen closed during a save is not a failure`() = runTest {
        val api = FakeDraftApi(saveMs = 2_000)
        val screen = open(api)
        type(screen, "Half", gapMs = 1_500)

        screen.scope.cancel()
        advanceUntilIdle()

        assertTrue(screen.statuses.none { it is SaveStatus.Failed }, "Cancellation was shown as a failure: ${screen.statuses}")
    }
}
