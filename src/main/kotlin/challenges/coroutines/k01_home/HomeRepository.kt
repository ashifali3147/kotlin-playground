package challenges.coroutines.k01_home

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async

data class Profile(val name: String)
data class Offer(val title: String)
data class OrderSummary(val id: Int)
data class HomeScreen(val profile: Profile, val offers: List<Offer>, val recentOrders: List<OrderSummary>)

/** Retrofit-style API. Every function is already main-safe and cancellable. */
interface HomeApi {
    suspend fun profile(): Profile
    suspend fun offers(): List<Offer>
    suspend fun recentOrders(): List<OrderSummary>
}

class HomeRepository(private val api: HomeApi) {

    /** Called from the HomeViewModel's viewModelScope when the home screen opens. */
    @OptIn(DelicateCoroutinesApi::class)
    suspend fun loadHome(): HomeScreen {
        val profile = GlobalScope.async { api.profile() }.await()
        val offers = GlobalScope.async { api.offers() }
        val orders = GlobalScope.async { api.recentOrders() }
        return HomeScreen(profile, offers.await(), orders.await())
    }
}
