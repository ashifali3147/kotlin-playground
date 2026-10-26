package challenges.flow

import app.cash.turbine.test
import challenges.flow.f03_events.CartEvent
import challenges.flow.f03_events.CartViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class F03CartViewModelTest {

    private val naanAdded = CartEvent.ShowSnackbar("Naan added to cart")

    @Test
    fun `basic - one event, delivered`() = runTest {
        val vm = CartViewModel()

        vm.events.test {
            vm.onCheckoutClicked()
            assertEquals(CartEvent.NavigateToCheckout, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `same snackbar twice in a row - shown twice`() = runTest {
        val vm = CartViewModel()

        vm.events.test {
            vm.onItemAdded("Naan")
            vm.onItemAdded("Naan")
            assertEquals(naanAdded, awaitItem())
            assertEquals(naanAdded, awaitItem(), "The second identical snackbar was swallowed")
            expectNoEvents()
        }
    }

    @Test
    fun `event sent while nobody collects - delivered when the screen comes back`() = runTest {
        val vm = CartViewModel()

        vm.onItemAdded("Naan") // phone locked, nobody is collecting

        vm.events.test {
            assertEquals(naanAdded, awaitItem(), "The event sent while the screen was stopped was lost")
            expectNoEvents()
        }
    }

    @Test
    fun `already-delivered event is not delivered again after rotation`() = runTest {
        val vm = CartViewModel()

        vm.events.test {
            vm.onCheckoutClicked()
            assertEquals(CartEvent.NavigateToCheckout, awaitItem())
            cancelAndIgnoreRemainingEvents() // rotation: the old collector goes away
        }

        vm.events.test { // the new Activity starts collecting
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `burst of events with a slow collector - all delivered, in order`() = runTest {
        val vm = CartViewModel()

        vm.events.onEach { delay(500) }.test { // e.g. each snackbar takes time to show
            vm.onItemAdded("Naan")
            vm.onItemAdded("Dal")
            vm.onCheckoutClicked()

            assertEquals(naanAdded, awaitItem())
            assertEquals(CartEvent.ShowSnackbar("Dal added to cart"), awaitItem())
            assertEquals(CartEvent.NavigateToCheckout, awaitItem())
        }
    }
}
