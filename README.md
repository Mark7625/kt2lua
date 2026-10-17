<p align="center">
  <img src="branding/kt2lua-square-icon.png" alt="kt2lua" width="120">
</p>
<p align="center">
  <img src="branding/kt2lua-wordmark.png" alt="kt2lua" height="56">
</p>

A small Kotlin → Lua transpiler, written in Kotlin, loosely inspired by
[TypeScriptToLua](https://github.com/TypeScriptToLua/TypeScriptToLua) (same idea — transpile a
typed language down to Lua — different source language). It covers a deliberately small subset
of Kotlin — enough for "hello world", functions, basic math, and control flow — not the whole
language. Lua execution doesn't depend on a system Lua install or a third-party interpreter
library: kt2lua ships its own small Lua lexer/parser/evaluator.

## Target Lua version

kt2lua targets **Lua 5.1 semantics** (one numeric type, no integer/float subtype split, no
bitwise operators, no `goto`) rather than 5.3/5.4. This is a deliberate choice, not an oversight:
the primary real-world target for generated output is the RS3 game client's embedded Lua runtime
(the same runtime the real `hello-ts`-style plugins run on), which is 5.1-shaped — and kt2lua's
own bundled interpreter (used for testing and for running compiled plugin bundles standalone) is
written to match that runtime's behavior, not a newer Lua spec. If you need 5.4-specific output
(integer subtype, `//`, bitwise `& | ~`, `goto`), that is explicitly **not** a goal right now;
tracked as future work below.

## Generated code style

The emitter follows the [luarocks Lua style guide](https://github.com/luarocks/lua-style-guide)
as far as machine-generated output reasonably can (identifier naming stays whatever the source
Kotlin used — renaming those would make the output harder to trace back to its source, the
opposite of readable):

- **3-space indentation**, never tabs.
- **A blank line before every function/class/object declaration** (not before the very first
  one, and not between plain statements) — `Emitter.isDeclBoundary`.
- **String literals double-quote by default**, switching to single quotes only when the text
  itself contains a `"` but no `'` (avoids escaping every embedded double quote).
- **A multi-field table literal stays on one line** when every field's value is itself
  single-line (`{label = "x", value = 3}`) but **reflows one field per line, trailing comma
  included,** the moment any field's value is multi-line — the common trigger being a lambda
  with a block body (`ext.definePlugin(name = ..., init = { ... })`-style calls, see "Class
  support" below). Avoids the alternative — a lambda's whole multi-statement body crammed onto
  one physical line next to three other fields — which was unreadable enough to fix directly
  (`Emitter.emitNamedArgTable`).
- Lambda bodies (`Emitter.emitLambda`) indent **relative to wherever they're actually embedded**
  (a statement, a table field, ...), not a fixed depth — otherwise a lambda nested inside
  anything indented would print its own `end` out of alignment with the rest of the file.

### Customizing it: `kt2lua-style.properties`

All four of the above are overridable per-project, the same way an IDE picks up a
`checkstyle.xml` without anything pointing at it: drop a `kt2lua-style.properties` file in the
current directory (CLI) or the project directory (Gradle plugin) and it's auto-detected, no
flag or build-script change needed. A missing file — the common case — just means "use kt2lua's
own defaults"; a file that only sets *some* of the four keys falls back to the default for
whichever ones it leaves out (or sets to something unparseable, e.g. `indent_size=lots`), rather
than discarding the whole file over one bad entry. See [`EmitStyle`][emitstyle] for the exact
fallback rules.

```properties
# kt2lua-style.properties — every key is optional; this example sets all four anyway.
indent_size=4
indent_style=space
# double | single | smart (kt2lua's own default — double-quote, except switch to single
# quotes when the text contains a " but no ', to avoid escaping)
quote_style=smart
blank_line_before_declarations=true
```

- **CLI**: auto-detected from the current directory; `--style <file>` points at a specific file
  instead.
- **Gradle plugin**: auto-detected from the project directory; `kt2lua { styleFile.set(file("
  ...")) }` points at a specific file instead.
- **Programmatically** (`kt2lua.transpiler.transpile`/`transpileProject`): pass an `EmitStyle`
  directly — `EmitStyle.load(file)` for "from a file, with fallback", or construct one directly
  (`EmitStyle(indentSize = 4)`) when you already know what you want.

[emitstyle]: build-logic/transpiler/src/main/kotlin/kt2lua/transpiler/EmitStyle.kt

## Layout

```
build-logic/               composite build providing the two libraries + the Gradle plugin
  transpiler/                Lexer -> Parser -> Ast -> SemanticAnalysis -> Emitter -> kt2lua:transpiler
  interpreter/                hand-written Lua lexer/parser/tree-walking evaluator -> kt2lua:interpreter
  gradle-plugin/              Gradle plugin on top of the transpiler -> id("kt2lua.transpile")
cli/                        standalone command-line tool (kt2lua.cli.Main)
test-runner/                JUnit module: runs the plugin over sample scripts, executes the
                            generated Lua with kt2lua's own interpreter, and checks output
libs/runescape/             publishable RS3 API bindings (real Kotlin, not transpiled) — three
                            independent modules (core/extended/prettyui), generated from the
                            real rs3-api-ts source, see its own README
  examples/
    prettyui-window-kotlin/   a real plugin depending on libs/runescape/* as a normal Gradle
                            dependency, compiled by the real Kotlin compiler and also
                            transpiled by kt2lua, ported from rs3-api-ts's examples/prettyui-
                            window — a full PrettyUI settings window + status panel; its README
                            documents several real kt2lua parser limits and converter bugs this
                            drove fixes for. Lives under libs/runescape (not at the repo's own
                            examples/) since it exists specifically to exercise those modules —
                            see examples/ below for kt2lua's own, RS3-independent examples
examples/                   plain kt2lua Kotlin -> Lua demos with no RS3/libs dependency at
                            all — just the transpiler's own features (control flow, collections,
                            classes/inheritance), each runnable straight through the CLI; see
                            its own README
```

`build-logic` is included via `pluginManagement.includeBuild` (so `id("kt2lua.transpile")`
resolves without a published version) and also via a plain `includeBuild` (so `cli`/`test-runner`
can depend on `kt2lua:transpiler:0.1.0` / `kt2lua:interpreter:0.1.0` as normal library
dependencies). The CLI, the Gradle plugin, and the test-runner all sit on top of the same
`transpile()` and `interpret()` functions — nothing is duplicated.

## Building and testing

```
./gradlew build
```

This compiles everything, runs `transpileKotlinToLua` over
`test-runner/src/test/resources/examples` (writing `.lua` files to
`test-runner/build/generated/kt2lua`), then runs the JUnit tests, which execute each generated
`.lua` file with kt2lua's own interpreter and diff the captured output against the matching
`*.expected.txt` file. No Lua install, Node, or JVM Lua library is required.

`.github/workflows/build.yml` runs the same `./gradlew build` on every push/PR to `main`/`master`.

Test coverage: 162 unit tests for the transpiler (`LexerTest`/`ParserTest`/`EmitterTest`), 14 for
the interpreter (`InterpreterTest`), and 6 end-to-end example pairs in `test-runner` that
transpile real `.kt` files and run the output through the interpreter.

## Using the Gradle plugin in a build script

```kotlin
plugins {
    id("kt2lua.transpile")
}

kt2lua {
    sourceDir.set(layout.projectDirectory.dir("src/main/kotlin-scripts"))
    outputDir.set(layout.buildDirectory.dir("generated/kt2lua"))
}
```

This registers a `transpileKotlinToLua` task that transpiles every `.kt` file in `sourceDir`
into a `.lua` file in `outputDir`.

## Using the CLI directly

```
./gradlew :cli:run --args="path/to/script.kt --run"
```

or, after `./gradlew :cli:installDist`, run the installed `cli` binary directly:

```
cli/build/install/cli/bin/cli path/to/script.kt -o out.lua --run
```

Options: `-o/--out <file>` sets the output path (default: `build/dist/<name>.lua`, relative to
the current directory — same `build/` convention the Gradle plugin uses); `--run` executes the
generated Lua immediately afterward using kt2lua's own built-in interpreter (in-process — no
subprocess, no system Lua). `-v/--version` prints the version; an unrecognized `--flag` is a
usage error, not silently treated as an input path. See `--help` for the full option list,
including multi-file mode and `kt2lua-style.properties` (output formatting).

### Multiple files at once (cross-file auto-`require`)

```
cli/build/install/cli/bin/cli ModuleA.kt ModuleB.kt Main.kt -o distDir/
```

Passing more than one input file transpiles them *together* instead of independently: the CLI
sees every file's top-level declarations at once, so a reference in one file to a name declared
at the top level of another (an `object`, a non-private top-level `fun`/`val`/`var`, a `class`)
gets a `require("<thatFile>")` call inserted automatically — no more hand-writing one for every
sibling file a module depends on (see `libs/runescape/examples/prettyui-window-kotlin`, which relies on this
instead of the manual calls an earlier version of it had). `-o`
names an output *directory* in this mode, not a file (defaulting to `build/dist`, same as
single-file mode, when omitted) — each input's own base name becomes `<outDir>/<name>.lua`,
which is also the exact string sibling files will `require()` it as.
Anything that isn't any input file's own top-level declaration is still left alone and treated
as an ambient global, same as single-file mode — this only ever auto-requires a name that
*matches* a known sibling declaration, never guesses. A name declared at the top level of more
than one input file is ambiguous and is never auto-required for either, on purpose (see
`transpileProject`'s doc comment).

The same logic also now backs `TranspileTask` (the Gradle plugin's `transpileKotlinToLua` task),
which already processed a whole `sourceDir` per invocation — it's always been able to see every
file in the batch, so this needed no new entry point, just swapping its per-file `transpile()`
loop for one `transpileProject()` call.

## Feature support

The full, living list of exactly what's supported, what's explicitly out of scope, and what's
planned next — as a checklist, not prose — lives in
[**#1, the Kotlin subset support tracker**][tracker], not here, so this README doesn't have to
be re-read top to bottom every time one more thing gets implemented. The two sections just below
("Class support" and "Try/catch") stay here because they're not status tracking — they explain
*why* those two features are shaped the way they are, and the one real gotcha each has.

[tracker]: https://github.com/Mark7625/rs3-kt-api/issues/1

## Class support, and why it has real caveats

`class Point(val x: Int, val y: Int) { fun describe(): String = "(${this.x}, ${this.y})" }`
compiles to the standard Lua "class" idiom:

```lua
Point = {}
Point.__index = Point
function Point.new(x, y)
  local self = setmetatable({}, Point)
  self.x = x
  self.y = y
  return self
end
function Point:describe()
  return "(" .. tostring(self.x) .. ", " .. tostring(self.y) .. ")"
end
```

`Point(3, 4)` compiles to `Point.new(3, 4)` (named args are reordered into the constructor's
declared order first). The hard part is call sites: Kotlin only ever writes `.` for a method
call, but a Lua method needs `:` so `self` gets passed — `instance:describe()`, not
`instance.describe()` (the latter calls the function with *no* `self` at all, which fails at
runtime the moment the method touches `self.anything`). Since there's no Kotlin-level syntax to
tell "this dot is a method call that needs self" apart from "this dot is a plain function/object
call that doesn't," kt2lua has to work it out statically — and it deliberately only does so for
a few well-defined, *conservative* cases, tracked through the semantic-analysis stage
(`TypeAnalyzer.declareInstance`, see `SemanticAnalysis.kt`):

- `this` inside one of the class's own methods (`this.x`, `this.otherMethod()`)
- a local directly initialized from a constructor call: `val p = Point(3, 4)`
- a function parameter whose declared type names the class: `fun greet(p: Point)`
- a local initialized from a call to a function or method that's known (by an explicit return
  type annotation, or an unannotated expression body that's directly a constructor call) to
  return that class: `val c = a.plus(b)` where `plus(...): Point` or `fun plus(...) = Point(...)`
- a plain `p = Point(5, 6)` reassignment re-tracks `p` the same way a fresh declaration would
- the implicit/first parameter of a `.forEach { }`/`.map { }`/`.filter { }` lambda, when the
  receiver is a `listOf(...)` whose elements all resolved to the *same* class at the point it
  was declared: `val team = listOf(Employee(...), Employee(...))`, then
  `team.forEach { it.describe() }` correctly colon-calls `it:describe()`

Any `.method()` call on a receiver that isn't one of those — a `listOf(...)` of a *mix* of
classes, returned from a block-bodied function with no return-type annotation, stored in a
field and read back out, captured inside a nested lambda from an untracked source, a result
re-derived from `.map { }` (its own element class isn't tracked for further chaining), etc. —
falls back to the same dot-call this
emitter has always produced for everything else, which **will fail at runtime** if that method
is a real class method (not a plain function). This is a conscious, documented trade-off: a
sound but incomplete static resolution, rather than either (a) emitting colon-calls everywhere
(which would break every *existing* dot-call case — objects, modules, external Lua values) or
(b) real type inference (a much bigger undertaking — see the "Roadmap" section of
[issue #1][tracker]).

Still out of scope: interfaces, multiple inheritance, secondary constructors, `init` blocks,
`super` calls from within a method, and constructor parameters without `val`/`var`. `data class`
only changes one thing — an auto-generated `__tostring` matching Kotlin's `toString()` format
(`Point(x=1, y=2)`) when the class doesn't define its own.

### Single-superclass inheritance

`open class Base { open var x = 0; open fun describe() = "base" }` /
`class Derived(val y: Int) : Base() { override fun describe() = "derived" }` compiles to Lua's
standard two-link prototype chain on top of the single-class idiom above:

```lua
Derived = setmetatable({}, { __index = Base })
Derived.__index = Derived
function Derived.new(y)
  local self = setmetatable(Base.new(), Derived)
  self.y = y
  return self
end
function Derived:describe()
  return "derived"
end
```

`setmetatable(Derived, { __index = Base })` lets a lookup that misses on the `Derived` *class
table itself* (an unoverridden method or default, e.g. `Derived.x`) fall through to `Base`;
`Derived.__index = Derived` is the ordinary instance-to-class-table link every class already
has. `.new()` chains the same way, calling `Base.new(args)` first (so inherited property
defaults land) before applying `Derived`'s own primary-constructor params and body properties
on top — each one simply overwrites the inherited default if redeclared via `override`, or adds
a new field if not. `open`/`override` themselves are parsed and then **ignored** — there's
nothing for them to do in codegen, since a redeclared name in a subclass already shadows the
superclass's via the metatable chain above, with zero special-casing needed.

A non-constructor property declaration in a class body (`open var x = 0`, `override var y: Int =
5`) now works too — not just for an inheriting class, any class — compiling to a plain
`self.<name> = <init>` line in `.new()`, after the primary constructor's own params.

`ClassInfo.methodNames` (used to decide dot- vs. colon-calls, see above) is built per-file by
plain single-file [transpile] — so a method call through an instance typed as a subclass only
"sees" an *inherited* (not locally overridden) method as known when the superclass is declared
**earlier in the same file**. A multi-file `transpileProject` call doesn't have that limit: it
runs a cheap pre-pass (`classInfoAcrossFiles` in `ProjectTranspile.kt`) over every input file's
classes *before* transpiling any of them, pre-registering each class's full inherited method set
into every other file's analyzer (`TypeAnalyzer.registerExternalClass`) — so a subclass whose
superclass lives in a *different* file (the common shape: a small per-plugin `open class
RunescapePlugin { ... }` shared across plugins, each plugin's own `Main` subclass in its own
file) still resolves inherited, unoverridden methods to colon-calls correctly, as long as the
superclass is listed no later than its subclass in that one CLI invocation — same order
dependency as the same-file case, just widened to span the whole file list.

## Try/catch, throw, and why `return` inside `try` is a trap

Lua has no native try/catch — the standard idiom, and what this compiles to, is `pcall`:

```lua
local __ok, __err = pcall(function()
  <try body>
end)
if not __ok then
  local e = __err
  <catch body>
end
<finally body>
```

A `try`/`finally` with no `catch` still wraps the try body in the same `pcall`, so `finally`
still runs on error, and re-raises (`error(__err)`) afterward so an uncaught exception keeps
propagating — same "finally always runs; an uncaught exception still propagates" semantics as
real Kotlin.

**The trap**: the try body runs *inside* `pcall`'s anonymous function. A bare `return` directly
inside a `try` returns from *that* function, not the one you wrote the `try` in — Lua has
nothing else `pcall` could call. Assign to a variable declared before the `try` and return it
afterward instead:

```kotlin
// Wrong in this subset: `return` here only exits pcall's wrapper, not f().
fun f(): Int {
    try { return 1 } catch (e: Exception) { return 2 }
}

// Right: no return inside the try/catch bodies at all.
fun f(): Int {
    var result = 0
    try { result = 1 } catch (e: Exception) { result = 2 }
    return result
}
```

Other scope limits: at most **one** `catch` clause (there's no exception-type hierarchy to
dispatch multiple clauses against — the `: Type` annotation on it is parsed and discarded);
`try` is a statement only, not an expression; `throw SomeException("message")` is special-cased
to throw just `"message"` (the "exception class name" is otherwise meaningless here, since
there's no such class), *unless* it names a real declared `class`, in which case that instance
is thrown (and catchable as a table) as-is.
