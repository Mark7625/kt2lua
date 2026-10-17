# examples

Plain kt2lua Kotlin → Lua demos, no RS3/`libs/runescape` dependency at all — just the
transpiler's own features. Each file is self-contained and runs straight through the CLI with
`--run` (single-file mode — these don't reference each other, so there's no need for the
multi-file CLI invocation). No Gradle module here: there's no real API to type-check against,
so these are just plain `.kt` files, not a Kotlin project.

| File | Demonstrates |
|---|---|
| `hello.kt` | A function, string templates, `println` — the smallest useful program |
| `control-flow.kt` | `if`/`else` (statement and expression), `when` (subject, `in range`), `for`/`until`/`step`, `while` |
| `collections.kt` | `listOf`/`mapOf`, indexing (`arr[i]`/`m["key"]`), `forEach`/`map`/`filter`/`fold`/`reduce` |
| `classes.kt` | `data class` (auto `toString()`), a `class` with methods, single-superclass inheritance (`open`/`override`) |

If you're looking for a real RS3 plugin built against the generated API bindings instead (real
Kotlin compile-time type-checking, a real `definePlugin(...)` call, the works), see
`libs/runescape/examples/prettyui-window-kotlin` — that one's deliberately kept separate from
here, since it exists specifically to exercise `libs/runescape/*`, not to demonstrate kt2lua on
its own.

## Running one

```
./gradlew :cli:installDist
CLI=cli/build/install/cli/bin/cli
"$CLI" examples/hello.kt --run
```

`--run` transpiles and immediately executes the result with kt2lua's own built-in interpreter
(in-process — no subprocess, no system Lua install) — the generated `build/dist/hello.lua` is
still written either way (that's the CLI's default output location; pass `-o` for somewhere
else). Drop `--run` if you just want to see the generated Lua without running it.
