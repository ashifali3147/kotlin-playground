# F08 · Build: The Review Draft Auto-Saver 🏗

**Track:** Flow · **Type:** Build · **Difficulty:** ★★★★☆ · *Track 3 final*

## The story

Users write long restaurant reviews and lose them when the app is killed. Product wants Google Docs-style
auto-save, with a small status label: *"Saving…"*, *"Saved"*, *"Couldn't save"*.

Your file: [`DraftAutoSaver.kt`](DraftAutoSaver.kt). Everything is `TODO()`.

## Spec
1. **Debounce:** save `DEBOUNCE_MS` after the user **stops** typing. Continuous typing → no saves yet.
2. **No pointless saves:** if the text equals the last **successfully saved** text, don't save.
3. **One save at a time:** never two `api.save` calls running at once. An in-flight save is **never cancelled**
   (the server may be halfway through a write). If newer text arrives meanwhile, save it after the current save finishes.
   Intermediate versions may be skipped, but the **latest** text always ends up saved.
4. **Status:** `Idle` at first. `Saving` while a save runs. `Saved(text)` after success. `Failed(text, exception.message)` after a failure.
5. **Failures:** no automatic retry. The next text change, or `flush()`, tries again.
6. **`flush()`:** saves the latest unsaved text **immediately** (no debounce wait), and returns once it's saved.
   - Nothing unsaved → returns immediately, without calling the API.
   - A save of that same text is already running → wait for it, don't save twice.
   - The debounce timer must not save the same text again afterwards.
7. Cancellation of `scope` (screen gone) stops everything. It never shows up as `Failed`.

## Rules
- Don't edit the test file. Keep the public API.
- Anything from `kotlinx.coroutines` is allowed.
- No AI.

## Run
```bash
./dojo F08
```

## Before asking for a review, write in `JOURNAL.md`
1. Draw your design: which flows, channels, locks or jobs are involved, and who owns what?
2. Spec 3 says "never cancel an in-flight save". Which operator would have violated that, and which one did you use instead?
3. How do `flush()` and the debounced pipeline avoid racing each other?
4. Which state is accessed from more than one coroutine? How did you make it safe?
5. What would you change if the app could be **killed** during typing? (Think: `SavedStateHandle`, WorkManager.)

## Hints
<details><summary>Hint 1</summary>

Start with a `MutableStateFlow<String>` for the text and a `MutableStateFlow<SaveStatus>` for the status.
Then launch one long-running collector in `scope`.
</details>

<details><summary>Hint 2</summary>

`debounce` → (a conflating step, so that only the latest text waits while a save runs) → `collect { save(it) }`.
Think about why `collectLatest` is wrong here (spec 3).
</details>

<details><summary>Hint 3</summary>

`flush()` and the collector both save. A `Mutex` around "check last saved → save → update" keeps them from racing.
</details>
