@file:OptIn(ExperimentalCoroutinesApi::class)

package challenges.flow

import challenges.flow.f02_search.SearchApi
import challenges.flow.f02_search.SearchState
import challenges.flow.f02_search.SearchViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class F02SearchViewModelTest {

    private class FakeSearchApi(
        private val delays: Map<String, Long> = emptyMap(),
        private val failing: Set<String> = emptySet(),
    ) : SearchApi {
        val searched = mutableListOf<String>()
        val cancelled = mutableListOf<String>()

        override suspend fun search(query: String): List<String> {
            searched += query
            try {
                delay(delays[query] ?: 100)
            } catch (e: CancellationException) {
                cancelled += query
                throw e
            }
            if (query in failing) throw IOException("timeout")
            return listOf("$query palace", "$query corner")
        }
    }

    private class Screen(val vm: SearchViewModel, val states: MutableList<SearchState>)

    /** The ViewModel and the screen's collector share a "viewModelScope" that runs on the test's virtual clock. */
    private fun TestScope.open(api: FakeSearchApi): Screen {
        val viewModelScope = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
        val vm = SearchViewModel(api, viewModelScope)
        val states = mutableListOf<SearchState>()
        viewModelScope.launch { vm.state.toList(states) }
        return Screen(vm, states)
    }

    private fun TestScope.type(screen: Screen, vararg texts: String, gapMs: Long = 100) {
        for (t in texts) {
            screen.vm.onQueryChanged(t)
            advanceTimeBy(gapMs)
        }
    }

    @Test
    fun `typing a word fires one search, after the user pauses`() = runTest {
        val api = FakeSearchApi()
        val screen = open(api)

        type(screen, "b", "bi", "bir", "biry", "birya", "biryan", "biryani", gapMs = 100)
        assertEquals(emptyList(), api.searched, "Searched while the user was still typing")

        advanceUntilIdle()
        assertEquals(listOf("biryani"), api.searched)
        assertEquals(SearchState.Results("biryani", listOf("biryani palace", "biryani corner")), screen.vm.state.value)
    }

    @Test
    fun `loading is shown before results`() = runTest {
        val screen = open(FakeSearchApi())

        type(screen, "dosa")
        advanceUntilIdle()

        val loadingIndex = screen.states.indexOf(SearchState.Loading)
        val resultsIndex = screen.states.indexOfFirst { it is SearchState.Results }
        assertTrue(loadingIndex in 0 until resultsIndex, "Expected Loading before Results, got ${screen.states}")
    }

    @Test
    fun `short queries are idle and never hit the API`() = runTest {
        val api = FakeSearchApi()
        val screen = open(api)

        type(screen, " p ", gapMs = 1_000)

        assertEquals(emptyList(), api.searched)
        assertEquals(SearchState.Idle, screen.vm.state.value)
    }

    @Test
    fun `query is trimmed`() = runTest {
        val api = FakeSearchApi()
        val screen = open(api)

        type(screen, "  momos  ", gapMs = 1_000)

        assertEquals(listOf("momos"), api.searched)
    }

    @Test
    fun `settling back on the same query does not search again`() = runTest {
        val api = FakeSearchApi()
        val screen = open(api)

        type(screen, "pizza", gapMs = 1_000)
        type(screen, "pizzas", "pizza", gapMs = 100)
        advanceUntilIdle()

        assertEquals(listOf("pizza"), api.searched)
    }

    @Test
    fun `slow old search never overwrites a newer one`() = runTest {
        val api = FakeSearchApi(delays = mapOf("piz" to 2_000, "pizza" to 100))
        val screen = open(api)

        type(screen, "piz", gapMs = 800) // "piz" search starts at 300ms and is slow
        type(screen, "pizza", gapMs = 100) // user keeps typing
        advanceUntilIdle()

        assertTrue(
            screen.states.none { it is SearchState.Results && it.query == "piz" },
            "Stale 'piz' results were shown: ${screen.states}"
        )
        assertEquals(listOf("piz"), api.cancelled, "The outdated 'piz' search should be cancelled")
        assertEquals("pizza", (screen.vm.state.value as SearchState.Results).query)
    }

    @Test
    fun `one failed search does not kill search`() = runTest {
        val api = FakeSearchApi(failing = setOf("paneer"))
        val screen = open(api)

        type(screen, "paneer", gapMs = 1_000)
        assertEquals(SearchState.Error("paneer", "timeout"), screen.vm.state.value)

        type(screen, "naan", gapMs = 1_000)
        assertEquals("naan", (screen.vm.state.value as? SearchState.Results)?.query, "Search stopped working after an error")
    }
}
