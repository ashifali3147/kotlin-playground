# F03 · The Snackbar That Shows Twice (or Never) 🐛

**Track:** Flow · **Type:** Fix · **Difficulty:** ★★★☆☆ · *A very common Android interview question*

## The story

QA filed three bugs against the cart screen:
1. Add "Naan" twice quickly. The snackbar *"Naan added to cart"* appears only **once**.
2. Tap Checkout, then rotate the phone on the checkout screen and press back. The app **navigates to checkout again** by itself.
3. Tap "Add" and quickly lock the phone. When you unlock it, the snackbar **never appears**.

A teammate's attempt with `MutableSharedFlow()` fixed bug 1 and made bug 3 worse.

Your file: [`CartViewModel.kt`](CartViewModel.kt)

## Your task
Make `events` a correct one-shot event stream:
- Every event is delivered **exactly once**, including two identical events in a row.
- Events sent while **nobody is collecting** (rotation, background) are kept and delivered when a collector comes back.
- An event that was already delivered is **never** delivered again to a new collector.
- Order is preserved, even when the collector is slow.
- `onItemAdded` / `onCheckoutClicked` stay regular (non-suspend) functions.

## Rules
- Don't edit the test file. Keep the public API.
- No AI.

## Run
```bash
./dojo F03
```
These tests use [Turbine](https://github.com/cashapp/turbine) (`flow.test { awaitItem() }`). It's worth learning: you'll see it in every modern Android codebase.

## Before asking for a review, write in `JOURNAL.md`
1. Why is `StateFlow` wrong for events? Name both problems it caused.
2. Why did `MutableSharedFlow()` (replay = 0) lose events? What about `replay = 1`?
3. Why does a `Channel` fit "deliver once, buffer until someone collects"? What happens with **two** collectors?
4. `trySend` vs `send`: which one did you use, why, and can it fail here?
5. The Android community debate: Google's guidance says *"model events as state"* (e.g. a `userMessages` list in UI state that the UI acknowledges).
   What problem does that solve that a `Channel` doesn't? (Hint: process death, and the tiny window during `repeatOnLifecycle` cancellation.)

## Hints
<details><summary>Hint 1</summary>

`StateFlow` holds **state**: it conflates equal values and replays the current value to every new collector. Events are not state.
</details>

<details><summary>Hint 2</summary>

`SharedFlow` with no subscribers drops emissions, no matter how big its buffer is. You need something that **queues** items until someone takes them.
</details>

<details><summary>Hint 3</summary>

`Channel(Channel.BUFFERED)` plus `receiveAsFlow()`. Then think about how to send into it from a non-suspend function.
</details>
