package challenges.threads.c03_wallet

class Account(val id: String, initialBalance: Long) {
    var balance: Long = initialBalance
        internal set
}

class InsufficientFundsException(message: String) : Exception(message)

/**
 * In-app wallet: users pay restaurants, get refunds, send money to friends.
 * Requests come in on a thread pool, so many transfers run at the same time,
 * and the same account can appear in several transfers at once.
 */
class WalletService {

    fun transfer(from: Account, to: Account, amount: Long) {
        require(amount > 0) { "amount must be positive" }

        if (from.balance < amount) {
            throw InsufficientFundsException("${from.id} has ${from.balance}, needs $amount")
        }

        synchronized(from) {
            synchronized(to) {
                from.balance -= amount
                to.balance += amount
            }
        }
    }
}
