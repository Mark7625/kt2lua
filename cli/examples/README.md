# examples

Small, self-contained kt2lua demos with no external dependencies.

```
./gradlew :cli:installDist
cli/build/install/cli/bin/cli cli/examples/hello.kt --run
```

| File | Demonstrates |
|---|---|
| `hello.kt` | functions, string templates, `println` |
| `control-flow.kt` | `if`/`when`/`for`/`while` |
| `collections.kt` | `listOf`/`mapOf`, indexing, `forEach`/`map`/`filter`/`fold`/`reduce` |
| `classes.kt` | `data class`, methods, single-superclass inheritance |

`--run` transpiles and executes with kt2lua's own interpreter. Drop it to just transpile.

For a real RS3 plugin example, see
[`libs/runescape/examples/prettyui-window-kotlin`](../../libs/runescape/examples/prettyui-window-kotlin).
