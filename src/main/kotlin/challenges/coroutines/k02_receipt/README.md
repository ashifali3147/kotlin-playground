# K02 · "But It's a Suspend Function!" 🐛

**Track:** Coroutines · **Type:** Fix · **Difficulty:** ★★☆☆☆

## The story

After checkout, the "Order placed!" animation **freezes for almost a second**. On slow phones
users get an ANR dialog. Play Console marks the app as having "bad behaviour".

The dev who wrote `ReceiptGenerator` is confused: *"It's a `suspend` function and I call it
from a coroutine. Coroutines run in the background, right?"*

Your file: [`ReceiptGenerator.kt`](ReceiptGenerator.kt)

## Your task
Make `generate()` **main-safe**: it must be safe to call from the main thread, without ever blocking it,
for both the blocking disk I/O and the CPU-heavy rendering. It must still work when called from any other thread.

## Rules
- Don't edit the test file or `ReceiptStorage` (it's a legacy library). Keep the public API.
- No AI.

## Run
```bash
./dojo K02
```
The tests use a fake single-threaded "main" dispatcher and count animation frames, like Choreographer does.

## Before asking for a review, write in `JOURNAL.md`
1. Does marking a function `suspend` move it to a background thread? What does `suspend` actually mean?
2. `Dispatchers.IO` vs `Dispatchers.Default`: what's each for, and why do they have different thread limits?
   Which one did you use for which part, and why?
3. What is the "main-safety" convention, and whose job is it: the ViewModel's or the repository's?
4. Interview classic: what's wrong with calling `runBlocking` on the main thread? Can it deadlock? How?
5. In production code, why do people inject dispatchers (`ioDispatcher: CoroutineDispatcher`) instead of using `Dispatchers.IO` directly?

## Hints
<details><summary>Hint 1</summary>

A coroutine runs on whatever thread its dispatcher gives it, and stays there until something moves it.
Nothing in `generate()` moves anything.
</details>

<details><summary>Hint 2</summary>

`withContext(...)` switches the dispatcher for a block of code, then switches back.
</details>

<details><summary>Hint 3</summary>

Blocking disk calls and a CPU-crunching loop are two *different* kinds of work. One dispatcher is sized
for waiting, the other for computing.
</details>
