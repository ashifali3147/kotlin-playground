# K01 · The Home Screen That Ignores You 🐛

**Track:** Coroutines · **Type:** Fix · **Difficulty:** ★★☆☆☆

## The story

The home screen makes three API calls: profile (~300ms), offers (~500ms) and recent orders (~400ms).
The previous dev said: *"I used `async`, so it's parallel."* But:

- The home screen takes noticeably longer than the slowest call.
- When the user leaves the screen, the network inspector shows the requests **still running**.
- When `offers()` fails, the user sees the error right away, yet the other two requests keep downloading
  data that nobody will ever see.

Your file: [`HomeRepository.kt`](HomeRepository.kt)

## Your task
- Load all three **in parallel**: total time ≈ the slowest call.
- If the caller is cancelled (the user left the screen), **all** in-flight requests are cancelled.
- If any call fails, `loadHome()` throws that exception and the **other calls are cancelled**.

## Rules
- Don't edit the test file. Keep the public API.
- `HomeApi` is already main-safe (like Retrofit), so you don't need to switch dispatchers.
- No AI.

## Run
```bash
./dojo K01
```
The tests use `runTest`, which runs on **virtual time**: `delay(500)` finishes instantly, but
`currentTime` says 500. Read about `kotlinx-coroutines-test` if this is new. You'll use it in every interview take-home.

## Before asking for a review, write in `JOURNAL.md`
1. What is "structured concurrency"? Explain it as you would to a junior dev, in 3 sentences.
2. Why is `GlobalScope` marked `@DelicateCoroutinesApi`? Whose job is cancelled when the ViewModel is cleared, and why isn't a `GlobalScope` coroutine one of them?
3. Why is `async { ... }.await()` on the same line pointless?
4. `coroutineScope` vs `supervisorScope`: which one fits here, and why? (You'll meet the other in K05.)
5. Why did one test report `currentTime == 0` with the original code?

## Hints
<details><summary>Hint 1</summary>

There are two different problems: **when** you call `await()`, and **which scope** the `async` children belong to.
</details>

<details><summary>Hint 2</summary>

Children launched in `GlobalScope` have no parent. Nothing cancels them, and their failures cancel nothing.
You need a scope that *is a child of the caller*, and waits for all its children.
</details>

<details><summary>Hint 3</summary>

Look at the `coroutineScope { }` builder. What does it do when one child fails? When the caller is cancelled?
</details>
