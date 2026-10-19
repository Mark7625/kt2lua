#!/usr/bin/env python3
"""Compare the compatibility manifests with the real Kotlin stdlib API pages.

Fetches https://kotlinlang.org/api/core/kotlin-stdlib/<package>/ for every manifest under
transpiler/src/main/resources/compatibility/kotlin-stdlib (except the kotlin.lang pseudo-package)
and lists the documented names (types, properties, functions) the manifest has no entry for.

    python .github/scripts/sync-kotlin-stdlib-names.py          # report only, exit 1 if names are missing
    python .github/scripts/sync-kotlin-stdlib-names.py --add    # append the missing names as unsupported

A name counts as present if any manifest entry has that `name`, so entries for specific overloads
or class members keep counting. Added entries use the id `<package>.<name>(*)` for functions and
`<package>.<name>` for types and properties.
"""
import json
import re
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MANIFESTS = ROOT / "transpiler/src/main/resources/compatibility/kotlin-stdlib"
URL = "https://kotlinlang.org/api/core/kotlin-stdlib/{}/"
KINDS = {"Types": "type", "Properties": "property", "Functions": "function"}
IDENTIFIER = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")


def documented_names(package):
    request = urllib.request.Request(URL.format(package), headers={"User-Agent": "kt2lua-compat-sync"})
    html = urllib.request.urlopen(request, timeout=60).read().decode("utf-8")
    found = []
    for section in re.split(r'<h2 class="tableheader">', html)[1:]:
        header = section[: section.index("<")]
        if header not in KINDS:
            raise SystemExit(f"{package}: unexpected section '{header}' on the API page")
        for name in re.findall(r'anchor-label="([^"]+)"', section):
            if not IDENTIFIER.match(name):
                raise SystemExit(f"{package}: unexpected name '{name}'")
            if all(name != existing for existing, _ in found):
                found.append((name, KINDS[header]))
    return found


def main():
    add = "--add" in sys.argv[1:]
    total_missing = 0
    for path in sorted(MANIFESTS.glob("kotlin*.json")):
        text = path.read_text(encoding="utf-8").replace("\r\n", "\n")
        manifest = json.loads(text)
        package = manifest["package"]
        if package == "kotlin.lang":
            continue
        known = {entry["name"] for entry in manifest["declarations"]}
        missing = [(name, kind) for name, kind in documented_names(package) if name not in known]
        print(f"{package}: {len(manifest['declarations'])} entries, {len(missing)} documented names missing")
        total_missing += len(missing)
        if not missing:
            continue
        if not add:
            print("  " + ", ".join(name for name, _ in missing))
            continue
        new_entries = []
        for name, kind in missing:
            entry_id = f"{package}.{name}(*)" if kind == "function" else f"{package}.{name}"
            new_entries.append({"id": entry_id, "name": name, "kind": kind, "status": "unsupported"})
        tail = "\n  ]\n}"
        if not text.rstrip().endswith(tail):
            raise SystemExit(f"{path.name}: unexpected file layout, can't append entries")
        body = text.rstrip()[: -len(tail)].rstrip() + ","
        lines = ",\n".join("    " + json.dumps(e, ensure_ascii=False) for e in new_entries)
        result = body + "\n" + lines + tail + "\n"
        json.loads(result)
        path.write_text(result, encoding="utf-8", newline="\n")
    if total_missing and not add:
        print(f"\n{total_missing} documented names are missing; rerun with --add to append them as unsupported.")
        sys.exit(1)


if __name__ == "__main__":
    main()
