# F07 · The Socket That Never Lets Go 🐛

**Track:** Flow · **Type:** Fix · **Difficulty:** ★★★☆☆

## The story

Live order status comes over a WebSocket, wrapped in a `callbackFlow`. Reports:
- The backend sees **thousands of open subscriptions** for orders whose screens were closed long ago.
- When an order is delivered, the server closes the stream, but the tracking screen's `collect` **never finishes**.
  The "Rate your order" dialog that should come after it never appears.
- When the socket fails, the screen keeps showing a spinner forever, with no error.
- On slow phones, *all* live features (chat, rider location, order status) freeze together for seconds at a time.

Your file: [`LiveOrderRepository.kt`](LiveOrderRepository.kt)

## Your task
- When the collector is cancelled, **unsubscribe** from the socket.
- When the server closes the stream (`onClosed`), the flow **completes normally**.
- When the socket fails (`onFailure`), the flow **fails with that exception**.
- In every case above, the subscription is cleaned up.
- A socket callback must **never block**, even if the collector is slow. The UI only needs the **latest** status,
  so older undelivered updates may be dropped.

## Rules
- Don't edit the test file. Keep the public API. You can't change `OrderSocket`.
- No AI.

## Run
```bash
./gradlew test --tests "challenges.flow.F07*"
```

## Before asking for a review, write in `JOURNAL.md`
1. What does `awaitClose` do, and when does its block run? List every way that block can be triggered.
2. `send` vs `trySend` from a callback: why is `runBlocking { send() }` on the socket thread so dangerous?
3. `close()` vs `close(exception)` vs `cancel()` on a `ProducerScope`: what happens downstream for each?
4. What buffer did you choose, and why? (`buffer(Channel.CONFLATED)` after `callbackFlow`: what is "fusion"?)
5. `callbackFlow` vs `channelFlow` vs `flow { }`: when do you need each one?

## Hints
<details><summary>Hint 1</summary>

`subscribe` returns a `Subscription`. Right now it's thrown away, and `awaitClose { }` is empty.
</details>

<details><summary>Hint 2</summary>

The `ProducerScope` inside `callbackFlow` is also a `SendChannel`: you can `close()` it, with or without a cause.
</details>

<details><summary>Hint 3</summary>

`trySend` never suspends. Combine it with a buffer policy that keeps only the latest value.
</details>
