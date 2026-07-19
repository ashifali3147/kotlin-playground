package coroutines

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import utils.LogColor

class CoroutinesExample : RestaurantV2 {

    fun handleCustomer(name: String, delay: Long): Unit = runBlocking {
        takeOrder(name)
        prepareFood(name, delay)
        serveFood(name)
        LogColor.log(LogColor.YELLOW, "--------------------")
    }

    override suspend fun prepareFood(name: String, delay: Long) {
        LogColor.log(LogColor.CYAN, "👨‍🍳 Preparing food for $name...")
        delay(delay)
    }
}