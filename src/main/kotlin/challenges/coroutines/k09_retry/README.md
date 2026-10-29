# K09 · Build: Retry With Exponential Backoff 🏗

**Track:** Coroutines · **Type:** Build · **Difficulty:** ★★★☆☆

## The story

Payments sometimes fail with a transient network error. Product wants automatic retries.
The last attempt at this ("retry 5 times in a `while` loop") brought the payment gateway down during an outage:
**every phone retried instantly**, all at the same time. That's a self-inflicted DDoS.

Write a reusable `retryWithBackoff` that the whole team can use.

Your file: [`RetryWithBackoff.kt`](RetryWithBackoff.kt). It's a `TODO()`.

## Spec
1. Call `block(attempt)` with `attempt` = 1, 2, 3, ... and return its result as soon as one succeeds.
2. Waits between attempts: `initialDelayMs`, then times `factor` each time, but never more than `maxDelayMs`.
   For example, with initial 100, factor 2 and max 800, the waits are 100, 200, 400, 800, 800, ...
3. No wait after the last attempt. Once `maxAttempts` attempts have failed, throw the **last** error (the original exception, not a wrapper).
4. If `shouldRetry(error)` is false, throw it **immediately**, with no more attempts.
5. **Never** retry a `CancellationException`, even if `shouldRetry` says yes to everything. Rethrow it.
6. If the caller is cancelled during a wait, stop immediately: no more attempts.
7. `maxAttempts < 1` → `IllegalArgumentException`.

## Rules
- Don't edit the test file. Keep the signature.
- No AI.

## Run
```bash
./dojo K09
```
The tests check the exact timing of each attempt with virtual time.

## Before asking for a review, write in `JOURNAL.md`
1. Why exponential? Why a max delay?
2. What's **jitter**, and why does it matter with a million phones? How would you add it, and how would you test it?
3. Spec 5: what goes wrong if you retry a `CancellationException`?
4. If `block` contains `withTimeout(...)` and that times out, it throws `TimeoutCancellationException`, which *is* a `CancellationException`.
   Should that be retried? Defend your answer.
5. Which errors should *not* be retried for a payment? (Think: idempotency, HTTP 400 vs 503.)

## Hints
<details><summary>Hint 1</summary>

A simple `for`/`repeat` loop with try/catch is enough. No recursion, no `Flow`.
</details>

<details><summary>Hint 2</summary>

Use `delay()`, not `Thread.sleep()`, and think about why. Your catch has to tell a cancellation apart from a real failure.
</details>

<details><summary>Hint 3</summary>

Careful with the delay maths. Compute the next delay as a `Double`, cap it, and only then convert to `Long`.
Also check the edge case `maxAttempts == 1`.
</details>
