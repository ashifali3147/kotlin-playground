# T07 · The Export Button That Kills the Server 🐛

**Track:** Threads · **Type:** Fix · **Difficulty:** ★★★★☆

## The story

The partner dashboard has an "Export weekly report" button. Two problems reached you:

1. **Ops:** *"The dashboard service's thread count climbs all day: 2,000... 6,000... then
   `OutOfMemoryError: unable to create native thread`. We restart it every night."*
2. **Support:** *"Big restaurant chains say Export spins forever. Small restaurants are fine."*
   Big chains have many sections in their report. The server isn't busy: CPU is near 0% while it "spins".

Your file: [`ReportExporter.kt`](ReportExporter.kt)

## Your task
- No thread leaks. After `close()`, every thread the exporter created must finish.
- `export` must complete for **any** number of sections and charts.
- Keep it parallel and bounded: charts must still render in parallel, and the exporter must
  never have more than **8 threads** alive, however many sections or exports there are.
- `export` after `close()` must throw `IllegalStateException`.
- Every thread you create must use `namedThreads(...)` so the tests can see it.

## Rules
- Don't edit the test file. Keep the public API.
- No AI.

## Run
```bash
./gradlew test --tests "challenges.threads.C07*"
```
Read the thread dump from the "many sections" test. Look at **what** each pool thread is waiting for.

## Before asking for a review, write in `JOURNAL.md`
1. Why didn't the JVM clean up the leaked pools on its own? What keeps an idle pool thread alive?
2. Explain "thread-pool starvation deadlock" in two sentences. Why was CPU at 0%?
3. `shutdown()` vs `shutdownNow()` vs `awaitTermination()`: what does each one do?
4. Why is `Executors.newCachedThreadPool()` **not** a real fix, even though the freeze goes away?
5. How do coroutines avoid this whole class of bug? (Think: what does `await()` do to the thread, compared with `Future.get()`?)

## Hints
<details><summary>Hint 1</summary>

Two separate bugs. Ops' bug is about the pool's **lifetime**: who owns it, and who shuts it down?
Support's bug is about **tasks waiting for other tasks in the same pool**.
</details>

<details><summary>Hint 2</summary>

With 4 threads and 6 sections: 4 section tasks start, and each one submits chart tasks and then blocks on `get()`.
Where are the chart tasks? Who is going to run them?
</details>

<details><summary>Hint 3</summary>

Rule of thumb: **never block a pool thread waiting for work submitted to that same pool.**
Options: separate pools for different levels of task, or don't block at all
(`CompletableFuture` composition). Each has trade-offs, so pick one and be ready to defend it.
</details>
