package challenges.flow.f02_search

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Results(val query: String, val restaurants: List<String>) : SearchState
    data class Error(val query: String, val message: String) : SearchState
}

interface SearchApi {
    /** Network search. Takes 100ms–2s. May throw. */
    suspend fun search(query: String): List<String>
}

/**
 * Restaurant search screen. [onQueryChanged] is called on every keystroke.
 *
 * @param scope the ViewModel's scope (viewModelScope in the real app)
 */
class SearchViewModel(private val api: SearchApi, scope: CoroutineScope) {

    private val query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<SearchState> = query
        .flatMapMerge { q ->
            flow { emit(SearchState.Results(q, api.search(q))) }
        }
        .stateIn(scope, SharingStarted.Eagerly, SearchState.Idle)

    fun onQueryChanged(text: String) {
        query.value = text
    }

    companion object {
        const val DEBOUNCE_MS = 300L
        const val MIN_QUERY_LENGTH = 2
    }
}
