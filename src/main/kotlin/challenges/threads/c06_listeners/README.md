# T06 · The Listener That Crashes on a Single Thread 🐛

**Track:** Threads · **Type:** Fix · **Difficulty:** ★★★☆☆

## The story

Crashlytics' top crash this week: `ConcurrentModificationException` in `ConnectivityMonitor.dispatch`.

A teammate's analysis: *"It's a threading bug, so I'll make everything `synchronized`."*
Then the strange part. One of the crash reports came from a device where **only the main thread**
touched the monitor. And a second teammate warned: *"careful, last time we put `synchronized`
around listener callbacks, the app froze in an ANR."*

Your file: [`ConnectivityMonitor.kt`](ConnectivityMonitor.kt)

## Your task
Make `ConnectivityMonitor` safe for every real-world usage pattern:
- A listener may **remove itself** (one-shot listener) or **add another listener** inside its callback.
- Listeners added **during** a dispatch get the **next** event, not the current one.
- Any thread may add, remove or dispatch at any time. No crash, and no lost add/remove.
- Listener callbacks are foreign code: they may be slow, or call back into the monitor from *another* thread.
  `dispatch` must never let that freeze the app.

Some tests are already green. They're there to make sure your fix doesn't break something else.

## Rules
- Don't edit the test file. Keep the public API.
- No AI.

## Run
```bash
./gradlew test --tests "challenges.threads.C06*"
```

## Before asking for a review, write in `JOURNAL.md`
1. How can `ConcurrentModificationException` happen with **one** thread? What is the `modCount` check actually protecting?
2. What is an "alien method call", and why is calling one while holding a lock dangerous?
3. `CopyOnWriteArrayList` vs `Collections.synchronizedList` vs "copy under lock, iterate outside":
   compare the cost of reads, writes and memory. Which one fits a listener list, and why?
4. On Android, where have you used this pattern without knowing it? (`LiveData` observers, `View` listeners, `BroadcastReceiver`s...)

## Hints
<details><summary>Hint 1</summary>

Run the single-thread test first and read the stack trace. The iterator throws, not the `remove`.
What does a `for` loop over a `MutableList` compile into?
</details>

<details><summary>Hint 2</summary>

The fix for the single-thread crash and the multi-thread crash is the same idea:
**iterate over something that can't change underneath you.**
</details>

<details><summary>Hint 3</summary>

If you lock while dispatching, ask yourself: what is the listener allowed to do while I hold that lock?
Either take a snapshot under the lock and call the listeners *after* releasing it, or look for
a `java.util.concurrent` list that is designed for "many reads, few writes, iterate a snapshot".
</details>
