#!/usr/bin/env bash
set -euo pipefail

version="$1"

sed -i "s/^version=.*/version=$version/" gradle.properties
sed -i -E \
  -e "s|(id\(\"io\.github\.mark7625\.kt2lua\"\) version \")[^\"]*\"|\1$version\"|" \
  -e "s|(io\.github\.mark7625:kt2lua:)[^\"\` ]*|\1$version|" \
  README.md
