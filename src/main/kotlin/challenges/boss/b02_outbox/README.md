# B02 · 🔥 Incident: Duplicate Orders 🐉

**Track:** Boss · **Type:** Production incident · **Difficulty:** ★★★★★

> **This file has 5 bugs.** The tests are your monitoring dashboard. Each one is a symptom, not a bug.

## Incident report: INC-2307 "Customers charged twice"

Severity: **P0**. Restaurants are cooking the same order twice, and customers are charged twice.

| Source | Signal |
|---|---|
| Orders backend | The **same order placed twice**, a few hundred ms apart. Clustered on trains and in elevators (flaky connectivity) |
| Restaurant partners | *"Got a CANCEL for an order we never received"*. It arrived **before** the PLACE_ORDER |
| Support | *"My order sat on 'waiting to sync' for 20 minutes while I had perfect Wi-Fi"* |
| Support | *"The badge says 2 changes waiting, but I made 3"* |
| Analytics | After one failed request, **nothing** syncs until the user toggles airplane mode |

Your file: [`OutboxSyncEngine.kt`](OutboxSyncEngine.kt)

## Expected behaviour (the spec)
1. `enqueue` is safe from any thread. `pendingCount` always equals the number of unsent actions.
2. While online, an enqueued action is sent **right away**. Nobody waits for a connectivity change.
3. While offline, nothing is sent. When the device comes online, pending actions are sent.
4. **No duplicates:** an action is never being sent twice at the same time, and never re-sent after it succeeded.
5. **Per-order ordering:** actions for the same order are sent one after another, in enqueue order.
   The next one only starts after the previous one succeeded. Actions for *different* orders sync in parallel.
6. **Retry:** a failed send (`IOException`) stays in the outbox and is retried while online, with backoff
   `RETRY_INITIAL_MS`, ×2 each time, capped at `RETRY_MAX_MS`. No attempts while offline.
   Coming back online retries immediately.

## Rules
- Don't edit the test file. Keep the public API.
- No AI. You've done every piece of this: T01, T05, K03, K04, K06, K09, F01.

## Run
```bash
./dojo B02
```

## Debrief (write in `JOURNAL.md` before asking for a review)
1. List the 5 bugs: symptom → root cause → earlier challenge.
2. Spec 4: what exactly happened on a train that produced a duplicate order? Draw the timeline.
3. Your design: how many coroutines run while online and offline, who wakes up whom, and what protects the outbox?
4. Going offline in the middle of a `send`: what did you do with the in-flight request, and why is that safe (or not)?
   What would the server need to provide so it's *always* safe? (Look up "idempotency keys".)
5. How would you make this survive **process death**? (Room + WorkManager: what does each give you?)

## Hints
<details><summary>Hint 1: triage order</summary>

Thread safety of `enqueue` → one sync loop instead of many → what wakes the loop → per-order ordering → retry/backoff.
</details>

<details><summary>Hint 2: the shape of a fix</summary>

One long-running sync loop that exists only while online (think `collectLatest` on connectivity).
It sends the *head* action of each order in parallel, and sleeps when there's nothing to do until `enqueue` wakes it.
</details>

<details><summary>Hint 3: waking up</summary>

A `Channel(Channel.CONFLATED)` used as a "doorbell": `enqueue` does `trySend(Unit)`, and the idle loop `receive()`s.
Ask yourself why a lost doorbell ring can't happen with `CONFLATED`.
</details>
