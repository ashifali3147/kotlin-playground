package challenges.flow.f04_checkout

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.zip

data class Cart(val items: Map<String, Int>)
data class Address(val pincode: String)
enum class PaymentMethod { UPI, CARD, CASH }

sealed interface CheckoutState {
    data object Loading : CheckoutState
    data class Ready(
        val cart: Cart,
        val address: Address?,
        val payment: PaymentMethod?,
        val totalPaise: Long,
        val canPlaceOrder: Boolean,
    ) : CheckoutState
}

interface PricingApi {
    /** Server-side price quote (taxes, delivery fee by pincode, offers). Takes ~300ms. */
    suspend fun quote(cart: Cart, address: Address?): Long
}

/**
 * Checkout screen. The three sources come from repositories and change independently:
 * the user edits the cart, picks an address, or switches payment method.
 */
class CheckoutViewModel(
    cart: Flow<Cart>,
    address: Flow<Address?>,
    payment: Flow<PaymentMethod?>,
    private val pricing: PricingApi,
    scope: CoroutineScope,
) {

    val state: StateFlow<CheckoutState> = cart
        .zip(address) { c, a -> c to a }
        .zip(payment) { (c, a), p -> Triple(c, a, p) }
        .map { (c, a, p) ->
            CheckoutState.Ready(
                cart = c,
                address = a,
                payment = p,
                totalPaise = pricing.quote(c, a),
                canPlaceOrder = a != null && p != null,
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, CheckoutState.Loading)
}
