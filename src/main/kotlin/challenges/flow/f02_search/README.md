# F02 · Search That Shows the Wrong Restaurants 🐛

**Track:** Flow · **Type:** Fix · **Difficulty:** ★★★☆☆ · *A classic Flow interview question*

## The story

The restaurant search screen has problems:
- Typing "biryani" fires **7 API calls**, one per letter. The search team asked us to stop.
- Users type "pizza" and see **results for "piz"**. The slow "piz" response arrived *after* the fast "pizza" one and overwrote it.
- If a single search fails (timeout), search **stops working** until the screen is reopened.
- There's no loading indicator, and typing one letter searches the entire catalogue.

Your file: [`SearchViewModel.kt`](SearchViewModel.kt)

## Your task
- Search only after the user has stopped typing for `DEBOUNCE_MS`.
- Queries are trimmed. Shorter than `MIN_QUERY_LENGTH` → `Idle`, and no API call.
- Don't search the same query twice in a row (e.g. "pizza" → "pizzas" → "pizza" quickly settles back on "pizza": no new search).
- A newer query **cancels** the in-flight search. Stale results must **never** be shown, not even briefly.
- Show `Loading` when a search starts, then `Results` or `Error`.
- A failed search shows `Error(query, exception.message)` for that query, and search keeps working for the next query.

## Rules
- Don't edit the test file. Keep the public API.
- No AI.

## Run
```bash
./dojo F02
```

## Before asking for a review, write in `JOURNAL.md`
1. `flatMapConcat` vs `flatMapMerge` vs `flatMapLatest`: describe each in one line, with a use case.
2. Why did one failed search kill the whole search screen? What happens to a flow after an exception reaches the terminal operator?
3. Where did you put `catch`, and why does its position matter?
4. Why did the order of `debounce` and `distinctUntilChanged` matter (or not)?
5. `StateFlow` already skips equal values. So why might you still need `distinctUntilChanged`?

## Hints
<details><summary>Hint 1</summary>

Think of it as a pipeline: clean the text → wait for a pause → skip duplicates → run *one* search at a time, cancelling the old one.
</details>

<details><summary>Hint 2</summary>

`flatMapMerge` runs inner flows **concurrently**, so results arrive in whatever order the network returns them.
You want the operator that cancels the previous inner flow when a new value arrives.
</details>

<details><summary>Hint 3</summary>

An exception that escapes the inner flow kills the outer flow. Catch it **inside** the inner flow, and turn it into an `Error` state.
</details>
