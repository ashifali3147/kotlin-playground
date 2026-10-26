package challenges.flow.f05_menu

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class Menu(val dishes: List<String>)

sealed interface MenuState {
    /** From the local cache. Shown instantly while the network loads. */
    data class Cached(val menu: Menu) : MenuState
    data class Fresh(val menu: Menu) : MenuState
    /** Network gave up. Keep showing the cached menu, with an "offline" banner. */
    data class Offline(val menu: Menu, val reason: String) : MenuState
}

/** Legacy SQLite cache. Blocking calls. */
interface MenuCache {
    fun read(restaurantId: Int): Menu
    fun write(restaurantId: Int, menu: Menu)
}

interface MenuApi {
    /** May throw IOException on flaky networks. */
    suspend fun fetchMenu(restaurantId: Int): Menu
}

/**
 * @param io dispatcher for blocking disk work (Dispatchers.IO in the real app)
 */
class MenuRepository(
    private val cache: MenuCache,
    private val api: MenuApi,
    private val io: CoroutineDispatcher,
) {

    /** Collected by the restaurant screen on the main thread. */
    fun menu(restaurantId: Int): Flow<MenuState> = flow {
        try {
            emit(MenuState.Cached(cache.read(restaurantId)))
            val fresh = api.fetchMenu(restaurantId)
            cache.write(restaurantId, fresh)
            emit(MenuState.Fresh(fresh))
        } catch (e: Exception) {
            emit(MenuState.Offline(cache.read(restaurantId), e.message ?: "unknown error"))
        }
    }

    companion object {
        /** Network attempts in total, including the first one. */
        const val MAX_NETWORK_ATTEMPTS = 3
        /** Wait before the 2nd attempt. Doubles after each failure (1s, 2s, ...). */
        const val RETRY_DELAY_MS = 1_000L
    }
}
