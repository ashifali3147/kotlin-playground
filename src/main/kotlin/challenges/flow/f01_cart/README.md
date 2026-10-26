# F01 · The Cart That's Fetched Three Times 🐛

**Track:** Flow · **Type:** Fix · **Difficulty:** ★★★☆☆

## The story

The backend team shows you a graph: **the cart endpoint gets 3× more traffic than there are users**.
The cart screen has three things showing cart data: the toolbar badge, the item list and the checkout button.
Each one collects `cartRepository.cart`.

There's more:
- **Every screen rotation** triggers a new fetch.
- Someone tried a "fix" that shares the flow, but now, when the user comes back to the app after a while,
  they **briefly see an old cart** before the fresh one arrives. Product calls it "the ghost cart".

Your file: [`CartRepository.kt`](CartRepository.kt)

## Your task
- **One** upstream (one fetch + refresh loop) shared by all collectors.
- A collector that joins later gets the latest cart **immediately**, without a new fetch.
- **Rotation-proof:** if all collectors disappear and a new one arrives within `STOP_TIMEOUT_MS`, nothing restarts.
- **Stops when unused:** once nobody has collected for longer than `STOP_TIMEOUT_MS`, the refresh loop stops (no more fetches).
- **No ghost cart:** after the upstream has stopped, a new collector must *not* receive the stale cached cart. Its first value is a fresh fetch.
- Keep the public type `Flow<Cart>`.

## Rules
- Don't edit the test file. Use the provided `appScope`.
- No AI.

## Run
```bash
./gradlew test --tests "challenges.flow.F01*"
```

## Before asking for a review, write in `JOURNAL.md`
1. "Cold" vs "hot" flow, in one sentence each. Which one is `flow { }`?
2. `shareIn` vs `stateIn`: what's the difference? Which one did you pick, and why?
3. `SharingStarted.Eagerly` vs `Lazily` vs `WhileSubscribed`. Why does Google recommend `WhileSubscribed(5000)` for UI? Where does the 5000 come from?
4. What does `replayExpirationMillis` do? How did it fix the ghost cart?
5. Why must sharing happen in a scope *outside* the collectors (here, `appScope`)? What would happen with a screen's scope?

## Hints
<details><summary>Hint 1</summary>

`flow { }` is cold: the block runs again **for every collector**. You need to turn it into a hot flow that multicasts one upstream.
</details>

<details><summary>Hint 2</summary>

Look at `shareIn(scope, started, replay)` and the `SharingStarted` options.
</details>

<details><summary>Hint 3</summary>

`SharingStarted.WhileSubscribed` takes **two** parameters. Read what the second one does to the replay cache after the upstream stops.
</details>
