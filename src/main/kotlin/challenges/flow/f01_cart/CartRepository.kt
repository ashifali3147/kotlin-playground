package challenges.flow.f01_cart

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class Cart(val items: Int, val version: Int)

interface CartApi {
    suspend fun fetchCart(): Cart
}

/**
 * Single source of truth for the user's cart.
 *
 * @param appScope application-wide scope that lives as long as the app process
 */
class CartRepository(
    private val api: CartApi,
    private val appScope: CoroutineScope,
) {

    /**
     * The cart, refreshed every [REFRESH_MS].
     * Collected at the same time by the toolbar badge, the cart screen and the checkout button.
     */
    val cart: Flow<Cart> = flow {
        while (true) {
            emit(api.fetchCart())
            delay(REFRESH_MS)
        }
    }

    companion object {
        const val REFRESH_MS = 30_000L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
