# T01 · The Analytics Numbers Don't Add Up 🐛

**Track:** Threads · **Type:** Fix · **Difficulty:** ★★☆☆☆ (stretch goal: ★★★★☆)

## The story

You've just joined a food-delivery startup. Product says the dashboards are lying:
the app reports **fewer** `screen_view` events than the backend's server logs. It's off by 3–8%,
and worse on low-end phones and during busy hours. Once, a Crashlytics report showed a
`ConcurrentModificationException` coming from the debug screen.

The previous dev said: *"It works fine when I test it."*

Your file: [`AnalyticsTracker.kt`](AnalyticsTracker.kt)

## Symptoms
- Totals come out lower than the number of events actually tracked
- Sometimes events vanish around the moment the upload job runs
- Occasional crash when the debug screen calls `snapshot()`

## Your task
Make `AnalyticsTracker` correct when **any number of threads** call any method at the same time.

## Rules
- Don't edit the test file. Don't change any public function signatures.
- No AI. Docs, the Kotlin/Java source code and your own experiments are all allowed.

## Run
```bash
./gradlew test --tests "challenges.threads.C01*"
```
You're done when it's all green, **and you can explain why each test was failing.**

## Stretch goal (I'll check this in the review)
`track()` runs on the main thread and in every network call, so it is on the hot path.
A solution where every `track()` call waits on one big global lock passes the tests,
but it isn't the best one. Can you make `track()` mostly lock-free, while `drain()`
still **never loses or double-counts** an event?

## Before asking for a review, write in `JOURNAL.md`
1. Why is `count++` not safe, even though it's one line? What actually happens at the CPU/JVM level?
2. Why does making a field `@Volatile` **not** fix this?
3. What's wrong with `drain()` even if every single call inside it were thread-safe?

## Hints (open only after you've really tried, one at a time)
<details><summary>Hint 1</summary>

Write `lifetimeEvents++` out as the separate steps the machine performs.
What happens if two threads interleave between those steps?
</details>

<details><summary>Hint 2</summary>

There are **three** different problems hiding here: a lost increment, a non-thread-safe collection,
and a gap between two operations that should look like one. Fixing one doesn't fix the others.
Look at the `java.util.concurrent` and `java.util.concurrent.atomic` packages.
</details>

<details><summary>Hint 3</summary>

For `drain()`: "copy, then clear" is two steps. Events tracked between them are lost.
Either make those two steps happen as one, or think about *replacing* the whole batch
instead of clearing it. (For the stretch goal: what happens to a writer that grabbed the old batch just before you replaced it?)
</details>
