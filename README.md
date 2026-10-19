<p align="center">
  <img src="branding/png/kt2lua-mark-512.png" alt="kt2lua" width="120">
</p>
<p align="center">
  <img src="branding/png/kt2lua-wordmark.png" alt="kt2lua" height="56">
</p>
<p align="center">
  <img src="https://img.shields.io/badge/kotlin-2.3.0-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 2.3.0">
  <img src="https://img.shields.io/badge/lua-5.1-2C2D72?logo=lua&logoColor=white" alt="Lua 5.1">
  <img src="https://img.shields.io/badge/license-MIT-blue" alt="MIT license">
  <a href="https://mvnrepository.com/artifact/io.github.mark7625/kt2lua"><img src="https://badges.mvnrepository.com/badge/io.github.mark7625/kt2lua/badge.svg?label=MvnRepository" alt="MvnRepository"></a>
  <!-- compat-badge:start --><img src="https://img.shields.io/badge/stdlib%20coverage-30%25-orange" alt="Kotlin stdlib coverage"><!-- compat-badge:end -->
</p>

kt2lua transpiles a small subset of Kotlin into Lua 5.1, and ships its own Lua
lexer/parser/interpreter so the output runs without installing Lua.

It supports functions, variables, control flow, classes with single inheritance, collections,
and basic try/catch — not the whole language. See the
[feature tracker](https://github.com/Mark7625/kt2lua/issues/1) for exactly what's supported,
what's not, and what's planned next.

## Add it to your project

Published to Maven Central under `io.github.mark7625`:

```kotlin
// build.gradle.kts
plugins {
    id("io.github.mark7625.kt2lua") version "0.1.0"
}

kt2lua {
    sourceDir.set(layout.projectDirectory.dir("src/main/kotlin-scripts"))
    outputDir.set(layout.buildDirectory.dir("generated/kt2lua"))
}
```

To use the transpiler, interpreter and CLI as a library instead:

```kotlin
implementation("io.github.mark7625:kt2lua:0.1.0")
```

This registers a `transpileKotlinToLua` task that transpiles every `.kt` file under `sourceDir`
into `.lua` under `outputDir`, keeping the same folder structure.

## Or use the CLI

```
./gradlew :cli:installDist
cli/build/install/cli/bin/cli script.kt --run
```

`--run` transpiles and immediately runs the result with kt2lua's own interpreter. Pass a
directory to transpile a whole package tree at once (folder structure preserved), or several
files to transpile them together with automatic cross-file `require()`. Run `--help` for
everything else.

Output formatting follows the
[luarocks Lua style guide](https://github.com/luarocks/lua-style-guide) by default; drop a
`kt2lua-style.properties` file next to your project to override it (auto-detected, no flag
needed).

## Lua compatibility

Generated output targets **Lua 5.1** syntax and semantics. The `build` CI job runs exclusively
against the interpreter this repo bundles — there's no step anywhere that installs or runs a
real standalone Lua/LuaJIT binary, so "tested" below means "verified against the bundled
interpreter," not against the real thing.

| Lua version | Status | Notes |
| --- | --- | --- |
| 5.1 | ✅ Yes | The actual target. Verified via the bundled interpreter in `test-runner`. |
| LuaJIT | ✅ Should work | LuaJIT's default syntax is Lua 5.1-compatible, which is everything this transpiler emits. Not independently verified against a real LuaJIT binary. |
| 5.2 | ✅ Should work | Lua 5.2 keeps the global `unpack()` as a deprecated compatibility alias, which is what this transpiler emits for a spread argument (`*xs`). Not independently verified. |
| 5.3 | ⚠️ No, unless the host provides `unpack` | Everything emitted is 5.3-compatible *except* spread arguments (`*xs`), which compile to the global `unpack(...)` — removed in 5.3. Works if your host environment defines a global `unpack` alias (common in embedding engines); fails otherwise. Avoid spread arguments if you need plain 5.3. |
| 5.4 | ⚠️ No, unless the host provides `unpack` | Same caveat as 5.3. |

If you hit the `unpack` issue on 5.3/5.4, add this once in your Lua environment before running
generated code:

```lua
unpack = unpack or table.unpack
```

## Kotlin stdlib compatibility

Per-package progress against the real Kotlin stdlib, generated from the compatibility manifests
under `transpiler/src/main/resources/compatibility/kotlin-stdlib` (`kt2lua compat report table`)
and kept in sync on every push to `main` — don't hand-edit the table below, it'll be overwritten.
See the [tracking issue](https://github.com/Mark7625/kt2lua/issues/3) for exactly which
declarations are still partial/unsupported per package, or run `kt2lua compat status <name>`
locally.

<!-- compat-report:start -->
| Package | Done | Partial | Left | % Complete |
|---|---|---|---|---|
| [kotlin](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/) | 8/206 | 21 | 177 | 3% |
| [kotlin.collections](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/) | 112/365 | 19 | 234 | 30% |
| [kotlin.enums](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.enums/) | 0/2 | 0 | 2 | 0% |
| [kotlin.io](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.io/) | 0/56 | 2 | 54 | 0% |
| [kotlin.io.path](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.io.path/) | 0/81 | 0 | 81 | 0% |
| [kotlin.math](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.math/) | 43/43 | 0 | 0 | 100% |
| [kotlin.random](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.random/) | 11/11 | 0 | 0 | 100% |
| [kotlin.ranges](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.ranges/) | 29/29 | 0 | 0 | 100% |
| [kotlin.sequences](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.sequences/) | 149/149 | 0 | 0 | 100% |
| [kotlin.streams](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.streams/) | 3/3 | 0 | 0 | 100% |
| [kotlin.system](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.system/) | 7/7 | 0 | 0 | 100% |
| [kotlin.text](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/) | 5/309 | 1 | 303 | 1% |
| [kotlin.time](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.time/) | 29/29 | 0 | 0 | 100% |
| **Total** | **396/1290** | **43** | **851** | **30%** |

_Done = fully implemented; Partial = implemented with a real caveat, not counted toward % Complete; Left = not started at all. See the tracking issue for exactly what each partial entry is missing._
<!-- compat-report:end -->

## Layout

- `transpiler` — the Kotlin → Lua transpiler
- `interpreter` — the bundled Lua interpreter
- `gradle-plugin` — the Gradle plugin
- `cli` — standalone command-line tool
- `test-runner` — end-to-end tests (transpile, run, diff output)
- `cli/examples` — small Kotlin → Lua demos
## Building

```
./gradlew build
```

Compiles everything and runs the test suite. No system Lua install required.

## License

MIT
