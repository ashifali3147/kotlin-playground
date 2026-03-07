package thread

class ThreadExample : Restaurant {

    fun handleCustomer(name: String) {
        takeOrder(name)
        prepareFood(name)
        serveFood(name)
        println("--------------------")
    }

    override fun prepareFood(name: String) {
        println("Preparing food for $name...")
        Thread.sleep(5000)
    }

}