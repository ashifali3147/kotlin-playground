# K03 · The Cancel Button That Doesn't Cancel 🐛

**Track:** Coroutines · **Type:** Fix · **Difficulty:** ★★★☆☆

## The story

Restaurant partners upload a kitchen video. Bug reports:

1. *"I pressed Cancel, but the phone stayed hot for a minute."* Compression kept running.
2. *"I cancelled during upload, but my data usage shows the whole video was sent."*
3. *"After I cancelled, the app showed an 'Upload failed' dialog. I cancelled it, it didn't fail!"*
4. Support found gigabytes of leftover temp files on partners' phones.

Everyone assumed *"coroutines are cancellable, so cancel just works."*

Your file: [`VideoUploader.kt`](VideoUploader.kt)

## Your task
When the caller's coroutine is cancelled, at **any** point:
- compression stops promptly
- no more chunks are uploaded, and `finish()` is never called
- `compressAndUpload` does **not** return normally: no `Result` comes back, so no "Upload failed" dialog
- the temp file is **always** deleted

And keep the existing behaviour: a chunk that fails with a network error is still skipped,
and a real failure (not a cancellation) still comes back as `Result.failure`.

## Rules
- Don't edit the test file. Keep the public API (including the `Result` return type).
- No AI.

## Run
```bash
./dojo K03
```

## Before asking for a review, write in `JOURNAL.md`
1. "Cancellation is cooperative." What does that mean? Which code can be cancelled, and which can't?
2. How is cancellation *delivered* to a suspended coroutine? (What gets thrown, and where?)
3. Why is `runCatching { }` around suspend calls a well-known trap? Same question for `catch (e: Exception)`.
4. Why couldn't the `finally` block delete the temp file? What does `NonCancellable` change, and why should you use it *only* in cleanup code?
5. `ensureActive()` vs `yield()` vs checking `isActive`: what's the difference?

## Hints
<details><summary>Hint 1</summary>

There are four separate bugs, one for each line of the story. Make a test go red → green for each, one at a time.
</details>

<details><summary>Hint 2</summary>

`CancellationException` is an `Exception`. Anything that catches every `Exception` also catches the cancellation itself, and then carries on as if nothing happened.
</details>

<details><summary>Hint 3</summary>

A suspend call inside a cancelled coroutine throws immediately, and that includes calls inside `finally`.
Look up `withContext(NonCancellable)`. For the CPU loop: something has to check for cancellation between frames.
</details>
