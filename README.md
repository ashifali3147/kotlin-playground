# Kotlin Playground

Small Kotlin JVM playground using Gradle.

## Requirements

- JDK 17+
- Gradle (one-time, only to generate wrapper if missing)

## Setup

If this repo does not have `gradlew` yet:

```bash
gradle wrapper
```

## Run

```bash
./gradlew run
```

## Build

```bash
./gradlew build
```

Main entrypoint is `src/main/kotlin/Main.kt`.

## Concurrency Dojo

Interview-practice challenges on threads, coroutines and Flow live in `src/main/kotlin/challenges/`.
Start at [CHALLENGES.md](CHALLENGES.md).

```bash
./gradlew test --tests "challenges.threads.C01*"
```
