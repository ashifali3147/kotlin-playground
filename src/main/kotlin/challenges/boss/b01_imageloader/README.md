# B01 · 🔥 Incident: The Image Loader 🐉

**Track:** Boss · **Type:** Production incident · **Difficulty:** ★★★★★

> **This file has 6 bugs.** They come from every track: threads, coroutines and cancellation.
> The tests are your monitoring dashboard. Each one is a symptom, not a bug. One bug can break several tests,
> and fixing one bug can turn another test red.

## Incident report: INC-2291 "Menu screen images"

Severity: **P1**. Opened by: on-call. Affected: all Android users since 4.12.

| Source | Signal |
|---|---|
| Crashlytics | `ConcurrentModificationException` / `NullPointerException` inside `LinkedHashMap` while scrolling fast |
| Play Vitals | ANR rate up 3×. StrictMode: **disk read on main thread**, **slow decode on main thread** |
| CDN bill | The same image is downloaded **dozens of times** when a menu opens |
| CDN rate limiter | `HTTP 429`: one device opened **40+ connections** at once |
| Support | *"Some dishes never show an image again, even after a network blip, until I kill the app"* |
| Data usage | Fast-scrolling a 500-item menu downloads **everything**, including rows the user scrolled past in milliseconds |

Your file: [`ImageLoader.kt`](ImageLoader.kt)

## Expected behaviour (the spec)
1. Memory hit → return instantly. Disk hit → no network. Otherwise download → write to disk → decode → memory.
2. `MemoryCache` is safe under any number of threads and never exceeds `maxEntries`.
3. Many concurrent `load(url)` calls for the same URL → **one** download, **one** decode, the **same** `Bitmap` instance.
4. Disk work runs on `io`, decoding on `cpu`, never on the caller's thread.
5. At most `MAX_PARALLEL_DOWNLOADS` downloads at once. Disk hits never wait for a download slot.
6. A failed load: every waiter gets the error, and the **next** `load` tries again.
7. A recycled row (cancelled caller) must not hurt other rows waiting for the same image.
   If **every** caller waiting for an image is cancelled, its download is **cancelled** too.

## Rules
- Don't edit the test file. Keep the public API.
- No AI. You've done every piece of this before: T01, T04, K02, K03, K05, K07.

## Run
```bash
./dojo B01
```

## Debrief (write in `JOURNAL.md` before asking for a review)
1. List the 6 bugs. For each: the symptom it caused, the root cause, and which earlier challenge it maps to.
2. Spec 7 (reference counting) is the hardest part. Explain your design, and the race you had to think about.
3. Which state is touched from several threads, and what protects each piece?
4. Write it as a real post-mortem: what monitoring or tests would have caught this before release?

## Hints
<details><summary>Hint 1: triage order</summary>

Fix the crashes first (thread safety), then correctness (dedup, failures), then resources (dispatchers, limits), then cancellation.
</details>

<details><summary>Hint 2: the 6 areas</summary>

LRU thread safety · in-flight map check-then-act · dispatchers · download limit · failure cleanup · who owns the shared download job.
</details>

<details><summary>Hint 3: spec 7</summary>

The shared download can't live in a caller's scope (cancel one caller, kill everyone) or in `GlobalScope` (nobody can cancel it).
Run it in a scope you own, and count the waiters. The last one to leave cancels it.
</details>
