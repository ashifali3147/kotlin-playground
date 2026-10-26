# K06 · The Refresh Token Stampede 🐛

**Track:** Coroutines · **Type:** Fix · **Difficulty:** ★★★★☆ · *A very common Android interview question*

## The story

The home screen fires ~50 API requests at once. When the access token expires, **all 50 get 401**,
and each one triggers its own token refresh. The auth server sees 50 refresh calls in 100ms and,
as a security measure, **revokes the refresh token**. The user gets logged out.
Users call it "the app logs me out randomly".

Your file: [`AuthTokenProvider.kt`](AuthTokenProvider.kt)

## Your task
- However many coroutines call `validToken()` at once (on any threads), there's **only one** `refresh()` in flight,
  and every caller gets the new token.
- `invalidate(rejected)` must be smart. If the token was **already refreshed** since `rejected` was handed out,
  it must do nothing. 50 requests rejected with the same old token cause exactly **one** refresh.
- A failed refresh isn't cached: the caller gets the exception, and a later call tries again.
- Never block a thread while waiting for the refresh.
- `invalidate` stays a regular (non-`suspend`) function. The HTTP layer calls it from a blocking interceptor.

## Rules
- Don't edit the test file. Keep the public API.
- No AI.

## Run
```bash
./gradlew test --tests "challenges.coroutines.K06*"
```

## Before asking for a review, write in `JOURNAL.md`
1. Why can't you use `synchronized` around `authApi.refresh()`? What does the compiler say, and *why* does that rule exist?
2. `Mutex` vs `synchronized`: what does "suspends instead of blocks" mean for the thread? Is `Mutex` reentrant?
3. Why do you need to check the token **again** after acquiring the lock? (Hello again, T04.)
4. How did you make `invalidate` safe without making it `suspend`? What would go wrong with a plain `token = null`?
5. In OkHttp, where does this logic live? (`Authenticator` vs `Interceptor`.)

## Hints
<details><summary>Hint 1</summary>

This is check-then-act again (T01, T03, T04), but now the "act" is a **suspend** call, so the thread tools don't fit.
Look in `kotlinx.coroutines.sync`.
</details>

<details><summary>Hint 2</summary>

Only one coroutine may refresh at a time. The ones that waited should find a fresh token when they finally get in.
What should they check first?
</details>

<details><summary>Hint 3</summary>

For `invalidate`: "clear the token, but only if it's still the rejected one" is a compare-and-set.
Look at `AtomicReference.compareAndSet`, or keep the critical section tiny.
</details>
