# F04 · The Checkout Total That Lies 🐛

**Track:** Flow · **Type:** Fix · **Difficulty:** ★★★☆☆

## The story

Checkout bugs from QA:
- Switching payment from UPI to Card **doesn't update the screen** until you also change the cart *and* the address.
- Tapping "+" quickly on an item shows totals that **jump around**. Sometimes the final total is for an *older* cart.
- The pricing team noticed that we call the quote API when the user only switches payment method. Payment doesn't affect the price.

Your file: [`CheckoutViewModel.kt`](CheckoutViewModel.kt)

## Your task
- `Loading` until **every** source has emitted at least once.
- After that, a change to **any** source updates the state.
- The quote depends only on cart + address. Changing **only** the payment method must **not** call `pricing.quote`.
- If cart/address change while a quote is in flight, drop that outdated quote. A total for an old cart must **never** be shown.
- `canPlaceOrder` = address and payment are both set.

## Rules
- Don't edit the test file. Keep the public API.
- No AI.

## Run
```bash
./gradlew test --tests "challenges.flow.F04*"
```

## Before asking for a review, write in `JOURNAL.md`
1. `zip` vs `combine`: draw the marble diagram for both with the same inputs.
2. `map` vs `mapLatest` (or `transformLatest`): what does "latest" cancel?
3. How did you avoid re-quoting when only payment changed? Draw your pipeline.
4. What does `combine` emit if one source never emits? Why does that matter for `Loading`?
5. Why does `stateIn` need an initial value, and how did you choose it?

## Hints
<details><summary>Hint 1</summary>

`zip` pairs the 1st with the 1st, the 2nd with the 2nd, and so on. It waits until **all** sources move. That's not "latest of each".
</details>

<details><summary>Hint 2</summary>

Split the pipeline: (cart + address) → quote, then combine that with payment.
</details>

<details><summary>Hint 3</summary>

A plain `map` with a suspend call processes every value, in order, even the outdated ones. There's a `...Latest` variant.
</details>
