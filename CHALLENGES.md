# Concurrency Dojo 🥋

Real-world, production-style problems on **threads → coroutines → Flow**.
No DSA. No copy-paste. You remember what you struggle through.

## The loop

1. **Pick** the next challenge in the tables below and open its `README.md`.
2. **Run the tests.** Watch them go red and read the failure output. The failure is a clue.
3. **Investigate before fixing.** Form a theory and prove it with an experiment, not a guess.
4. **Fix** until it's green: `./dojo T01` (add `-d` to see why a test failed, where, and what it printed)
5. **Journal**: answer the README's questions in [`JOURNAL.md`](JOURNAL.md), in your own words, from memory.
6. **Review**: ask Claude *"review T01"*. You'll get a score, a better approach (if there is one),
   and interviewer follow-up questions.
7. **Update** [`PROGRESS.md`](PROGRESS.md).
8. **Revisit**: about 2 weeks later, ask for a *variant* of a solved challenge and solve it cold.

**Rules:** no AI while solving. Don't edit test files. Hints are layered, so open one only after a real attempt.
Being stuck for 45 minutes is part of the training, not a failure.

## How a review is scored (/10)

| Area | Weight | What's checked |
|---|---|---|
| Correctness | 3 | Races the tests can't catch, edge cases, contract honoured |
| Concurrency cost | 2 | Lock scope and contention, blocking vs suspending, threads held, allocations |
| Design & idiom | 2 | Right tool for the job, readable, how an experienced Kotlin dev would write it |
| Structured concurrency | 1 | Ownership, cancellation, error propagation (from the coroutine track on) |
| Understanding | 2 | Your journal: can you explain *why*, not just *what*? |

## Track 1 · Threads & the JVM memory model

| # | Challenge | Concept | Difficulty |
|---|---|---|---|
| T01 | [The Analytics Numbers Don't Add Up](src/main/kotlin/challenges/threads/c01_analytics/README.md) | Race conditions, atomicity, compound actions | ★★☆☆☆ |
| T02 | [The Rider App That Never Stops Uploading](src/main/kotlin/challenges/threads/c02_uploader/README.md) | Visibility, happens-before, `join` | ★★★☆☆ |
| T03 | [The Wallet That Freezes (and Prints Money)](src/main/kotlin/challenges/threads/c03_wallet/README.md) | Deadlock, lock ordering, check-then-act | ★★★☆☆ |
| T04 | [The Avatar That Downloads 40 Times](src/main/kotlin/challenges/threads/c04_avatarcache/README.md) | Check-then-act, memoization, lock scope | ★★★★☆ |
| T05 | [Dinner Rush Takes Down Dispatch](src/main/kotlin/challenges/threads/c05_dispatchqueue/README.md) | `wait`/`notify`, conditions, bounded blocking queue | ★★★☆☆ |
| T06 | [The Listener That Crashes on a Single Thread](src/main/kotlin/challenges/threads/c06_listeners/README.md) | CME, copy-on-write, calling foreign code under a lock | ★★★☆☆ |
| T07 | [The Export Button That Kills the Server](src/main/kotlin/challenges/threads/c07_reportexport/README.md) | Executor lifetime, thread leaks, pool-starvation deadlock | ★★★★☆ |
| T08 | [Build a Connection Pool](src/main/kotlin/challenges/threads/c08_connectionpool/README.md) 🏗 | `Semaphore`, resource ownership, failure paths | ★★★★☆ |

## Track 2 · Coroutines *(unlocks after T08)*

Tests use `kotlinx-coroutines-test` (`runTest`, virtual time). Learning to read those tests is part of the track.

| # | Challenge | Concept | Difficulty |
|---|---|---|---|
| K01 | [The Home Screen That Ignores You](src/main/kotlin/challenges/coroutines/k01_home/README.md) | Structured concurrency, `async`, `GlobalScope` | ★★☆☆☆ |
| K02 | ["But It's a Suspend Function!"](src/main/kotlin/challenges/coroutines/k02_receipt/README.md) | Dispatchers, main-safety, `withContext` | ★★☆☆☆ |
| K03 | [The Cancel Button That Doesn't Cancel](src/main/kotlin/challenges/coroutines/k03_upload/README.md) | Cooperative cancellation, swallowed `CancellationException`, `NonCancellable` | ★★★☆☆ |
| K04 | [The Tracker That Polls Forever](src/main/kotlin/challenges/coroutines/k04_tracking/README.md) | Owning a scope, `Job` lifecycle, `SupervisorJob` | ★★★☆☆ |
| K05 | [One Bad Photo Breaks the Whole Menu](src/main/kotlin/challenges/coroutines/k05_prefetch/README.md) | `supervisorScope`, exception propagation, `CoroutineExceptionHandler` | ★★★☆☆ |
| K06 | [The Refresh Token Stampede](src/main/kotlin/challenges/coroutines/k06_token/README.md) | `Mutex`, double-check, compare-and-set | ★★★★☆ |
| K07 | [HTTP 429: Too Many Uploads](src/main/kotlin/challenges/coroutines/k07_photos/README.md) | `Semaphore`, the `limitedParallelism` trap | ★★★☆☆ |
| K08 | [GPS That Never Turns Off](src/main/kotlin/challenges/coroutines/k08_location/README.md) | `suspendCancellableCoroutine`, `withTimeoutOrNull` | ★★★☆☆ |
| K09 | [Build: Retry With Exponential Backoff](src/main/kotlin/challenges/coroutines/k09_retry/README.md) 🏗 | Backoff, cancellation-safe retries | ★★★☆☆ |
| K10 | [Interview Round: "What Does This Print?"](src/main/kotlin/challenges/coroutines/k10_predict/README.md) 🔍 | Execution order, propagation, `finally` | ★★★☆☆ |

## Track 3 · Flow *(unlocks after K10)*

| # | Challenge | Concept | Difficulty |
|---|---|---|---|
| F01 | [The Cart That's Fetched Three Times](src/main/kotlin/challenges/flow/f01_cart/README.md) | Cold vs hot, `shareIn`, `WhileSubscribed`, replay expiration | ★★★☆☆ |
| F02 | [Search That Shows the Wrong Restaurants](src/main/kotlin/challenges/flow/f02_search/README.md) | `debounce`, `distinctUntilChanged`, `flatMapLatest`, inner `catch` | ★★★☆☆ |
| F03 | [The Snackbar That Shows Twice (or Never)](src/main/kotlin/challenges/flow/f03_events/README.md) | One-shot events: StateFlow vs SharedFlow vs Channel, Turbine | ★★★☆☆ |
| F04 | [The Checkout Total That Lies](src/main/kotlin/challenges/flow/f04_checkout/README.md) | `zip` vs `combine`, `mapLatest`, pipeline design | ★★★☆☆ |
| F05 | [The Offline Menu That Crashes Online](src/main/kotlin/challenges/flow/f05_menu/README.md) | Exception transparency, `flowOn`, `retryWhen` | ★★★★☆ |
| F06 | [The Rider Marker That Lags Behind](src/main/kotlin/challenges/flow/f06_backpressure/README.md) | Backpressure: `conflate` vs `collectLatest` vs `buffer` | ★★★☆☆ |
| F07 | [The Socket That Never Lets Go](src/main/kotlin/challenges/flow/f07_socket/README.md) | `callbackFlow`, `awaitClose`, `trySend`, closing with a cause | ★★★☆☆ |
| F08 | [Build: The Review Draft Auto-Saver](src/main/kotlin/challenges/flow/f08_autosave/README.md) 🏗 | Debounce + conflate + Mutex, status `StateFlow` | ★★★★☆ |
| F09 | [Interview Round: "What Does This Flow Print?"](src/main/kotlin/challenges/flow/f09_predict/README.md) 🔍 | Cold flows, buffering, conflation, `catch`, `combine` | ★★★☆☆ |

## Track 4 · Boss levels *(unlocks after F08)*

Production incidents: one realistic component, several bugs from every track, and a README that reads like an incident report.
The tests are symptoms, not bugs. Each boss ends with a post-mortem debrief in your journal.

| # | Incident | Bugs | Mixes | Difficulty |
|---|---|---|---|---|
| B01 | [The Image Loader](src/main/kotlin/challenges/boss/b01_imageloader/README.md) | 6 | Thread-safe LRU, request dedup, dispatchers, `Semaphore`, failure cleanup, reference-counted cancellation | ★★★★★ |
| B02 | [Duplicate Orders](src/main/kotlin/challenges/boss/b02_outbox/README.md) | 5 | Thread-safe enqueue, one sync loop, `collectLatest`, per-order ordering, backoff, a `Channel` doorbell | ★★★★★ |
| B03 | [The Tracking Screen](src/main/kotlin/challenges/boss/b03_tracking/README.md) | 7 | Shared `callbackFlow`, non-blocking callbacks, polling fallback, ETA off main, one-shot events, `transformWhile` | ★★★★★ |
