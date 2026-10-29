# T08 · Build a Connection Pool 🏗

**Track:** Threads · **Type:** Build · **Difficulty:** ★★★★☆ · *Track 1 final*

## The story

The order service opens a new database connection for every request. The database allows
**20 connections**. During the lunch rush, 300 requests arrive at once and the database starts refusing
connections. The whole app goes down.

You're asked to build a connection pool. There's no library allowed, because this service is a
tiny sidecar that has to stay dependency-free. Tech lead's words: *"It's 50 lines. It's also
where most people introduce three bugs."*

Your file: [`ConnectionPool.kt`](ConnectionPool.kt). All methods are `TODO()`.

## Spec
1. **Bounded:** never more than `maxConnections` connections in use at the same time.
2. **Lazy:** only call `factory` when there is no idle connection and fewer than `maxConnections` exist.
   Never create more than `maxConnections` in total.
3. **Reuse:** a released connection is handed out again.
4. **Timeout:** `acquire(timeoutMs)` waits up to `timeoutMs` for a free connection, then throws
   `PoolExhaustedException`. Waiting must not burn CPU.
5. **Strict release:** releasing a connection that isn't currently checked out from *this* pool
   (released twice, or from another pool) throws `IllegalArgumentException` and changes nothing.
6. **Always released:** `withConnection` returns `block`'s result and releases the connection
   even if `block` throws.
7. **Factory failures don't leak:** if `factory` throws, `acquire` throws that same exception,
   and the pool's capacity isn't reduced.
8. **Slow factory doesn't block the pool:** while one thread is inside `factory` (a 2s handshake),
   other threads can still `release` and `acquire` *idle* connections.
9. **Close:** `close()` closes idle connections. Connections in use are closed when they're released.
   `acquire` after `close()` throws `IllegalStateException`.

## Rules
- Don't edit the test file. Keep the public API.
- You **may** use anything in `java.util.concurrent` (unlike T05).
- No AI.

## Run
```bash
./dojo T08
```

## Before asking for a review, write in `JOURNAL.md`
1. Which concurrency tool did you pick for "at most N", and why? What else could have worked?
2. Spec 7 and spec 8 pull against each other. Explain the tension and how you resolved it.
3. Find one race your tests *don't* catch (hint: `close()` while another thread is in `release()`). Is it a real problem?
4. How does this map to coroutines? (`Semaphore` in `kotlinx.coroutines.sync`, and what `withPermit` gives you.)

## Hints
<details><summary>Hint 1</summary>

Split the problem in two: **"am I allowed to hold a connection?"** (a counting problem) and
**"which connection do I get?"** (a storage problem). They don't need the same tool.
</details>

<details><summary>Hint 2</summary>

A `Semaphore(maxConnections)` answers the first question, including the timeout. For the second, you need
an idle stack/queue and a way to know which connections are currently checked out.
</details>

<details><summary>Hint 3</summary>

Every place you take a permit, ask: *"on which code path could I fail to give it back?"*
`try`/`finally` is your friend. Never call `factory` while holding a lock.
</details>
