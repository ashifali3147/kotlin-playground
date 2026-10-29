# T05 · Dinner Rush Takes Down Dispatch 🐛

**Track:** Threads · **Type:** Fix · **Difficulty:** ★★★☆☆

## The story

Rider dispatch worked perfectly in staging, which has 1 socket reader and 1 assignment worker.
On the first Friday night in production, with 4 readers and 4 workers:
- some workers crashed with `NoSuchElementException` from `take()`
- the queue sometimes held **more** orders than its capacity
- and twice, the whole dispatch pipeline just **stopped**: no crash, no CPU usage, orders piling up upstream

Your file: [`OrderDispatchQueue.kt`](OrderDispatchQueue.kt)

## Your task
Make `OrderDispatchQueue` a correct bounded blocking queue for **any** number of producers and consumers:
- `put` waits while full, `take` waits while empty. No busy-spinning: a waiting thread must use no CPU.
- The size never goes over `capacity`. FIFO order. Every order is taken exactly once.

## Rules
- Don't edit the test file. Keep the public API.
- **No ready-made blocking queues or semaphores** (`ArrayBlockingQueue`, `LinkedBlockingQueue`, `Semaphore`, ...).
  The point is to understand what they do inside. Use `wait`/`notify`, or `ReentrantLock` + `Condition`.
- No AI.

## Run
```bash
./dojo T05
```

## Before asking for a review, write in `JOURNAL.md`
1. What is a "spurious wakeup"? Even without spurious wakeups, why can a woken thread find the condition false again?
2. `notify()` vs `notifyAll()`: describe the exact sequence of events that freezes the pipeline with `notify()`.
3. If you used `ReentrantLock`: why do two `Condition`s (`notFull`, `notEmpty`) beat one monitor here?
4. In real code you'd never write this class. What would you use on the JVM? And in coroutines?
   How is a `Channel(capacity)` similar, and how is it different?
5. What happens to a thread blocked in `wait()` when it's interrupted? Should `put`/`take` swallow `InterruptedException`?

## Hints
<details><summary>Hint 1</summary>

With 1 producer and 1 consumer everything works, so the bugs need *several threads waiting for the
same lock at once*. Picture 2 consumers waiting on an empty queue, and 1 producer adding a single item.
Who gets the item?
</details>

<details><summary>Hint 2</summary>

Two separate bugs: one about **what you do after waking up**, and one about **who you wake up**.
Both producers and consumers wait on the *same* monitor. What if a producer wakes another producer?
</details>

<details><summary>Hint 3</summary>

The canonical wait pattern is in the Javadoc of `Object.wait()`. Go read it, it's one line long.
For the freeze: either wake everyone, or give producers and consumers separate waiting rooms.
</details>
