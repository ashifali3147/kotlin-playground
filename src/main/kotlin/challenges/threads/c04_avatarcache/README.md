# T04 · The Avatar That Downloads 40 Times 🐛

**Track:** Threads · **Type:** Fix · **Difficulty:** ★★★★☆

## The story

The backend team pinged you: *"Why does your app request the same avatar 40 times in one second?"*
When a group chat opens, every message row asks for the sender's avatar. 40 rows from the same
person means 40 downloads of the same image, all at once. The CDN bill doubled.

A teammate tried a fix: wrap `get()` in `synchronized`. Duplicate downloads stopped, but the
chat screen now loads avatars **one by one**, like a slideshow. QA rejected it.

Your file: [`AvatarCache.kt`](AvatarCache.kt)

## Your task
Make `AvatarCache` correct and fast:
- **Exactly one** load per user, however many threads ask at the same moment. Everyone gets the *same* `Avatar` instance.
- Loads for **different** users must run **in parallel**, never waiting on each other.
- If a load fails, every caller waiting for it gets the original `AvatarLoadException` (not a wrapper),
  nobody hangs, and the failure is **not** cached: the next `get()` tries again.

## Rules
- Don't edit the test file. Keep the public API (private members and helper classes are fine).
- No AI.

## Run
```bash
./dojo T04
```

## Before asking for a review, write in `JOURNAL.md`
1. This bug has a name: "check-then-act". Where did you meet it before (T01? T03?), and why is it the same bug?
2. Why does `synchronized` around the whole `get()` hurt so much here, when it was fine in T01?
3. If you used `ConcurrentHashMap`: what does `computeIfAbsent` do while its function is running?
   Read its Javadoc carefully. Why does that matter when the function takes 2 seconds?
4. Classic interview question: the *double-checked locking* singleton. Write it from memory.
   Why does it need `@Volatile`? What does Kotlin's `by lazy` do instead?

## Hints
<details><summary>Hint 1</summary>

The second `synchronized` attempt failed because it holds a lock **during the slow part**.
What if threads only needed to agree on *who* loads a user, quickly, and then waited on *that load*
instead of on a lock?
</details>

<details><summary>Hint 2</summary>

`ConcurrentHashMap.computeIfAbsent(key) { loader(key) }` looks perfect and passes some tests.
Run the "different users" test with it and read the thread dump. The map locks more than one key's worth of data.
</details>

<details><summary>Hint 3</summary>

Cache a *promise* of an avatar, not the avatar itself: something like `FutureTask` or `CompletableFuture`.
Putting a promise into the map is instant. The slow work happens after it's in the map and outside any lock.
This is the "Memoizer" pattern from *Java Concurrency in Practice*, ch. 5.6.
Then think about what to do with a promise that failed.
</details>
