# K04 · The Tracker That Polls Forever 🐛

**Track:** Coroutines · **Type:** Fix · **Difficulty:** ★★★☆☆

## The story

The live order tracking screen polls the backend every 5 seconds. The backend team says that
**the number of polling requests per user keeps growing during a session**:
open the screen, background the app, come back, rotate the phone... and each time a *new* poller appears,
while the old ones never stop. Some phones poll for orders that were delivered hours ago.

Also: one flaky network call (`IOException`) kills tracking for good, and the screen stays stuck on "Preparing".

Your file: [`OrderTracker.kt`](OrderTracker.kt)

## Your task
- `stopTracking()` stops polling **immediately**. An in-flight request is cancelled, and `onUpdate` is never called after it.
- `startTracking()` called again (rotation, onStart twice) **never** creates a second poller.
  Starting with a different order id switches to tracking the new order.
- `startTracking()` after `stopTracking()` resumes polling.
- After `destroy()`, nothing ever runs again, even if `startTracking()` is called.
- A failed fetch (`IOException`) doesn't stop tracking: just try again at the next interval.
- Polling stops by itself once the order is `DELIVERED` (this already works, so keep it working).
- All polling must run on the injected `dispatcher`.

## Rules
- Don't edit the test file. Keep the public API.
- No AI.

## Run
```bash
./dojo K04
```

## Before asking for a review, write in `JOURNAL.md`
1. A `Job` vs a `CoroutineScope`: what's the difference, and which one does `launch` return?
2. Why `SupervisorJob()` for a scope you own? What goes wrong with a plain `Job()` after the first failure?
3. What does `viewModelScope` / `lifecycleScope` do for you that you had to do by hand here?
4. On Android, what would you use instead of start/stop calls? (Look up `repeatOnLifecycle`.)
5. What happens to a cancelled scope if you try to `launch` in it again?

## Hints
<details><summary>Hint 1</summary>

`GlobalScope.launch` returns something. Right now it's thrown away.
</details>

<details><summary>Hint 2</summary>

This class has a lifetime (until `destroy()`), so it should own a `CoroutineScope` with that lifetime.
Each tracking session is a `Job` inside it.
</details>

<details><summary>Hint 3</summary>

Catch only what you mean to recover from (`IOException`). Remember K03: a broad `catch` also catches cancellation.
</details>
