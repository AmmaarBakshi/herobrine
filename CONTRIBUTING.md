# Contributing

Thanks for helping out! This mod aims to stay small, fast and server-authoritative.

## Building

You only need a JDK 25 (Gradle can fetch one for you through the toolchain resolver).

```sh
./gradlew build        # compile, run unit tests, produce build/libs/*.jar
./gradlew runClient    # launch a dev client
./gradlew runServer    # launch a dev dedicated server (run/server)
./gradlew genSources   # decompile Minecraft for reading vanilla code
```

All versions are pinned in `gradle.properties` and the wrapper checks the Gradle
distribution's SHA-256, so the same commit produces the same jar everywhere.

## Code rules

- **Mojang names only.** Minecraft 26.x is unobfuscated; use the names you see in `genSources`.
- **Never guess an API.** Check the decompiled source or the Fabric API source first.
- **Events over Mixins.** Use a Fabric API event when one exists. When a Mixin is
  unavoidable, keep it tiny and comment *why* no event works.
- **Server-authoritative.** Game logic lives in `src/main`. Anything touching
  rendering, screens or input lives in `src/client` and must never be referenced
  from `src/main`.
- **Performance.** No per-tick world scans. Use cooldowns, bounded searches and
  sample at intervals.
- **Pure logic is testable.** Code in `mirror/` (traits, analysis, counters) should
  avoid Minecraft imports and come with JUnit tests in `src/test`.
- Comments explain *why*, not *what*.

## Commits and pull requests

- One logical change per commit, imperative mood ("Add torch thief event").
- `./gradlew build` must pass before you open a PR; CI runs the same command.

## Assets

Only original assets or CC0 assets. Record the source and license of anything
you did not create yourself in `CREDITS.md`.
