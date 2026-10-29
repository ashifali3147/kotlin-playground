# K08 · GPS That Never Turns Off 🐛

**Track:** Coroutines · **Type:** Fix · **Difficulty:** ★★★☆☆

## The story

The location SDK has a callback API, and someone wrapped it in a suspend function. Since then:
- Battery complaints: **GPS stays on** after the user leaves the "restaurants near me" screen.
- The "near me" screen sometimes waits **forever**, even though there's a 3-second timeout.
- Crashlytics: `IllegalStateException: Already resumed` on some Xiaomi and Samsung devices.
- When GPS is disabled, the SDK reports an error, but the screen silently shows the default city instead of "Please enable GPS".

Your file: [`LocationRepository.kt`](LocationRepository.kt)

## Your task
`currentLocation()`:
- Returns the location, or throws the SDK's error.
- If the caller is cancelled: the SDK request is cancelled (GPS off), and the coroutine stops right away, without waiting for the SDK.
- Survives the SDK bugs: a double `onLocation`, or a callback arriving after cancellation, must not crash.

`currentLocationOrNull(timeoutMs)`:
- Returns `null` **only** on timeout, and the SDK request is cancelled.
- SDK errors are thrown. Caller cancellation stays a cancellation (no `null` returned to a dead screen).

## Rules
- Don't edit the test file. Keep the public API. You can't change `LocationClient`.
- No AI.

## Run
```bash
./dojo K08
```

## Before asking for a review, write in `JOURNAL.md`
1. `suspendCoroutine` vs `suspendCancellableCoroutine`: what's the difference, and why did the timeout never fire?
2. What does `invokeOnCancellation` give you? On which thread can it run?
3. Why does resuming twice crash? How did you guard against it, and is your guard thread-safe? (The callback runs on the SDK's thread.)
4. `withTimeout` vs `withTimeoutOrNull`: what does each throw or return? Why is `TimeoutCancellationException` a `CancellationException`, and why does that matter?
5. For callbacks that fire **many** times (location updates every second), what would you use instead? (Coming up in the Flow track.)

## Hints
<details><summary>Hint 1</summary>

A coroutine suspended in `suspendCoroutine` can't be cancelled. It only wakes up when someone resumes it.
That's why the timeout and screen exit had no effect.
</details>

<details><summary>Hint 2</summary>

`suspendCancellableCoroutine { cont -> ... cont.invokeOnCancellation { ... } }`. What should happen in there?
</details>

<details><summary>Hint 3</summary>

For double callbacks: check `cont.isActive` before resuming, or use an atomic "already delivered" flag
(think about which one is race-free across threads). For the timeout: there's a builder that returns `null` instead of throwing.
</details>
