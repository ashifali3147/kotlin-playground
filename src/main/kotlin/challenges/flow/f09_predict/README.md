# F09 · Interview Round: "What Does This Flow Print?" 🔍

**Track:** Flow · **Type:** Predict · **Difficulty:** ★★★☆☆

## The story

Same format as K10, now with Flow. The interviewer shares a snippet: *"what's the output, and why?"*
There's no IDE. You reason it out loud.

Your files:
- [`FlowPuzzles.kt`](FlowPuzzles.kt): 8 puzzles. **Read only. Don't run them.**
- [`FlowPredictions.kt`](FlowPredictions.kt): write your predicted output for each one.

## Rules
- Predict **on paper first**. Don't run the puzzles, `main`, or a debugger before answering.
- For each puzzle, write **why** in `JOURNAL.md`, in one or two sentences.
- Only flip `REVEAL = true` after an honest best effort on all 8.
- No AI.

## Run
```bash
./gradlew test --tests "challenges.flow.F09*"
```

## What each puzzle tests (read after answering)
<details><summary>Spoiler-free topics</summary>

1. Cold flows and repeated collection
2. Flow operators are sequential, value by value
3. What `buffer()` does to execution order
4. StateFlow conflation and when a launched collector starts
5. SharedFlow without subscribers
6. `flatMapLatest` cancellation
7. Which exceptions `catch` can see
8. `combine` timing
</details>

## After revealing
For every puzzle you got wrong, write the **rule** you didn't know as a one-line flashcard in `JOURNAL.md`.
