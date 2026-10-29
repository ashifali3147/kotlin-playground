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
./dojo T01       # pass/fail per test
./dojo T01 -d    # plus why each failure happened, where, and what it printed
```
