# K05 · One Bad Photo Breaks the Whole Menu 🐛

**Track:** Coroutines · **Type:** Fix · **Difficulty:** ★★★☆☆

## The story

The restaurant page prefetches all menu photos. Reports from QA:
- If **one** restaurant photo is broken (404), the **whole prefetch crashes** and none of the other
  photos load. The author even added a `CoroutineExceptionHandler` "to catch errors". It doesn't seem to do anything.
- The analytics event "prefetch finished: 0 succeeded, 0 failed" fires for **every** restaurant,
  even when all photos download fine.

Your file: [`MenuImagePrefetcher.kt`](MenuImagePrefetcher.kt)

## Your task
- Download all photos **in parallel**.
- A failing download must not affect the others. `prefetch()` doesn't throw because of it: it reports the URL in `failed`.
- The report is complete and accurate: every URL is in exactly one list, **in the same order as the input**.
- If the *caller* is cancelled, all downloads are cancelled and `prefetch()` doesn't return a report.

## Rules
- Don't edit the test file. Keep the public API.
- No AI.

## Run
```bash
./dojo K05
```

## Before asking for a review, write in `JOURNAL.md`
1. Draw the job tree: what happens to the parent and the siblings when a child of `coroutineScope` fails?
   And under `supervisorScope`?
2. **Where** does a `CoroutineExceptionHandler` actually work? Why was it ignored here?
3. `launch` vs `async`: how does each one deliver its exception? When does an `async` failure appear?
4. Why was the report always empty? When does `coroutineScope` wait for its children: before or after evaluating the last expression?
5. Why does `awaitAll()` inside `supervisorScope` still throw on the first failure?

## Hints
<details><summary>Hint 1</summary>

Two separate bugs: **failure propagation** (one child takes everyone down) and **timing** (the report is built too early).
</details>

<details><summary>Hint 2</summary>

`CoroutineExceptionHandler` only applies to *root* coroutines, or to direct children of a `supervisorScope`/`SupervisorJob`.
A child of `coroutineScope` passes its exception to its parent instead.
</details>

<details><summary>Hint 3</summary>

You want each download to produce a *result* (success or failure) that you can collect after *all* of them finish.
`async` returns a value. But think about what `await()` does with an exception, and how not to lose the input order.
</details>
