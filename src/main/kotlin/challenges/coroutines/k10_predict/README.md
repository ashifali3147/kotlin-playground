# K10 · Interview Round: "What Does This Print?" 🔍

**Track:** Coroutines · **Type:** Predict · **Difficulty:** ★★★☆☆

## The story

The most common coroutine interview format: the interviewer shares a snippet and asks
*"what's the output, and why?"* There's no IDE and no running it. You talk them through it.

Your files:
- [`Puzzles.kt`](Puzzles.kt): 8 puzzles. **Read only. Don't run them.**
- [`Predictions.kt`](Predictions.kt): write your predicted output for each one.

## Rules
- Predict **on paper first**, then type your answers. Don't run the puzzles, `main`, or a debugger before you've answered.
- For each puzzle, write **why** in `JOURNAL.md`, in one or two sentences, as you'd say it to an interviewer.
- Only flip `REVEAL = true` after you've honestly done your best on all 8.
- No AI.

## Run
```bash
./gradlew test --tests "challenges.coroutines.K10*"
```
Each puzzle is a separate test. A wrong answer just says "wrong" until you turn on `REVEAL`.

## What each puzzle tests (read after answering)
<details><summary>Spoiler-free topics</summary>

1. When does a `launch`ed coroutine actually start?
2. `coroutineScope` vs `launch`: who waits for whom?
3. `async` laziness (or not)
4. `yield()` and the single-threaded event loop
5. Failure propagation, cancellation of siblings, and where exceptions surface
6. Suspending inside `finally` after cancellation
7. `async` failures inside `supervisorScope`
8. Is `count++` from 1000 coroutines a race here?
</details>

## After revealing
For every puzzle you got wrong, write the **rule** you didn't know as a one-line flashcard in `JOURNAL.md`.
We'll come back to them in a "variant" round in 2 weeks.
