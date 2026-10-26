package challenges.threads

import challenges.runConcurrently
import challenges.threads.c03_wallet.Account
import challenges.threads.c03_wallet.InsufficientFundsException
import challenges.threads.c03_wallet.WalletService
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Timeout(30, unit = TimeUnit.SECONDS)
class C03WalletServiceTest {

    private val wallet = WalletService()

    @Test
    fun `single thread - basic rules`() {
        val alice = Account("alice", 100)
        val bob = Account("bob", 0)

        wallet.transfer(alice, bob, 30)
        assertEquals(70, alice.balance)
        assertEquals(30, bob.balance)

        assertFailsWith<InsufficientFundsException> { wallet.transfer(bob, alice, 31) }
        assertFailsWith<IllegalArgumentException> { wallet.transfer(alice, bob, 0) }
        assertEquals(70, alice.balance)
        assertEquals(30, bob.balance)
    }

    @RepeatedTest(3)
    fun `friends paying each other at the same time - never freezes`() {
        val alice = Account("alice", 1_000_000)
        val bob = Account("bob", 1_000_000)

        runConcurrently(threadCount = 4, timeoutMs = 5_000, namePrefix = "payment") { i ->
            repeat(50_000) {
                if (i % 2 == 0) wallet.transfer(alice, bob, 1) else wallet.transfer(bob, alice, 1)
            }
        }

        assertEquals(2_000_000, alice.balance + bob.balance, "money was created or destroyed")
    }

    @RepeatedTest(3)
    fun `three-way cycle A to B, B to C, C to A - never freezes`() {
        val accounts = listOf(Account("a", 1_000_000), Account("b", 1_000_000), Account("c", 1_000_000))

        runConcurrently(threadCount = 3, timeoutMs = 5_000, namePrefix = "payment") { i ->
            val from = accounts[i]
            val to = accounts[(i + 1) % 3]
            repeat(50_000) { wallet.transfer(from, to, 1) }
        }

        assertEquals(3_000_000, accounts.sumOf { it.balance }, "money was created or destroyed")
    }

    @RepeatedTest(3)
    fun `rapid repeated payments - balance never goes negative`() {
        val startBalance = 20_000L
        val user = Account("user", startBalance)
        val restaurants = List(8) { Account("restaurant-$it", 0) }
        val succeeded = AtomicInteger()

        // 8 threads x 5_000 attempts of 1 each = 40_000 attempts against a balance of 20_000.
        runConcurrently(threadCount = 8, namePrefix = "pay") { i ->
            repeat(5_000) {
                try {
                    wallet.transfer(user, restaurants[i], 1)
                    succeeded.incrementAndGet()
                } catch (_: InsufficientFundsException) {
                    // expected once the money runs out
                }
            }
        }

        assertTrue(user.balance >= 0, "Balance went negative: ${user.balance}")
        assertEquals(startBalance.toInt(), succeeded.get(), "Exactly $startBalance payments of 1 should succeed")
        assertEquals(startBalance, user.balance + restaurants.sumOf { it.balance }, "money was created or destroyed")
    }

    @Test
    fun `transfer to yourself is harmless`() {
        val alice = Account("alice", 50)
        runConcurrently(threadCount = 4) { repeat(10_000) { wallet.transfer(alice, alice, 5) } }
        assertEquals(50, alice.balance)
    }
}
