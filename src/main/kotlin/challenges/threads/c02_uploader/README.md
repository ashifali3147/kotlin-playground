# T02 · The Rider App That Never Stops Uploading 🐛

**Track:** Threads · **Type:** Fix · **Difficulty:** ★★★☆☆

## The story

Riders are complaining that the app **drains their battery** and uses mobile data even after
they've finished a delivery and closed the screen. Android Vitals shows excessive background CPU.

A teammate tested it with a debug build: they added
`println("uploaded $uploadedFixes")` inside the loop to watch it, and... **the bug went away.**
The thread stopped perfectly. They removed the log, shipped the build, and the bug came back.

Your file: [`LocationUploader.kt`](LocationUploader.kt)

## Your task
Make `stop()` actually stop the worker, and make it honour its contract:
**once `stop()` returns, no more fixes are uploaded, and the number it returned is final.**
`stop()` may be called more than once (Android lifecycles are messy), and it must not crash.

## Rules
- Don't edit the test file. Keep the public API. Keep the worker loop as a loop (don't swap it for an executor or coroutines; that's a later challenge).
- No AI.

## Run
```bash
./gradlew test --tests "challenges.threads.C02*"
```

## Before asking for a review, write in `JOURNAL.md`
1. Why does the loop keep running even though another thread clearly set `isRunning = false`?
   Who is "allowed" to make that happen: the CPU, the JIT compiler, or both?
2. **Why did adding a `println` make the bug disappear?** (Go and read the source of `println`. Seriously.)
   Why is "adding a log fixes it" a huge red flag in concurrent code?
3. What does "happens-before" mean? Name 3 actions in Java/Kotlin that create one.
4. Is `@Volatile var count; count++` thread-safe? Why or why not? (Link this to T01.)

## Hints
<details><summary>Hint 1</summary>

The worker thread never writes `isRunning`, and nothing in the loop tells the JVM it must
look at main memory again. So what is the JIT allowed to do with `while (isRunning)`?
Try rewriting the loop by hand as the compiler might "optimize" it.
</details>

<details><summary>Hint 2</summary>

There are two separate problems. The first is **visibility**: the worker doesn't see the flag change.
The second is **ordering/completion**: `stop()` returns while the worker might still be
mid-iteration. Fixing the first one turns a red test green, and the second test stays red.
</details>

<details><summary>Hint 3</summary>

Look up the `@Volatile` annotation, and look at what `Thread.join()` guarantees about
memory visibility, not just about waiting. You'll need to keep a reference to something you currently throw away.
</details>
