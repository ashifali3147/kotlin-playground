# Concurrency Dojo 🥋

Real-world, production-style problems on **threads → coroutines → Flow**.
No DSA. No copy-paste. You remember what you struggle through.

## The loop

1. **Pick** the next challenge in the tables below and open its `README.md`.
2. **Run the tests.** Watch them go red and read the failure output. The failure is a clue.
3. **Investigate before fixing.** Form a theory and prove it with an experiment, not a guess.
4. **Fix** until it's green: `./gradlew test --tests "challenges.threads.C01*"`
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
Sequential-vs-parallel `async` · blocking inside coroutines · cancellation ·
scope leaks · `supervisorScope` and exception handling · `Mutex` · timeouts and cleanup ·
limiting how much runs at once · callback → `suspendCancellableCoroutine` · retry with backoff

## Track 3 · Flow
Cold vs hot · search-as-you-type · one-shot UI events · `combine` vs `zip` ·
exception transparency and `flowOn` · backpressure · `callbackFlow` leaks · testing with Turbine

## Track 4 · Boss levels
Production incidents with several bugs mixed together.
