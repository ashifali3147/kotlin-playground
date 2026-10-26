package challenges.flow.f03_events

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull

sealed interface CartEvent {
    data class ShowSnackbar(val message: String) : CartEvent
    data object NavigateToCheckout : CartEvent
}

/**
 * Cart screen. The Activity/Fragment collects [events] while it is STARTED
 * (repeatOnLifecycle), so during rotation or while the app is in the background nobody collects.
 */
class CartViewModel {

    private val _events = MutableStateFlow<CartEvent?>(null)

    /** One-shot UI events: show a snackbar, navigate somewhere. */
    val events: Flow<CartEvent> = _events.filterNotNull()

    fun onItemAdded(name: String) {
        _events.value = CartEvent.ShowSnackbar("$name added to cart")
    }

    fun onCheckoutClicked() {
        _events.value = CartEvent.NavigateToCheckout
    }
}
