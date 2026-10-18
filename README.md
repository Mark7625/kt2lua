<p align="center">
  <img src="branding/kt2lua-square-icon.png" alt="kt2lua" width="120">
</p>
<p align="center">
  <img src="branding/kt2lua-wordmark.png" alt="kt2lua" height="56">
</p>

kt2lua transpiles a small subset of Kotlin into Lua 5.1, and ships its own Lua
lexer/parser/interpreter so the output runs without installing Lua.

It supports functions, variables, control flow, classes with single inheritance, collections,
and basic try/catch — not the whole language. See the
[feature tracker](https://github.com/Mark7625/kt2lua/issues/1) for exactly what's supported,
what's not, and what's planned next.

## Add it to your project

Not published to a registry yet — use it as a composite build:

```kotlin
// settings.gradle.kts
pluginManagement {
    includeBuild("path/to/kt2lua")
}
```

```kotlin
// build.gradle.kts
plugins {
    id("kt2lua.transpile")
}

kt2lua {
    sourceDir.set(layout.projectDirectory.dir("src/main/kotlin-scripts"))
    outputDir.set(layout.buildDirectory.dir("generated/kt2lua"))
}
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

## Layout

- `transpiler` — the Kotlin → Lua transpiler
- `interpreter` — the bundled Lua interpreter
- `gradle-plugin` — the Gradle plugin
- `cli` — standalone command-line tool
- `test-runner` — end-to-end tests (transpile, run, diff output)
- `cli/examples` — small Kotlin → Lua demos
- `libs/runescape` — RS3 API bindings, unrelated to kt2lua itself (see its own README)

## Building

```
./gradlew build
```

Compiles everything and runs the test suite. No system Lua install required.

## License

MIT
