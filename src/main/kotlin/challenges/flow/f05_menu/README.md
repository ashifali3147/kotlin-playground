# F05 · The Offline Menu That Crashes Online 🐛

**Track:** Flow · **Type:** Fix · **Difficulty:** ★★★★☆

## The story

The restaurant screen shows the cached menu instantly, then the fresh one. Problems:
- StrictMode screams: **disk reads on the main thread**.
- On a flaky network the screen gives up after **one** failed request. Product wants 3 attempts with backoff.
- Crashlytics shows a strange crash: `IllegalStateException: Flow exception transparency is violated`.
  It happens when the *screen's own code* throws while rendering a menu (a UI bug). That UI bug should crash
  with its own stack trace, not something confusing from the repository.
- Sometimes the screen shows "Offline", although the user just **left the screen**.

Your file: [`MenuRepository.kt`](MenuRepository.kt)

## Your task
- All `MenuCache` calls run on the injected `io` dispatcher, never on the collector's thread.
- Emit `Cached` **once**, first. Then `Fresh` on success.
- Network: up to `MAX_NETWORK_ATTEMPTS` attempts **for `IOException` only**, waiting `RETRY_DELAY_MS`, then double that, between attempts.
  Retrying must **not** re-read or re-emit the cache.
- If the network still fails, emit `Offline(cachedMenu, exception.message)`.
- An exception thrown by the **collector** must reach the collector unchanged. It is never caught, never turned into `Offline`.
- Cancellation is not a failure: no `Offline` after the collector is cancelled.
- A non-IO failure (a bug, e.g. `IllegalStateException` from parsing) is **not** retried. It also becomes `Offline` with its message.

## Rules
- Don't edit the test file. Keep the public API.
- No AI.

## Run
```bash
./dojo F05
```

## Before asking for a review, write in `JOURNAL.md`
1. What is "exception transparency"? Why is `try { emit(x) } catch` inside `flow { }` illegal?
2. Where does `flowOn` apply: upstream or downstream? What did you put it after, and why?
3. `catch` operator vs try/catch in the collector: which exceptions does each one see?
4. `retry` vs `retryWhen`: how did you retry **only** the network part?
5. Why can't you just call `withContext(io) { emit(...) }` inside `flow { }`? (Try it and read the error.)

## Hints
<details><summary>Hint 1</summary>

`emit()` runs the **downstream** code (the collector) right there, inside your try block.
So your `catch` also catches the collector's exceptions.
</details>

<details><summary>Hint 2</summary>

Build it from smaller flows. The network part as its own flow, with its own retry and `catch`.
The cached part as another. Then compose them (e.g. `emit(...)` + `emitAll(...)`), and use `flowOn` for the dispatcher.
</details>

<details><summary>Hint 3</summary>

`retryWhen { cause, attempt -> ... }` lets you `delay` and decide. `catch { }` only sees **upstream** exceptions,
which is exactly the transparency you want.
</details>
