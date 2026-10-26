package challenges.flow.f08_autosave

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

sealed interface SaveStatus {
    data object Idle : SaveStatus
    data object Saving : SaveStatus
    data class Saved(val text: String) : SaveStatus
    data class Failed(val text: String, val message: String) : SaveStatus
}

interface DraftApi {
    /** Saves the review draft on the server. Slow, and may throw. */
    suspend fun save(text: String)
}

/**
 * Auto-saves a restaurant review while the user types. Read the spec in README.md.
 * The tests check every line of it.
 *
 * @param scope the ViewModel's scope
 */
class DraftAutoSaver(
    private val api: DraftApi,
    private val scope: CoroutineScope,
) {

    val status: StateFlow<SaveStatus> get() = TODO("F08: implement")

    /** Called on every keystroke, on the main thread. */
    fun onTextChanged(text: String) {
        TODO("F08: implement")
    }

    /** Called when the screen is closing: save the latest text now, and return once it's saved. */
    suspend fun flush() {
        TODO("F08: implement")
    }

    companion object {
        const val DEBOUNCE_MS = 1_000L
    }
}
