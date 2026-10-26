# K07 · HTTP 429: Too Many Uploads 🐛

**Track:** Coroutines · **Type:** Fix · **Difficulty:** ★★★☆☆

## The story

Customers can attach up to 200 photos to a review. The CDN allows at most **4 uploads at once** per device,
and above that it answers `429 Too Many Requests`. The author knew this and "limited" it:
`Dispatchers.IO.limitedParallelism(4)`. Code review approved it.

In production, the CDN logs show **dozens** of parallel uploads from a single phone.
The progress bar also misbehaves: sometimes it stops at "197 / 200" although every photo was uploaded.

Your file: [`PhotoUploader.kt`](PhotoUploader.kt)

## Your task
- Never more than `MAX_PARALLEL_UPLOADS` uploads in flight. Still use all 4 slots, so it isn't slower than necessary.
- `onProgress` reports every count from 1 to N exactly once, and the final count is reached.
- Result URLs are in the same order as the input.
- If one upload fails, `uploadAll` throws and the remaining uploads are cancelled (keep this behaviour).

## Rules
- Don't edit the test file. Keep the public API.
- No AI.

## Run
```bash
./gradlew test --tests "challenges.coroutines.K07*"
```

## Before asking for a review, write in `JOURNAL.md`
1. What exactly does `limitedParallelism(4)` limit? Why doesn't it limit *suspended* coroutines?
2. When *is* `limitedParallelism` the right tool? (Think: a blocking JDBC driver, or confining state to one thread.)
3. Why did `done++` lose updates? Which threads were running it?
4. Compare three ways to limit how much runs at once: `Semaphore`, a fixed number of worker coroutines reading from a `Channel`,
   and `chunked(4)` + `awaitAll`. Which one wastes time, and why?
5. What does `Semaphore.withPermit` guarantee if the block throws or is cancelled?

## Hints
<details><summary>Hint 1</summary>

A dispatcher limits **threads**. While a coroutine is suspended inside `upload()`, it isn't using a thread,
so the dispatcher happily starts another one.
</details>

<details><summary>Hint 2</summary>

You need to limit **coroutines in a section of code**, not threads. `kotlinx.coroutines.sync` has the tool.
</details>

<details><summary>Hint 3</summary>

For the progress: either make the counter thread-safe, or make sure only one thread ever touches it.
`chunked(4)` passes the limit test but fails the "use all 4 slots" one. Do you see why?
</details>
