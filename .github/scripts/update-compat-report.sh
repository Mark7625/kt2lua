#!/usr/bin/env bash
set -euo pipefail

cli="./cli/build/install/cli/bin/cli"
if [ ! -x "$cli" ]; then
  echo "error: $cli not found — run './gradlew :cli:installDist' first" >&2
  exit 1
fi

table=$("$cli" compat report table)
badge=$("$cli" compat report badge)
"$cli" compat report issue > compat-issue-body.md

awk -v table="$table" '
  /<!-- compat-report:start -->/ { print; print table; skip=1; next }
  /<!-- compat-report:end -->/ { skip=0 }
  !skip { print }
' README.md > README.md.new
mv README.md.new README.md

awk -v badge="$badge" '{
  s = index($0, "<!-- compat-badge:start -->")
  e = index($0, "<!-- compat-badge:end -->")
  if (s > 0 && e > 0) {
    print substr($0, 1, s - 1) "<!-- compat-badge:start -->" badge "<!-- compat-badge:end -->" substr($0, e + length("<!-- compat-badge:end -->"))
  } else {
    print
  }
}' README.md > README.md.new
mv README.md.new README.md
