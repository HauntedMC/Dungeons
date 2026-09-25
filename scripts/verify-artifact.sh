#!/usr/bin/env bash
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
version="$(sed -n 's/.*<revision>\([0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*\)<\/revision>.*/\1/p' pom.xml | head -n 1)"
[[ -n "$version" ]] || { echo 'Missing semantic Maven revision' >&2; exit 1; }
artifact="target/Dungeons-$version.jar"
[[ -f "$artifact" ]] || { echo "Missing distributable $artifact" >&2; exit 1; }
entries="$(jar tf "$artifact")"
descriptor="$(unzip -p "$artifact" plugin.yml)"
grep -Fxq "version: $version" <<<"$descriptor"
grep -Fq 'nl/hauntedmc/dungeons/plugin/DungeonsPlugin.class' <<<"$entries"
grep -Fq 'nl/hauntedmc/dungeons/libs/nbtapi/NBT.class' <<<"$entries"
if grep -Eq '^de/tr7zw/changeme/nbtapi/|^org/bukkit/|^io/papermc/paper/' <<<"$entries"; then
  echo 'Plugin jar contains unrelocated NBT or provided Paper classes' >&2
  exit 1
fi
echo "Artifact audit passed: $artifact"
