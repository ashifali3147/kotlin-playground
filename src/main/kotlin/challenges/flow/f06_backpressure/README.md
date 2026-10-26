# F06 · The Rider Marker That Lags Behind 🐛

**Track:** Flow · **Type:** Fix · **Difficulty:** ★★★☆☆

## The story

On the tracking screen the rider marker is **further and further behind** the real rider. After a few minutes,
the marker shows where the rider was 2 minutes ago. The GPS team also complains that our slow collectors
**block their reader**: fixes come in late, with stale timestamps.

Meanwhile, the analytics team says the recorded routes are fine but "arrive late". Whatever you do,
they **need every single point**, in order.

Your file: [`RiderLocationFeed.kt`](RiderLocationFeed.kt)

## Your task
`showOnMap`:
- Must never slow down the GPS source.
- Always moves towards the **newest** position. Skipping intermediate points is fine.
- A render that has started must **finish** (no half-drawn animation frames). Don't cancel renders.
- After the GPS stops, the marker reaches the final position soon after, not seconds later.

`recordRoute`:
- Must never slow down the GPS source.
- Records **every** point, in order. Losing points is not allowed.

## Rules
- Don't edit the test file. Keep the public API.
- No AI.

## Run
```bash
./gradlew test --tests "challenges.flow.F06*"
```

## Before asking for a review, write in `JOURNAL.md`
1. What is backpressure? How does Flow handle it by default, and why is that the default?
2. `buffer()` vs `conflate()` vs `collectLatest`: one sentence each, plus one real use case each.
3. Why is `collectLatest` wrong for the map here? When would it be right?
4. `buffer(Channel.UNLIMITED)` for analytics: what's the risk? What would you do in production?
5. What does "operator fusion" mean for `buffer` + `flowOn` placed next to each other?

## Hints
<details><summary>Hint 1</summary>

By default, the producer and the collector run in **one** coroutine, taking turns. A slow collector means a slow producer.
</details>

<details><summary>Hint 2</summary>

To decouple them, the producer needs to run in its own coroutine and hand values over through a buffer.
What should happen when that buffer fills up? The two consumers want *different* answers.
</details>

<details><summary>Hint 3</summary>

One consumer wants "keep only the newest, but let the current work finish". The other wants "keep everything".
</details>
