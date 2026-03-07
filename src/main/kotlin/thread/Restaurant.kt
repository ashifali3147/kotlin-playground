package thread

interface Restaurant {
    fun takeOrder(name: String) {
        println("Taking order for $name")
    }
    fun prepareFood(name: String)
    fun serveFood(name: String) {
        println("Serving food to $name")
    }
}