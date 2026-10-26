@file:OptIn(ExperimentalCoroutinesApi::class)

package challenges.flow

import challenges.flow.f04_checkout.Address
import challenges.flow.f04_checkout.Cart
import challenges.flow.f04_checkout.CheckoutState
import challenges.flow.f04_checkout.CheckoutViewModel
import challenges.flow.f04_checkout.PaymentMethod
import challenges.flow.f04_checkout.PricingApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class F04CheckoutViewModelTest {

    /** Total = 100 paise per item + 40 paise delivery when an address is set. Takes 300ms. */
    private class FakePricing : PricingApi {
        val quotes = mutableListOf<Cart>()

        override suspend fun quote(cart: Cart, address: Address?): Long {
            quotes += cart
            delay(300)
            return cart.items.values.sum() * 100L + if (address != null) 40 else 0
        }
    }

    private class Sources {
        val cart = MutableStateFlow(Cart(mapOf("naan" to 1)))
        val address = MutableStateFlow<Address?>(Address("560001"))
        val payment = MutableStateFlow<PaymentMethod?>(PaymentMethod.UPI)
    }

    private class Screen(val vm: CheckoutViewModel, val states: MutableList<CheckoutState>) {
        val ready get() = vm.state.value as CheckoutState.Ready
    }

    /** A "viewModelScope" that runs on the test's virtual clock. */
    private fun TestScope.viewModelScope() = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))

    private fun TestScope.open(src: Sources, pricing: FakePricing): Screen {
        val scope = viewModelScope()
        val vm = CheckoutViewModel(src.cart, src.address, src.payment, pricing, scope)
        val states = mutableListOf<CheckoutState>()
        scope.launch { vm.state.toList(states) }
        return Screen(vm, states)
    }

    @Test
    fun `initial state is calculated once all sources are known`() = runTest {
        val screen = open(Sources(), FakePricing())
        advanceUntilIdle()

        assertEquals(
            CheckoutState.Ready(
                cart = Cart(mapOf("naan" to 1)),
                address = Address("560001"),
                payment = PaymentMethod.UPI,
                totalPaise = 140,
                canPlaceOrder = true,
            ),
            screen.vm.state.value,
        )
    }

    @Test
    fun `loading until every source has emitted`() = runTest {
        val payment = MutableSharedFlow<PaymentMethod?>() // payment methods still loading
        val vm = CheckoutViewModel(Sources().cart, Sources().address, payment, FakePricing(), viewModelScope())
        advanceTimeBy(5_000)
        assertEquals(CheckoutState.Loading, vm.state.value)

        payment.emit(null)
        advanceUntilIdle()
        assertTrue(vm.state.value is CheckoutState.Ready)
        assertEquals(false, (vm.state.value as CheckoutState.Ready).canPlaceOrder)
    }

    @Test
    fun `changing only the payment method updates the screen`() = runTest {
        val src = Sources()
        val screen = open(src, FakePricing())
        advanceUntilIdle()

        src.payment.value = PaymentMethod.CARD
        advanceUntilIdle()

        assertEquals(PaymentMethod.CARD, screen.ready.payment, "Screen still shows the old payment method")
    }

    @Test
    fun `changing only the payment method does not call the pricing API`() = runTest {
        val src = Sources()
        val pricing = FakePricing()
        open(src, pricing)
        advanceUntilIdle()
        val quotesBefore = pricing.quotes.size

        src.payment.value = PaymentMethod.CASH
        advanceUntilIdle()

        assertEquals(quotesBefore, pricing.quotes.size, "Payment doesn't affect price, so don't re-quote")
    }

    @Test
    fun `address change re-quotes with the new address`() = runTest {
        val src = Sources()
        val screen = open(src, FakePricing())
        advanceUntilIdle()

        src.address.value = null
        advanceUntilIdle()

        assertEquals(100, screen.ready.totalPaise)
        assertEquals(false, screen.ready.canPlaceOrder)
    }

    @Test
    fun `rapid cart edits - an outdated total is never shown`() = runTest {
        val src = Sources()
        val screen = open(src, FakePricing())
        advanceUntilIdle()

        for (qty in 2..5) { // user taps "+" every 100ms; each quote takes 300ms
            src.cart.value = Cart(mapOf("naan" to qty))
            advanceTimeBy(100)
        }
        advanceUntilIdle()

        val totals = screen.states.filterIsInstance<CheckoutState.Ready>().map { it.totalPaise }
        assertEquals(540, screen.ready.totalPaise, "Final total should be for 5 naan")
        assertEquals(listOf(140L, 540L), totals.distinct(), "Outdated totals were shown along the way: $totals")
    }

    @Test
    fun `total and cart shown together always match`() = runTest {
        val src = Sources()
        val screen = open(src, FakePricing())
        advanceUntilIdle()

        src.cart.value = Cart(mapOf("naan" to 3))
        runCurrent()
        advanceTimeBy(100)
        src.payment.value = PaymentMethod.CARD // payment changes while the quote is in flight
        advanceUntilIdle()

        screen.states.filterIsInstance<CheckoutState.Ready>().forEach {
            val expected = it.cart.items.values.sum() * 100L + if (it.address != null) 40 else 0
            assertEquals(expected, it.totalPaise, "Showed a total that doesn't match the cart on screen: $it")
        }
    }
}
