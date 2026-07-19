package coroutines

import utils.LogColor

interface RestaurantV2 {
    suspend fun takeOrder(name: String) {
        LogColor.log(LogColor.YELLOW, "📝 Taking order for $name")
    }
    suspend fun prepareFood(name: String, delay: Long)
    suspend fun serveFood(name: String) {
        LogColor.log(LogColor.GREEN, "🍽 Serving food to $name")
    }
}