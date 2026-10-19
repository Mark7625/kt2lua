# Differential testing

Every fixture under `src/test/resources/differential-fixtures/**/*.kt` is run two ways and the
results are compared:

1. Compiled for real with the Kotlin compiler (`kotlin-compiler-embeddable`, in-process — no
   `kotlinc` process spawned) and executed on this JVM. This is the reference behavior.
2. Transpiled with kt2lua and run through kt2lua's own Lua interpreter, in-process.

Both sides capture `println`/`print` output; a mismatch means the generated Lua doesn't behave
like the Kotlin it came from.

## Running it

```bash
./gradlew differentialTest                      # everything
./gradlew differentialTest -Pcategory=collections # one category (the fixture's top-level subdirectory)
```

`differentialTest` is a separate task from `test` — it isn't part of `./gradlew build`/`check`,
since compiling with the real Kotlin compiler costs a few seconds even for one fixture. Ordinary
`./gradlew test` stays fast and unaffected.

## Adding a fixture

Add a `.kt` file anywhere under `differential-fixtures/<category>/`. It needs a `fun main()` that
prints what you want verified — nothing else is required; the file is compiled and run both ways
automatically. Stick to features kt2lua actually supports (see the root README's feature
description) — this suite exists to catch *regressions* in supported behavior, not to document
wishlist features.

## The `regression/` category

Fixtures here are *confirmed, known* transpiler bugs — a mismatch there is expected and the test
passes, logging that the bug is still present. If kt2lua is changed and a regression fixture
starts *matching* Kotlin again, that specific test fails, telling you the bug looks fixed and the
fixture should move out of `regression/` into its real category as ordinary coverage. Currently
tracked:

- `regression/data_class_structural_equality.kt` — `==` on two structurally-equal data class
  instances is `false` in Lua (no `__eq` metamethod is emitted).
- `regression/negative_modulo_sign.kt` — Kotlin's `%` truncates toward zero; Lua's `%` floors.
  `-5 % 2` is `-1` in Kotlin, `1` in the generated Lua.
- `regression/filternotnull_embedded_nil.kt` — a `List<Int?>` with nulls becomes a plain Lua array
  with `nil` holes; `ipairs()` stops at the first hole, so `filterNotNull()` silently drops
  everything after the first null.

## What this does and doesn't cover yet

Implemented: real dual-execution comparison (not source-text diffing), batch Kotlin compilation
(one compiler invocation per run, not per fixture), in-process Lua execution (no process spawned),
per-fixture compile-failure isolation (a broken fixture doesn't take the whole batch down), a
`regression/` category for confirmed bugs, category filtering, and a CI workflow
(`.github/workflows/differential-tests.yml`).

Not yet built (noted here rather than silently skipped): affected-test selection (every run
currently runs the full suite — fine while it's ~25 fixtures and ~20 seconds, but won't scale to
thousands without it), content-addressed caching, property-based/randomized testing, a structured
(non-stdout-text) result protocol, nightly/weekly tiers, and compatibility-manifest coverage
reporting.
