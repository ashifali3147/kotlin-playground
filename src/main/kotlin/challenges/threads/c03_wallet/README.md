# T03 · The Wallet That Freezes (and Prints Money) 🐛

**Track:** Threads · **Type:** Fix · **Difficulty:** ★★★☆☆ (stretch goal: ★★★★☆)

## The story

Your startup added an in-app wallet. Two incident reports landed on the same day:

1. **"App frozen."** Two friends were splitting a bill and sent money to each other at the same moment.
   Both apps hung on "Processing…" forever, and the payment service needed a restart.
2. **"Negative balance."** A user with ₹100 tapped "Pay ₹80" twice quickly (double tap, flaky network,
   retry). Both payments went through and the balance is now **-₹60**. Finance is not happy.

Your file: [`WalletService.kt`](WalletService.kt)

## Your task
Make `transfer()` safe under any amount of concurrency:
- It must never freeze, whatever the order or direction of transfers.
- A balance must never go below zero. If there isn't enough money, throw `InsufficientFundsException`.
- Money is never created or destroyed: the total across all accounts stays the same.

## Rules
- Don't edit the test file. Keep the public API (you may add **private** members or helper code).
- No AI.

## Run
```bash
./gradlew test --tests "challenges.threads.C03*"
```
When the freeze test fails, **read the thread dump it prints.** Learning to read a thread dump
is the actual skill here. It's the same thing you'll see in an ANR trace or a server hang.

## Stretch goal (I'll check this in the review)
Locking the whole `WalletService` with one global lock passes every test, but then
**A→B and C→D can't run in parallel**, even though they share nothing. At scale that's a
throughput disaster. Solve it so unrelated transfers never block each other.

## Before asking for a review, write in `JOURNAL.md`
1. What are the 4 conditions needed for a deadlock? Which one did your fix break?
2. From the thread dump: how did you tell *which* thread held *which* lock?
3. What is "check-then-act"? Where else have you seen it? (Hint: T01, lazy init, `if (!file.exists()) create()`.)
4. What is the trade-off between `synchronized` and `ReentrantLock.tryLock(timeout)` for this problem?

## Hints
<details><summary>Hint 1</summary>

Thread 1 runs `transfer(alice, bob)` and Thread 2 runs `transfer(bob, alice)`.
Step through which lock each one grabs first. Draw it on paper.
</details>

<details><summary>Hint 2</summary>

There are two bugs. The freeze comes from lock *order*. The negative balance comes from
reading the balance at a moment when you don't hold the lock. Fixing one doesn't fix the other.
</details>

<details><summary>Hint 3</summary>

If every thread always took the locks in the **same global order**, could a cycle ever form?
What property of an `Account` could define that order? What happens when `from == to`?
</details>
