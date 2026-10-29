# B03 · 🔥 Incident: The Tracking Screen 🐉

**Track:** Boss · **Type:** Production incident · **Difficulty:** ★★★★★ · *Final boss*

> **This file has 7 bugs.** Almost every Flow topic is in here, plus a few from the coroutine track.
> The tests are your monitoring dashboard. Each one is a symptom, not a bug.

## Incident report: INC-2340 "Live tracking"

Severity: **P1**. Live tracking is the most-used screen in the app.

| Source | Signal |
|---|---|
| Socket backend | **3 connections per user** on the tracking screen. Connections stay open **hours after delivery** |
| Socket backend | Reader threads stall. *All* live features (chat, rider location) freeze together on slow phones |
| Play Vitals | Janky map animation. StrictMode: **slow call on main thread** (`EtaCalculator`) |
| Support | *"Live tracking froze at 'Preparing' and never moved"*. It happens every time the socket drops |
| Support | *"I got the rating screen twice"*. It happens after rotating the phone |
| Support | *"I never got the 'rider is nearby' notification"*. The user had the app in the background |

Your file: [`TrackingViewModel.kt`](TrackingViewModel.kt)

## Expected behaviour (the spec)
1. `uiState` shows status, rider position and ETA for every update.
2. **One** socket connection per ViewModel, no matter how many collectors `uiState` has.
3. When `scope` is cancelled (screen gone), the socket is disconnected.
4. A socket callback **never blocks**. The UI only needs the latest position.
5. `EtaCalculator` never runs on the caller's/collector's thread. Use `cpu`.
6. If the socket fails: **poll** `api.latest` every `POLL_MS`, with `usingFallback = true`, until the order is delivered.
7. `RiderNearby` fires **once** per order, the first time ETA ≤ `NEARBY_MINUTES`, even if the ETA goes up and down afterwards.
8. Events are never lost (even with no collector), never duplicated, and never replayed to a new collector. Order is preserved.
9. On `DELIVERED`: emit `OpenRatingScreen` once, and stop tracking: disconnect the socket, or stop polling.

## Rules
- Don't edit the test file. Keep the constructor and the names `uiState` and `events`.
  Their types may become more specific (e.g. `StateFlow`), as long as they're still a `Flow` of the same element type.
- No AI. It's all from K02, K03, F01, F03, F05, F07, plus your own design sense.

## Run
```bash
./dojo B03
```

## Debrief (write in `JOURNAL.md` before asking for a review)
1. List the 7 bugs: symptom → root cause → earlier challenge.
2. Draw your final pipeline from the socket to `uiState` and `events`. Mark where each operator goes, and why.
3. How does tracking stop on `DELIVERED` in your design? Which operator completes the flow, and what runs `disconnect()`?
4. Sharing: which `SharingStarted` did you pick, and why? (Hint: who is always collecting here?)
5. If you rewrote this for production, what would you change in the *architecture* (not just the bugs)?

## Hints
<details><summary>Hint 1: triage order</summary>

Socket hygiene (cleanup, non-blocking) → share one upstream → fallback polling → ETA dispatcher → events → stopping on delivery.
</details>

<details><summary>Hint 2: fallback</summary>

`catch` runs once and the flow then completes. To *switch* to another stream, `emitAll` a polling flow from inside `catch`.
The fallback flag has to travel with each update. Pair it up before `catch`.
</details>

<details><summary>Hint 3: stopping</summary>

Look at `transformWhile`. When the upstream of a `callbackFlow` stops being collected, `awaitClose` runs.
</details>
