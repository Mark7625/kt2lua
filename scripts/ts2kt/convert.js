#!/usr/bin/env node
// Converts rs3-api-ts's ambient .d.ts type declarations into real Kotlin source, for the
// libs/runescape/* publishable API-binding modules. These are TYPE-ONLY stubs (never fed
// through kt2lua, never actually run) meant purely so the real Kotlin compiler can
// type-check plugin code against the real RS3 client API shape — every generated body is a
// placeholder value, not real behavior. See libs/runescape/README.md.
//
// Uses the real TypeScript compiler (from rs3-api-ts's own node_modules) for robust AST-based
// parsing rather than regex — these files have too much structural variety for regex to be
// reliable across ~270 files.

const fs = require("fs");
const path = require("path");

const RS3_API_TS_ROOT = process.argv[2];
const SRC_DIR = process.argv[3];
const OUT_DIR = process.argv[4];
const BASE_PACKAGE = process.argv[5];

if (!RS3_API_TS_ROOT || !SRC_DIR || !OUT_DIR || !BASE_PACKAGE) {
    console.error("usage: node convert.js <rs3-api-ts root> <src .d.ts dir> <output kotlin dir> <base package>");
    process.exit(1);
}

const ts = require(path.join(RS3_API_TS_ROOT, "node_modules", "typescript"));

const warnings = [];
function warn(file, msg) {
    warnings.push(`${file}: ${msg}`);
}

// ---- type mapping -----------------------------------------------------------------------

/**
 * A dedup-by-name collector for declarations hoisted out of a namespace (see
 * [emitNamespaceBody]'s doc comment). Across a *merged* namespace specifically (several
 * source files all contributing to e.g. `namespace id { ... }`), more than one file can
 * happen to nest a same-named interface/enum with a genuinely different shape — without
 * dedup, that's a second colliding Kotlin declaration with the same name. First one wins;
 * the rest are dropped with a warning, same policy as the top-level dedup in [convertPackage].
 */
function makeCollector() {
    const seen = new Set();
    const items = [];
    return {
        items,
        push(name, body, file) {
            if (seen.has(name)) {
                warn(file || "?", `duplicate nested declaration '${name}' hoisted from a namespace (keeping the first one)`);
                return;
            }
            seen.add(name);
            items.push(body);
        },
    };
}

/** Real TS type names that happen to collide with a Kotlin builtin this converter itself
 * relies on (`List`/`Map`/etc. from [mapType]'s `Array`/`Record` mapping) — e.g. the real API
 * has a UI component literally named `List` (`ui/components/List.d.ts`). TS has no builtin
 * type spelled exactly like any of these (arrays are `T[]`/`Array<T>`, never bare `List`), so
 * any TS type reference landing on one of these names unambiguously means the real,
 * colliding declaration, never the Kotlin builtin — safe to always rename. Applied at both the
 * declaration site ([emitInterface]/[emitClass]/[emitEnum]) and every reference site
 * ([simpleName], which every `TypeReference`/heritage lookup in this file goes through).
 */
const RESERVED_TOP_LEVEL_NAMES = new Set(["List", "Map", "Set", "Pair", "String", "Int", "Double", "Boolean", "Unit", "Any", "Array"]);
function disambiguate(name) {
    return RESERVED_TOP_LEVEL_NAMES.has(name) ? `${name}Component` : name;
}

function typeParamsOf(node) {
    return node.typeParameters ? `<${node.typeParameters.map((tp) => tp.name.getText()).join(", ")}>` : "";
}

/** The RHS of a generic `typealias` (e.g. `type SelectionChoice<V> = V | SelectionChoiceOptions<V>`)
 * — [mapType]'s `UnionType` case approximates any non-literal union as just its first member, which
 * for a union like this collapses to the bare type parameter `V` itself. Kotlin rejects a typealias
 * that expands to nothing but a naked type variable ("not a class, interface or object"), so that
 * one case falls back to `Any?` instead — the alias couldn't represent the real union precisely
 * either way. */
function aliasBodyFor(stmt, ctx) {
    const body = mapType(stmt.type, ctx);
    const typeParamNames = new Set((stmt.typeParameters || []).map((tp) => tp.name.getText()));
    return typeParamNames.has(body.trim()) ? "Any?" : body;
}

/** The last segment of a dotted TS name (`ui.AspectRatioMode` -> `AspectRatioMode`), with
 * [disambiguate] applied — see the call site in [mapType]'s `TypeReference` case for why. */
function simpleName(name) {
    const idx = name.lastIndexOf(".");
    return disambiguate(idx === -1 ? name : name.slice(idx + 1));
}

function mapType(node, ctx) {
    if (!node) return "Any?";
    switch (node.kind) {
        case ts.SyntaxKind.StringKeyword: return "String";
        case ts.SyntaxKind.NumberKeyword: return "Int";
        case ts.SyntaxKind.BooleanKeyword: return "Boolean";
        case ts.SyntaxKind.VoidKeyword: return "Unit";
        case ts.SyntaxKind.AnyKeyword: return "Any?";
        case ts.SyntaxKind.UnknownKeyword: return "Any?";
        case ts.SyntaxKind.UndefinedKeyword: return "Any?";
        case ts.SyntaxKind.NullKeyword: return "Any?";
        case ts.SyntaxKind.ObjectKeyword: return "Any?";
        case ts.SyntaxKind.IntersectionType:
            // `XOptions & FlowOptions` — this codebase's standard "merge a widget's own options
            // with the shared layout options every `AddXxx` call accepts" pattern. No Kotlin
            // type can represent a real intersection; take the first member (same pragmatic
            // approximation already used for heritage with several interfaces, and for unions
            // in the `UnionType` case below) — the far more load-bearing part for a generic
            // `AddXxx<V>(..., options?: XOptions<V> & FlowOptions)` signature is keeping `V`
            // itself inferrable from this argument's type, which dropping straight to `Any?`
            // (the previous, pre-this-case fallback) would have broken entirely.
            return mapType(node.types[0], ctx);
        case ts.SyntaxKind.ArrayType:
            return `List<${mapType(node.elementType, ctx)}>`;
        case ts.SyntaxKind.TupleType:
            return "List<Any?>";
        case ts.SyntaxKind.ParenthesizedType:
            return mapType(node.type, ctx);
        case ts.SyntaxKind.LiteralType:
            if (node.literal.kind === ts.SyntaxKind.StringLiteral) return "String";
            return "Any?";
        case ts.SyntaxKind.FunctionType: {
            const params = node.parameters
                .filter((p) => !(p.type && p.type.kind === ts.SyntaxKind.TypeReference && p.type.typeName.getText() === "void") && p.name.getText() !== "this")
                .filter((p) => !p.dotDotDotToken) // drop rest params from functional types; approximate only
                .map((p) => mapType(p.type, ctx));
            const ret = mapType(node.type, ctx);
            return `(${params.join(", ")}) -> ${ret === "Unit" ? "Unit" : ret}`;
        }
        case ts.SyntaxKind.UnionType: {
            const parts = node.types;
            const hasUndefined = parts.some(
                (t) => t.kind === ts.SyntaxKind.UndefinedKeyword || (t.kind === ts.SyntaxKind.LiteralType && t.literal.kind === ts.SyntaxKind.NullKeyword)
            );
            const real = parts.filter((t) => t.kind !== ts.SyntaxKind.UndefinedKeyword);
            if (real.length === 0) return "Any?";
            if (real.every((t) => t.kind === ts.SyntaxKind.LiteralType && t.literal.kind === ts.SyntaxKind.StringLiteral)) {
                return "String" + (hasUndefined ? "?" : "");
            }
            const base = mapType(real[0], ctx);
            if (!hasUndefined || base.endsWith("?")) return base;
            // A bare function type needs parens before the nullability "?", or it reads as
            // "the return type is nullable" instead of "the whole function ref is nullable".
            return base.startsWith("(") ? `(${base})?` : `${base}?`;
        }
        case ts.SyntaxKind.TypeReference: {
            const name = node.typeName.getText();
            if ((name === "Array" || name === "ReadonlyArray") && node.typeArguments && node.typeArguments.length === 1) {
                return `List<${mapType(node.typeArguments[0], ctx)}>`;
            }
            if (name === "Record" && node.typeArguments && node.typeArguments.length === 2) {
                return `Map<${mapType(node.typeArguments[0], ctx)}, ${mapType(node.typeArguments[1], ctx)}>`;
            }
            if ((name === "Promise" || name === "Partial" || name === "Readonly") && node.typeArguments && node.typeArguments.length === 1) {
                // `Partial<T>`/`Readonly<T>` are TS utility types that change property
                // optionality/mutability, not the type itself — this converter already
                // defaults every property to a placeholder regardless, so unwrapping to the
                // inner type is a reasonable, type-check-only approximation.
                return mapType(node.typeArguments[0], ctx);
            }
            if (name === "Omit" && node.typeArguments && node.typeArguments.length === 2) {
                // `Omit<T, K>` drops specific keys from T — no Kotlin equivalent (would need
                // real structural type subtraction), so this just keeps the full T, same
                // approximation philosophy as `Partial`/`Readonly` above.
                return mapType(node.typeArguments[0], ctx);
            }
            if (name === "AssetID") return "Int";
            if (name === "Uppercase" || name === "Lowercase") return "String";
            if (name === "LuaMultiReturn") {
                // A `typescript-to-lua` ambient marker for "this function returns multiple
                // Lua values" (`LuaMultiReturn<[number, number]>`) — no real Kotlin
                // equivalent; approximated as untyped since only the *first* return value
                // would be meaningfully usable from Kotlin anyway.
                return "Any?";
            }
            // A known local/project type: keep just the simple name. TS code commonly
            // qualifies a type with its enclosing namespace (`ui.AspectRatioMode`), but this
            // converter flattens every namespace/category into one Kotlin package of
            // top-level declarations, so the namespace prefix wouldn't resolve to anything —
            // the bare name is assumed unique enough within that package instead.
            const base = simpleName(name);
            if (node.typeArguments && node.typeArguments.length > 0) {
                return `${base}<${node.typeArguments.map((a) => mapType(a, ctx)).join(", ")}>`;
            }
            return base;
        }
        default:
            return "Any?";
    }
}

/** Splits a non-nullable function-type string (`"(A, B) -> C"`) into its parameter-list text and
 * return-type text, matching parens by depth rather than `lastIndexOf("->")` — a param can itself
 * be a function type (`"((A) -> B, C) -> D"`), which would otherwise throw off a naive search. */
function splitFunctionType(type) {
    let depth = 0;
    let closeIdx = -1;
    for (let i = 0; i < type.length; i++) {
        if (type[i] === "(") depth++;
        else if (type[i] === ")") {
            depth--;
            if (depth === 0) {
                closeIdx = i;
                break;
            }
        }
    }
    const paramsStr = type.slice(1, closeIdx);
    const ret = type
        .slice(closeIdx + 1)
        .trim()
        .replace(/^->\s*/, "");
    return { paramsStr, ret };
}

/** Counts comma-separated entries at depth 0 only, so a param whose own type contains commas
 * (`Map<String, Int>`, a nested function type) doesn't get miscounted as multiple params. */
function countTopLevelParams(paramsStr) {
    if (paramsStr.trim() === "") return 0;
    let depth = 0;
    let count = 1;
    for (const ch of paramsStr) {
        if (ch === "(" || ch === "<") depth++;
        else if (ch === ")" || ch === ">") depth--;
        else if (ch === "," && depth === 0) count++;
    }
    return count;
}

/** A syntactically valid placeholder value for [type] — these stubs are never run for real,
 * so correctness of the *value* never matters, only that something type-checks. */
function placeholderFor(type) {
    if (type === "Unit") return null;
    // Checked before the generic `endsWith("?")` nullability check below: a non-nullable
    // function type can still end in "?" when its *return* type is nullable (`(A) -> B?`) —
    // that "?" belongs to B, not to the function type itself, and must still get a real lambda,
    // not a bare `null`. A function type that's *itself* nullable is always wrapped in its own
    // extra pair of parens before the trailing "?" (`((A) -> B)?`, see [mapType]'s `UnionType`
    // case), which is the only case that actually ends in `)?`.
    if (type.startsWith("(")) {
        if (type.endsWith(")?")) return "null";
        const { paramsStr, ret } = splitFunctionType(type);
        const n = countTopLevelParams(paramsStr);
        const retPlaceholder = placeholderFor(ret);
        const body = retPlaceholder === null ? "" : retPlaceholder;
        // A lambda literal with NO declared parameter list only adapts to a target function type
        // of arity 0 (or 1, via implicit `it`) — Kotlin rejects it outright for arity 2+ ("the
        // lambda has no parameters but the function type requires N"). Naming them `_` opts out
        // of that check regardless of arity, which is all a never-run placeholder needs.
        return n === 0 ? `{ ${body} }` : `{ ${Array(n).fill("_").join(", ")} -> ${body} }`;
    }
    if (type.endsWith("?")) return "null";
    if (type === "String") return '""';
    if (type === "Int") return "0";
    if (type === "Double") return "0.0";
    if (type === "Boolean") return "false";
    if (type.startsWith("List<")) return "emptyList()";
    if (type.startsWith("Map<")) return "emptyMap()";
    // An unknown custom type with no obvious placeholder — null is the only thing that always
    // compiles, so fall back to a nullable placeholder (slight fidelity loss, documented).
    return "null";
}

const KNOWN_TYPE_PREFIXES = ["String", "Int", "Double", "Boolean", "Unit", "Any?"];

/**
 * Resolves both the Kotlin type *and* a placeholder value together, so they never disagree —
 * the bug that bit the first version of this script: an unknown custom type (no obvious
 * default we can construct) fell back to a `null` placeholder while the type annotation
 * stayed non-nullable, which doesn't compile. Any type that isn't one of the primitives/
 * collections/function types we know how to default meaningfully gets forced nullable here,
 * since `null` is the only placeholder that always compiles for an arbitrary custom type.
 */
function resolveType(tsTypeNode, ctx) {
    let type = mapType(tsTypeNode, ctx);
    const isKnown =
        KNOWN_TYPE_PREFIXES.includes(type) ||
        type.startsWith("List<") ||
        type.startsWith("Map<") ||
        type.startsWith("(") ||
        type.endsWith("?");
    if (!isKnown) type = `${type}?`;
    return { type, placeholder: placeholderFor(type) };
}

// ---- JSDoc helpers ------------------------------------------------------------------------

function jsDocTags(node) {
    const tags = [];
    const jsDocNodes = ts.getJSDocTags ? ts.getJSDocTags(node) : [];
    for (const t of jsDocNodes) tags.push(t.tagName.getText());
    return tags;
}

function customConstructorName(node) {
    const jsDocNodes = ts.getJSDocTags ? ts.getJSDocTags(node) : [];
    for (const t of jsDocNodes) {
        if (t.tagName.getText() === "customConstructor" && t.comment) {
            return typeof t.comment === "string" ? t.comment.trim() : t.comment.map((c) => c.text).join("").trim();
        }
    }
    return null;
}

// ---- emitting one file ---------------------------------------------------------------------

function emitParams(params, ctx) {
    return params
        .filter((p) => p.name.getText() !== "this")
        .map((p) => {
            const t = mapType(p.type, ctx);
            const name = sanitizeIdent(p.name.getText());
            if (p.dotDotDotToken) return `vararg ${name}: Any?`;
            if (!p.questionToken) return `${name}: ${t}`;
            const nullableT = t.endsWith("?") ? t : t.startsWith("(") ? `(${t})?` : `${t}?`;
            return `${name}: ${nullableT} = null`;
        })
        .join(", ");
}

const KOTLIN_HARD_KEYWORDS = new Set([
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface",
    "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias",
    "typeof", "val", "var", "when", "while",
]);
function sanitizeIdent(name) {
    return KOTLIN_HARD_KEYWORDS.has(name) ? `\`${name}\`` : name;
}

/**
 * The constructor-param equivalent of [emitInterfaceOrClassBody]'s property branch, used by
 * [emitInterface] so every property lands in the primary constructor instead of the class
 * body — see that function's doc comment for why.
 */
function emitInterfaceConstructorParams(members, ctx, inheritedNames) {
    const params = [];
    for (const m of members) {
        if (!(ts.isPropertySignature(m) || ts.isPropertyDeclaration(m))) continue;
        if (inheritedNames && inheritedNames.has(m.name.getText())) continue;
        const name = sanitizeIdent(m.name.getText());
        const { type, placeholder } = resolveType(m.type, ctx);
        const readonly = m.modifiers && m.modifiers.some((mm) => mm.kind === ts.SyntaxKind.ReadonlyKeyword);
        const kw = readonly ? "val" : "var";
        params.push(`${kw} ${name}: ${type} = ${placeholder}`);
    }
    return params;
}

function emitInterfaceOrClassBody(members, ctx, indent, inheritedNames, includeProperties = true) {
    const lines = [];
    const seenMethodSignatures = new Set();
    for (const m of members) {
        if (ts.isPropertySignature(m) || ts.isPropertyDeclaration(m)) {
            if (!includeProperties) continue; // emitInterface puts these in the constructor instead
            const name = sanitizeIdent(m.name.getText());
            // TS lets a subtype narrow an inherited property's type (`XOptions extends YOptions`
            // re-declaring `colour: SpecificColour`); Kotlin requires `open`/`override` for that,
            // which would also require retrofitting the ancestor — skipping the re-declaration is
            // the simpler fix. These are stub-only placeholder values, so losing the narrower type
            // here costs nothing real: the field still exists, just typed as the ancestor declared it.
            if (inheritedNames && inheritedNames.has(m.name.getText())) continue;
            const { type, placeholder } = resolveType(m.type, ctx);
            const readonly = m.modifiers && m.modifiers.some((mm) => mm.kind === ts.SyntaxKind.ReadonlyKeyword);
            const kw = readonly ? "val" : "var";
            lines.push(`${indent}${kw} ${name}: ${type} = ${placeholder}`);
        } else if (ts.isMethodSignature(m) && m.questionToken && !m.typeParameters) {
            // An *optional* method-shorthand interface member (`init?(): void;`) is this
            // codebase's way of declaring a config-object callback hook (`definePlugin({
            // init() {...}, shutdown() {...} })`) — a fixed, non-overridable no-op `fun` (what
            // every other method signature becomes) can't be customized by calling code at all,
            // which defeats the entire point here. A mutable nullable function-typed property
            // can be reassigned (`PluginOptions().apply { init = { ... } }`), the nearest real
            // equivalent to a TS object literal overriding an optional method.
            const name = sanitizeIdent(m.name.getText());
            const paramTypes = m.parameters.filter((p) => p.name.getText() !== "this").map((p) => mapType(p.type, ctx));
            const retType = mapType(m.type, ctx);
            const funcType = `(${paramTypes.join(", ")}) -> ${retType}`;
            lines.push(`${indent}var ${name}: (${funcType})? = null`);
        } else if (ts.isMethodSignature(m) || ts.isMethodDeclaration(m)) {
            const name = sanitizeIdent(m.name.getText());
            const typeParams = typeParamsOf(m);
            const typeParamsPrefix = typeParams ? `${typeParams} ` : "";
            const params = emitParams(m.parameters, ctx);
            const { type: retType, placeholder } = resolveType(m.type, ctx);
            const sig = `${name}(${params})`;
            if (seenMethodSignatures.has(sig)) continue; // duplicate overload text, skip
            seenMethodSignatures.add(sig);
            if (retType === "Unit") {
                lines.push(`${indent}fun ${typeParamsPrefix}${name}(${params}) {}`);
            } else {
                lines.push(`${indent}fun ${typeParamsPrefix}${name}(${params}): ${retType} = ${placeholder}`);
            }
        } else if (ts.isIndexSignatureDeclaration(m)) {
            // TS index signature (`[name: string]: T`) has no direct Kotlin equivalent as a
            // property; approximate with a get(name) function.
            const { type: retType, placeholder } = resolveType(m.type, ctx);
            lines.push(`${indent}operator fun get(name: String): ${retType} = ${placeholder}`);
        } else if (ts.isCallSignatureDeclaration(m) || ts.isConstructSignatureDeclaration(m)) {
            // skip — rare in this codebase, not worth modelling
        }
    }
    return lines;
}

/** All property names declared anywhere in [node]'s heritage chain, via [ctx.typeRegistry]
 * (built in [convertPackage] from every top-level interface/class) — used to skip re-declaring
 * an inherited property that TS lets a subtype narrow but Kotlin can't without retrofitting
 * `open`/`override` onto a chain of classes this converter emits independently of each other. */
function getAncestorMemberNames(node, ctx, seen = new Set()) {
    const result = new Set();
    if (!node.heritageClauses || !ctx.typeRegistry) return result;
    // Only the *first* heritage type ever actually becomes `: heritage[0]()` in the emitted
    // Kotlin (see [emitInterface]'s own comment — Kotlin can't extend several classes, so every
    // heritage type past the first is dropped entirely, its members never merged in any way).
    // Walking every heritage type here instead of just that one — the bug this replaces — meant
    // a property declared on, say, a TS interface's 2nd/3rd/4th `extends` target (`PanelOptions
    // extends PositionOptions, SizeOptions, HostOptions, RowBackgroundOptions`) got treated as
    // "already inherited" and skipped from the subtype's own constructor, even though that
    // ancestor was never actually extended — silently losing real fields like `PanelOptions`'s
    // own `width`/`height` (declared there specifically to narrow `SizeOptions`'s) entirely.
    const allTypes = node.heritageClauses.flatMap((h) => h.types);
    if (allTypes.length === 0) return result;
    const t = allTypes[0];
    // `extends globalThis.Text` — strip the qualifier; the registry is keyed by simple
    // name only (see [simpleName]'s own reasoning for why qualifiers never resolve here).
    // Known gap: when the *current* package also declares its own same-simple-named
    // type (as `prettyui` does for `Text` — `globalThis.Text` means core's ambient
    // component, a different type), this resolves to whichever one the registry
    // happens to hold, not necessarily the cross-package one — fixing that needs the
    // registry to track types per-package, not just by simple name.
    const baseName = t.expression.getText().replace(/^globalThis\./, "");
    if (seen.has(baseName)) return result;
    seen.add(baseName);
    const baseNode = ctx.typeRegistry.get(baseName);
    if (!baseNode) return result;
    for (const m of baseNode.members) {
        if ((ts.isPropertySignature(m) || ts.isPropertyDeclaration(m)) && m.name) result.add(m.name.getText());
    }
    for (const n of getAncestorMemberNames(baseNode, ctx, seen)) result.add(n);
    return result;
}

/**
 * This codebase's widest-reaching TS declaration-merging pattern: a UI component is declared as
 * `class Window { ...own instance members... }` plus a same-named `interface Window extends
 * ContentHost, Destroyable, ... {}` right below it — real TS merges those into one type, so the
 * class instance picks up every mixin's members (`AddTitle`, `Destroy`, etc.) despite declaring
 * none of them itself. Pervasive across prettyui (every widget follows this shape). Without
 * handling it, [emitNamespaceBody]'s plain hoist-by-name dedup keeps only whichever of the two
 * same-named nodes it saw first and silently drops the other — which for `class Window` means
 * losing literally every content-adding method, since the class body alone declares none.
 * Flattens every mixin's members (recursively, through the mixin's own heritage) directly into
 * the class body instead, since Kotlin has no multiple-inheritance equivalent to fall back on.
 */
function collectMixinMembers(interfaceNode, ctx, seenNames, visited = new Set()) {
    const extra = [];
    if (!interfaceNode.heritageClauses) return extra;
    for (const h of interfaceNode.heritageClauses) {
        for (const t of h.types) {
            const baseName = t.expression.getText().replace(/^globalThis\./, "");
            if (visited.has(baseName)) continue;
            visited.add(baseName);
            const baseNode = ctx.typeRegistry && ctx.typeRegistry.get(baseName);
            if (!baseNode) continue;
            for (const m of baseNode.members) {
                if (m.name && seenNames.has(m.name.getText())) continue;
                if (m.name) seenNames.add(m.name.getText());
                extra.push(m);
            }
            extra.push(...collectMixinMembers(baseNode, ctx, seenNames, visited));
        }
    }
    return extra;
}

function emitMergedClassInterface(classNode, interfaceNode, ctx, hoistPrefix = "") {
    const seenNames = new Set(classNode.members.filter((m) => m.name).map((m) => m.name.getText()));
    const extraMembers = collectMixinMembers(interfaceNode, ctx, seenNames);
    return emitClass(classNode, ctx, hoistPrefix, extraMembers);
}

function emitInterface(node, ctx, hoistPrefix = "") {
    const name = hoistPrefix + disambiguate(node.name.getText());
    const typeParams = typeParamsOf(node);
    const heritage = [];
    if (node.heritageClauses) {
        for (const h of node.heritageClauses) {
            for (const t of h.types) {
                const base = simpleName(t.expression.getText());
                // `extends AssetIdLookup<"Achievement">` — the base type's own type params
                // (see emitInterface's own `typeParams` for the declaring side) need an
                // argument supplied here too, same as any other generic reference.
                const args = t.typeArguments && t.typeArguments.length > 0 ? `<${t.typeArguments.map((a) => mapType(a, ctx)).join(", ")}>` : "";
                heritage.push(`${base}${args}`);
            }
        }
    }
    const inheritedNames = getAncestorMemberNames(node, ctx);
    // Properties go in the primary constructor (`class X(var a: T = ..., var b: U = ...)`),
    // not the class body (`class X { var a: T = ...; var b: U = ... }`) — the real TS interface
    // these model almost always stands in for an inline object-literal parameter (`AddXxx(options?:
    // XOptions)`, called as `AddXxx({ a: ..., b: ... })`), and a Kotlin class only round-trips
    // through kt2lua's PascalCase-all-named-args record-literal trick (the same mechanism
    // `listOf`/`mapOf` use — see root README) when it's actually *constructed* that way, i.e.
    // has real constructor parameters. A body-only property (no primary constructor) compiles
    // `X()` to a call to a Lua global function that was never defined, which crashes at runtime.
    const ctorParams = emitInterfaceConstructorParams(node.members, ctx, inheritedNames);
    const body = emitInterfaceOrClassBody(node.members, ctx, "    ", inheritedNames, false);
    // Kotlin only allows extending one class (unlike TS interfaces, which can extend several) —
    // take the first; the rest just lose their members in this stub, a known fidelity gap.
    const extendsClause = heritage.length > 0 ? ` : ${heritage[0]}()` : "";
    // `open`: these classes stand in for real client-provided interfaces, and other generated
    // classes extend them (`X extends Y` in the real TS) — Kotlin classes are final by default.
    const lines = [`open class ${name}${typeParams}(${ctorParams.join(", ")})${extendsClause} {`];
    lines.push(...body);
    lines.push("}");
    return lines.join("\n");
}

/**
 * TS commonly merges `declare enum X { ... }` with a same-named `declare namespace X { function
 * FromID(...): X; }` — real TS declaration merging. Kotlin has no such merging, but a
 * `companion object` is accessed with the exact same `X.member(...)` call syntax, so
 * [mergedNamespace]'s members (if any) are folded directly into one instead of becoming a
 * separately emitted (and name-colliding) top-level `object X`. When there's no real namespace
 * to merge, a placeholder `FromID` is still added so the shape matches the rest of the API.
 */
function emitEnum(node, mergedNamespaces, ctx, extraTopLevel, hoistPrefix = "") {
    const name = hoistPrefix + disambiguate(node.name.getText());
    const memberNames = node.members.map((m) => m.name.getText());
    const bodyIndent = "        ";
    const mergedStatements = (mergedNamespaces || []).flatMap((ns) => ns.body.statements);
    const nsLines = mergedStatements.length > 0 ? emitNamespaceBody(mergedStatements, ctx, bodyIndent, extraTopLevel || makeCollector()) : [];
    const lines = [`enum class ${name} {`, `    ${memberNames.join(", ")};`, "", "    companion object {"];
    if (nsLines.length > 0) {
        lines.push(...nsLines);
    } else {
        lines.push(`${bodyIndent}fun FromID(id: Int): ${name} = ${name}.values()[0]`);
    }
    lines.push("    }");
    lines.push("}");
    return lines.join("\n");
}

/**
 * [extraTopLevel] collects declarations that have to be *hoisted* out of the nested `object`
 * this namespace would otherwise become: an `enum`/`interface` nested inside `namespace X {
 * ... }` (e.g. `namespace ui { enum AspectRatioMode {...} }`) still gets referenced elsewhere
 * as the bare qualified name `ui.AspectRatioMode` — and since [mapType]'s `TypeReference` case
 * strips every namespace qualifier down to the simple name (this converter flattens every
 * category into one package of top-level declarations), a type like that has to actually
 * *exist* as a top-level declaration for those references to resolve, not stay nested inside
 * `object ui`. Namespace-level functions/consts don't have this problem — nothing generated
 * here ever references a *value* through its qualified path, only types — so those stay
 * nested as `object` members same as always.
 */
function emitNamespaceBody(statements, ctx, indent, extraTopLevel, nsName) {
    const lines = [];
    // `namespace id { interface Sprite extends AssetIdLookup<"Sprite"> {} ... }` is the
    // asset-ID-lookup marker pattern — and several of its category names (`Interface`, `Sprite`,
    // ...) collide with genuinely distinct *global* types of the same bare name (e.g. the real
    // UI `interface Sprite extends Component`) once every namespace is flattened to one package.
    // TS keeps these apart via the `id.` qualifier; prefixing hoisted `id` members with `Id`
    // (matching this codebase's established `IdXxx` naming) keeps them apart here too.
    const hoistPrefix = nsName === "id" ? "Id" : "";
    // Members of *this same* namespace (e.g. `const Achievement: Achievement;` inside
    // `namespace id`) reference the just-hoisted-and-prefixed sibling types by their bare
    // original name — rewrite those references too, or they'd point at a type that no longer
    // exists under that name.
    const localHoisted = new Set();
    if (hoistPrefix) {
        for (const stmt of statements) {
            if (
                ts.isEnumDeclaration(stmt) ||
                ts.isInterfaceDeclaration(stmt) ||
                (ts.isClassDeclaration(stmt) && stmt.name) ||
                ts.isTypeAliasDeclaration(stmt)
            ) {
                localHoisted.add(disambiguate(stmt.name.getText()));
            }
        }
    }
    const applyHoistPrefix = (typeStr) => {
        if (localHoisted.size === 0) return typeStr;
        let result = typeStr;
        for (const n of localHoisted) result = result.replace(new RegExp(`\\b${n}\\b`, "g"), hoistPrefix + n);
        return result;
    };
    // `class Window { ...own members... }` plus a same-named `interface Window extends
    // ContentHost, Destroyable, ... {}` right below it (see [emitMergedClassInterface]) — found
    // up front so either branch below can produce the same merged body regardless of which of
    // the two same-named statements it hits first.
    const classesByLocalName = new Map();
    const interfacesByLocalName = new Map();
    for (const stmt of statements) {
        if (ts.isClassDeclaration(stmt) && stmt.name) classesByLocalName.set(stmt.name.getText(), stmt);
        else if (ts.isInterfaceDeclaration(stmt) && !interfacesByLocalName.has(stmt.name.getText())) {
            interfacesByLocalName.set(stmt.name.getText(), stmt);
        }
    }
    for (const stmt of statements) {
        if (ts.isFunctionDeclaration(stmt) && stmt.name) {
            const name = sanitizeIdent(stmt.name.getText());
            const typeParams = typeParamsOf(stmt);
            const typeParamsPrefix = typeParams ? `${typeParams} ` : "";
            const params = emitParams(stmt.parameters, ctx);
            const { type: retType, placeholder } = resolveType(stmt.type, ctx);
            const fixedRetType = applyHoistPrefix(retType);
            if (fixedRetType === "Unit") {
                lines.push(`${indent}fun ${typeParamsPrefix}${name}(${params}) {}`);
            } else {
                lines.push(`${indent}fun ${typeParamsPrefix}${name}(${params}): ${fixedRetType} = ${placeholder}`);
            }
        } else if (ts.isVariableStatement(stmt)) {
            for (const decl of stmt.declarationList.declarations) {
                const name = sanitizeIdent(decl.name.getText());
                const { type, placeholder } = resolveType(decl.type, ctx);
                lines.push(`${indent}val ${name}: ${applyHoistPrefix(type)} = ${placeholder}`);
            }
        } else if (ts.isModuleDeclaration(stmt)) {
            const nested = emitModuleDeclaration(stmt, ctx, indent, extraTopLevel);
            if (nested) lines.push(nested);
        } else if (ts.isEnumDeclaration(stmt)) {
            const name = stmt.name.getText();
            extraTopLevel.push(name, emitEnum(stmt, null, ctx, null, hoistPrefix));
        } else if (ts.isInterfaceDeclaration(stmt)) {
            const name = stmt.name.getText();
            // Merged pair: handled once from the class branch below, so this side just skips —
            // otherwise both branches would push the same name and the dedup collector's warning
            // would misleadingly read as a real collision rather than a known, intended merge.
            if (classesByLocalName.has(name)) continue;
            extraTopLevel.push(name, emitInterface(stmt, ctx, hoistPrefix));
        } else if (ts.isClassDeclaration(stmt) && stmt.name) {
            const name = stmt.name.getText();
            const interfaceNode = interfacesByLocalName.get(name);
            const body = interfaceNode ? emitMergedClassInterface(stmt, interfaceNode, ctx, hoistPrefix) : emitClass(stmt, ctx, hoistPrefix);
            extraTopLevel.push(name, body);
        } else if (ts.isTypeAliasDeclaration(stmt)) {
            // Just as common nested as enums/interfaces in this codebase (e.g. `namespace
            // prettyui { type Colour = number; ... }`) — same hoisting reasoning applies.
            const name = stmt.name.getText();
            const aliasName = hoistPrefix + disambiguate(name);
            extraTopLevel.push(name, `typealias ${aliasName}${typeParamsOf(stmt)} = ${aliasBodyFor(stmt, ctx)}`);
        }
        // other nested statement kinds inside namespaces: skip silently, genuinely rare.
    }
    return lines;
}

function emitModuleDeclaration(node, ctx, indent, extraTopLevel) {
    if (!node.body || !ts.isModuleBlock(node.body)) return null;
    const name = disambiguate(node.name.getText());
    const body = emitNamespaceBody(node.body.statements, ctx, indent + "    ", extraTopLevel, node.name.getText());
    if (body.length === 0) return null;
    return [`${indent}object ${sanitizeIdent(name)} {`, ...body, `${indent}}`].join("\n");
}

/** Same as [emitModuleDeclaration], but for a namespace that's declared across *multiple*
 * files under the same name (real TS declaration merging across files — very common for e.g.
 * `namespace ui { ... }`, augmented once per UI component file) — [nodes] are every
 * same-named `ModuleDeclaration` found across the whole category being converted, and their
 * bodies are concatenated into one before emitting, so only a single `object X` results. */
function emitMergedModuleDeclaration(name, nodes, ctx, extraTopLevel, wrapperName = name) {
    const statements = nodes.flatMap((n) => (n.body && ts.isModuleBlock(n.body) ? n.body.statements : []));
    const body = emitNamespaceBody(statements, ctx, "    ", extraTopLevel, name);
    if (body.length === 0) return null;
    return [`object ${sanitizeIdent(disambiguate(wrapperName))} {`, ...body, "}"].join("\n");
}

function emitClass(node, ctx, hoistPrefix = "", extraMembers = []) {
    const name = hoistPrefix + disambiguate(node.name.getText());
    const typeParams = typeParamsOf(node);
    const ctorNode = node.members.find((m) => ts.isConstructorDeclaration(m));
    const customNew = customConstructorName(node);
    const instanceMembers = node.members
        .filter((m) => !ts.isConstructorDeclaration(m) && !(m.modifiers && m.modifiers.some((mm) => mm.kind === ts.SyntaxKind.StaticKeyword)))
        .concat(extraMembers);
    const staticMembers = node.members.filter(
        (m) => !ts.isConstructorDeclaration(m) && m.modifiers && m.modifiers.some((mm) => mm.kind === ts.SyntaxKind.StaticKeyword)
    );
    const ctorParams = ctorNode ? emitParams(ctorNode.parameters, ctx) : "";
    const ctorVisibility = customNew ? "private " : "";
    const inheritedNames = getAncestorMemberNames(node, ctx);
    const lines = [`open class ${name}${typeParams} ${ctorVisibility}constructor(${ctorParams}) {`];
    lines.push(...emitInterfaceOrClassBody(instanceMembers, ctx, "    ", inheritedNames));
    if (customNew || staticMembers.length > 0) {
        lines.push("    companion object {");
        if (customNew) {
            const argNames = ctorNode ? ctorNode.parameters.filter((p) => p.name.getText() !== "this").map((p) => sanitizeIdent(p.name.getText())) : [];
            // A generic class's custom-constructor factory needs its own type params too
            // (`fun <V> new(...): RadioButton<V> = RadioButton(...)`), distinct from the
            // class's own `<V>` — a companion object can't see its enclosing class's type
            // parameters at all.
            const typeParamsPrefix = typeParams ? `${typeParams} ` : "";
            lines.push(`        fun ${typeParamsPrefix}new(${ctorParams}): ${name}${typeParams} = ${name}${typeParams}(${argNames.join(", ")})`);
        }
        lines.push(...emitInterfaceOrClassBody(staticMembers, ctx, "        "));
        lines.push("    }");
    }
    lines.push("}");
    return lines.join("\n");
}

// ---- directory walk --------------------------------------------------------------------

function listDtsFiles(dir) {
    const result = [];
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
        const full = path.join(dir, entry.name);
        if (entry.isDirectory()) result.push(...listDtsFiles(full));
        else if (entry.name.endsWith(".d.ts")) result.push(full);
    }
    return result;
}

function kotlinFileNameFor(tsFile) {
    const base = path.basename(tsFile, ".d.ts");
    // PascalCase-ify a hyphenated/lowercase file name (ids/* use kebab-case).
    return base
        .split(/[-_]/)
        .map((s) => s.charAt(0).toUpperCase() + s.slice(1))
        .join("");
}

/**
 * Converts one whole package's worth of .d.ts files (everything landing in the same flattened
 * Kotlin package — i.e. one category) as a single two-pass unit, *not* file by file:
 *
 * 1. Parse every file, collect every top-level statement into one combined pool, remembering
 *    which file each came from (for naming the eventual .kt file after).
 * 2. Group by (kind, name) across *all* those files and merge/dedupe before emitting anything —
 *    this is what makes cross-file TS declaration merging (`namespace ui {...}` augmented once
 *    per file, which is common in this codebase) produce one Kotlin `object ui`, not N
 *    colliding ones.
 *
 * Within a group: namespaces merge (concatenate every file's body); enums/interfaces/classes/
 * functions/vars take the *first* occurrence and warn on the rest (duplicate declarations with
 * genuinely different content are rare here and not worth modelling — see the warnings list).
 */
function convertPackage(files, ctx, pkg) {
    const parsed = files.map((f) => ({
        file: f,
        source: ts.createSourceFile(f, fs.readFileSync(f, "utf8"), ts.ScriptTarget.Latest, true),
    }));

    const enumsByName = new Map(); // name -> { node, file }
    const namespacesByName = new Map(); // name -> [{ node, file }]
    const interfacesByName = new Map();
    const classesByName = new Map();
    const looseByFile = new Map(); // file -> [{ kind: 'fn'|'var'|'typealias', node }]

    for (const { file, source } of parsed) {
        for (const stmt of source.statements) {
            if (ts.isEnumDeclaration(stmt)) {
                const name = stmt.name.getText();
                if (!enumsByName.has(name)) enumsByName.set(name, { node: stmt, file });
                else warn(file, `duplicate enum '${name}' (keeping the one from ${enumsByName.get(name).file})`);
            } else if (ts.isModuleDeclaration(stmt)) {
                const name = stmt.name.getText();
                if (!namespacesByName.has(name)) namespacesByName.set(name, []);
                namespacesByName.get(name).push({ node: stmt, file });
            } else if (ts.isInterfaceDeclaration(stmt)) {
                const name = stmt.name.getText();
                if (!interfacesByName.has(name)) interfacesByName.set(name, { node: stmt, file });
                else warn(file, `duplicate interface '${name}' (keeping the one from ${interfacesByName.get(name).file})`);
            } else if (ts.isClassDeclaration(stmt) && stmt.name) {
                const name = stmt.name.getText();
                if (!classesByName.has(name)) classesByName.set(name, { node: stmt, file });
                else warn(file, `duplicate class '${name}' (keeping the one from ${classesByName.get(name).file})`);
            } else if (ts.isFunctionDeclaration(stmt) && stmt.name) {
                if (!looseByFile.has(file)) looseByFile.set(file, []);
                looseByFile.get(file).push({ kind: "fn", node: stmt });
            } else if (ts.isVariableStatement(stmt)) {
                if (!looseByFile.has(file)) looseByFile.set(file, []);
                looseByFile.get(file).push({ kind: "var", node: stmt });
            } else if (ts.isTypeAliasDeclaration(stmt)) {
                // A real `typealias`, generic or not, via the same [mapType] every other type
                // reference goes through — including the common "just aliases a primitive"
                // case (`type Colour = number`) that silently vanishing used to leave as a
                // dangling unresolved reference everywhere it was used.
                if (!looseByFile.has(file)) looseByFile.set(file, []);
                looseByFile.get(file).push({ kind: "typealias", node: stmt });
            }
            // /// <reference> triples, ambient module augmentations etc: ignored, not statements.
        }
    }

    // Enum+namespace declaration merging (same name, same file-set) — resolve before emitting
    // either side, so the namespace's members land in the enum's companion, not a colliding
    // separate `object`.
    const consumedNamespaceNames = new Set();
    const outputs = []; // { name, file, body }

    // Lets [getAncestorMemberNames] walk a heritage chain by name during Pass 2 emission. Most
    // of this codebase's option/heritage interfaces (`XOptions extends YOptions`) live *nested*
    // inside a namespace (`namespace prettyui { interface ColourPickerOptions extends
    // SimpleButtonOptions {...} }`), not at the top level, so the registry needs those too —
    // walked recursively since a namespace can nest another namespace.
    ctx.typeRegistry = new Map();
    for (const [name, { node }] of interfacesByName) ctx.typeRegistry.set(name, node);
    for (const [name, { node }] of classesByName) ctx.typeRegistry.set(name, node);
    const registerNested = (statements) => {
        for (const stmt of statements) {
            if (ts.isInterfaceDeclaration(stmt) || (ts.isClassDeclaration(stmt) && stmt.name)) {
                if (!ctx.typeRegistry.has(stmt.name.getText())) ctx.typeRegistry.set(stmt.name.getText(), stmt);
            } else if (ts.isModuleDeclaration(stmt) && stmt.body && ts.isModuleBlock(stmt.body)) {
                registerNested(stmt.body.statements);
            }
        }
    };
    for (const entries of namespacesByName.values()) {
        for (const { node } of entries) {
            if (node.body && ts.isModuleBlock(node.body)) registerNested(node.body.statements);
        }
    }
    // One real cross-package mixin reference exists (`prettyui`'s `interface Text extends
    // globalThis.Text {}`, merging in `core`'s own ambient UI `Text` component) — `core` isn't
    // otherwise scanned when converting a different package (each `convertPackage` call is its
    // own process with its own registry), so without this, that lookup would silently miss and
    // drop every member `globalThis.Text` contributes (here, `content` — exactly the field
    // `TextLabel.content = ...`-style call sites need). Scanning it unconditionally is harmless
    // when SRC_DIR already *is* core/types (same files, Map.set is idempotent).
    const coreTypesDir = path.join(RS3_API_TS_ROOT, "packages", "core", "types");
    if (fs.existsSync(coreTypesDir)) {
        for (const coreFile of listDtsFiles(coreTypesDir)) {
            const coreSource = ts.createSourceFile(coreFile, fs.readFileSync(coreFile, "utf8"), ts.ScriptTarget.Latest, true);
            registerNested(coreSource.statements);
        }
    }

    for (const [name, { node, file }] of enumsByName) {
        const mergedNs = namespacesByName.get(name);
        if (mergedNs) consumedNamespaceNames.add(name);
        const extraTopLevel = makeCollector();
        const body = emitEnum(node, mergedNs ? mergedNs.map((m) => m.node) : null, ctx, extraTopLevel);
        outputs.push({ name, file, body: [body, ...extraTopLevel.items].join("\n\n") });
    }
    // Same `class X {} / interface X extends Mixin {}` merge pattern as [emitNamespaceBody]
    // handles for nested declarations — see [emitMergedClassInterface] — but for a same-named
    // pair that both happen to sit at the top level of a file instead of inside a namespace.
    // The merge is handled once, from the class side; a merged name is skipped on the interface
    // side so it doesn't *also* independently emit (and duplicate) the same declaration.
    for (const [name, { node, file }] of interfacesByName) {
        if (classesByName.has(name)) continue;
        outputs.push({ name, file, body: emitInterface(node, ctx) });
    }
    for (const [name, { node, file }] of classesByName) {
        const interfaceEntry = interfacesByName.get(name);
        const body = interfaceEntry ? emitMergedClassInterface(node, interfaceEntry.node, ctx) : emitClass(node, ctx);
        outputs.push({ name, file, body });
    }
    for (const [name, entries] of namespacesByName) {
        if (consumedNamespaceNames.has(name)) continue;
        const extraTopLevel = makeCollector();
        // A namespace whose name exactly equals the target Kotlin package's *own full name*
        // (e.g. `namespace prettyui { ... }` converted into a bare, single-segment package
        // `prettyui` — not a multi-segment one like `com.fluxious.runescape.prettyui`, where
        // this never fires) would otherwise wrap its members in `object prettyui { ... }` — a
        // declaration literally named the same as the package it lives in. A consumer outside
        // that package writing the fully-qualified `prettyui.Window.new(...)` then can't tell
        // whether `prettyui` means the package or this wildcard-imported object: genuinely
        // ambiguous specifically because the package is bare enough to spell identically to the
        // object. Renaming just the wrapper's own declared name (not its members, not the
        // hoisting/merging logic — see [emitMergedModuleDeclaration]'s `wrapperName` param)
        // keeps every other emission path identical, unlike an earlier attempt at this fix that
        // tried to emit the namespace's body bare (no wrapper at all) and broke nested-type
        // hoisting badly enough to be reverted.
        const wrapperName = name === pkg ? `${name}Namespace` : name;
        const body = emitMergedModuleDeclaration(name, entries.map((e) => e.node), ctx, extraTopLevel, wrapperName);
        if (body) outputs.push({ name, file: entries[0].file, body: [body, ...extraTopLevel.items].join("\n\n") });
    }
    for (const [file, entries] of looseByFile) {
        const lines = [];
        for (const { kind, node: stmt } of entries) {
            try {
                if (kind === "fn") {
                    const name = sanitizeIdent(stmt.name.getText());
                    const typeParams = typeParamsOf(stmt);
                    const typeParamsPrefix = typeParams ? `${typeParams} ` : "";
                    const params = emitParams(stmt.parameters, ctx);
                    const { type: retType, placeholder } = resolveType(stmt.type, ctx);
                    lines.push(retType === "Unit" ? `fun ${typeParamsPrefix}${name}(${params}) {}` : `fun ${typeParamsPrefix}${name}(${params}): ${retType} = ${placeholder}`);
                } else if (kind === "var") {
                    for (const decl of stmt.declarationList.declarations) {
                        const name = sanitizeIdent(decl.name.getText());
                        const { type, placeholder } = resolveType(decl.type, ctx);
                        lines.push(`val ${name}: ${type} = ${placeholder}`);
                    }
                } else if (kind === "typealias") {
                    lines.push(`typealias ${disambiguate(stmt.name.getText())}${typeParamsOf(stmt)} = ${aliasBodyFor(stmt, ctx)}`);
                }
            } catch (e) {
                warn(file, `failed to convert a loose top-level statement: ${e.message}`);
            }
        }
        if (lines.length > 0) outputs.push({ name: kotlinFileNameFor(file), file, body: lines.join("\n\n") });
    }

    const pkgDir = path.join(OUT_DIR, ...pkg.split("."));
    fs.mkdirSync(pkgDir, { recursive: true });
    // `extended` and `prettyui`'s real TS types reference `core`'s (e.g. prettyui's `Window`
    // methods take a `Component`) — real TS project references resolve that; a standalone
    // Kotlin file needs an explicit import instead. A wildcard import is harmless even where
    // unused, so this is applied unconditionally rather than tracking which specific names a
    // given file actually needs from core.
    const CORE_PACKAGE = "com.fluxious.runescape.core";
    const crossPackageImport = pkg === CORE_PACKAGE ? "" : `import ${CORE_PACKAGE}.*\n\n`;
    // Two different `outputs` entries can legitimately land on the same Kotlin file name — e.g.
    // `ui/Component.d.ts` contributes both `interface Component` (named after the declaration)
    // and a loose top-level `type ComponentHookHandler = ...` (named after the *source file* via
    // [kotlinFileNameFor], which happens to also be "Component"). Grouping by name and
    // concatenating instead of writing each entry independently avoids one silently clobbering
    // the other on disk.
    const byName = new Map(); // name -> { bodies: string[], file }
    for (const { name, file, body } of outputs) {
        if (!body || body.trim().length === 0) continue;
        if (!byName.has(name)) byName.set(name, { bodies: [], file });
        byName.get(name).bodies.push(body);
    }
    let converted = 0;
    for (const [name, { bodies, file }] of byName) {
        const rel = path.relative(SRC_DIR, file).replace(/\\/g, "/");
        const header = `package ${pkg}\n\n${crossPackageImport}// Auto-generated from rs3-api-ts (incl. ${rel}) by scripts/ts2kt/convert.js.\n// Ambient type stub only — never fed through kt2lua, bodies are placeholders. Do not hand-edit;\n// re-run the converter instead.\n\n`;
        fs.writeFileSync(path.join(pkgDir, `${name}.kt`), header + bodies.join("\n\n") + "\n");
        converted++;
    }
    return converted;
}

// No filename-based exclusion for barrel/index files: a pure `/// <reference>` barrel just
// produces no top-level statements naturally and gets skipped above anyway — and not every
// "index.d.ts" is a barrel (extended's real API happens to live in one).
const files = listDtsFiles(SRC_DIR);
console.log(`Found ${files.length} .d.ts files under ${SRC_DIR}`);
const converted = convertPackage(files, {}, BASE_PACKAGE);
console.log(`Converted into ${converted} Kotlin files.`);
if (warnings.length > 0) {
    console.log(`\n${warnings.length} warnings:`);
    for (const w of warnings.slice(0, 50)) console.log(`  ${w}`);
    if (warnings.length > 50) console.log(`  ... and ${warnings.length - 50} more`);
}
