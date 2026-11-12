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
grep -Fq 'nl/hauntedmc/theme/HauntedMcColor.class' <<<"$entries"
grep -Fq 'nl/hauntedmc/theme/featureframework/HauntedMcTheme.class' <<<"$entries"
grep -Fq 'nl/hauntedmc/featureframework/localization/ComponentLocalization.class' <<<"$entries"
grep -Fxq 'nl/hauntedmc/featureframework/hud/HudService.class' <<<"$entries"
grep -Fxq 'nl/hauntedmc/dungeons/api/DungeonExperienceApi.class' <<<"$entries"
grep -Fq 'lang/messages.yml' <<<"$entries"
grep -Fxq 'lang/messages_EN.yml' <<<"$entries"
if grep -Fxq 'messages.yml' <<<"$entries"; then
  echo 'Plugin jar contains obsolete root messages.yml; rebuild from a clean target directory' >&2
  exit 1
fi
if grep -Eq '^de/tr7zw/changeme/nbtapi/|^org/bukkit/|^io/papermc/paper/' <<<"$entries"; then
  echo 'Plugin jar contains unrelocated NBT or provided Paper classes' >&2
  exit 1
fi
if grep -Eq '^nl/hauntedmc/(dataprovider|dataregistry|serverfeatures|proxyfeatures)/|^org/hibernate/|^jakarta/persistence/' <<<"$entries"; then
  echo 'Plugin jar contains provided HauntedMC database APIs or ORM implementation classes' >&2
  exit 1
fi
if grep -Eq '^nl/hauntedmc/dungeons/acceptance/|^org/junit/|^org/mockito/' <<<"$entries"; then
  echo 'Plugin jar contains acceptance or unit-test classes' >&2
  exit 1
fi
if grep -Eq '(^|/)module-info\.class$' <<<"$entries"; then
  echo 'Plugin jar contains dependency module descriptors that do not describe the shaded jar' >&2
  exit 1
fi
if grep -Eq '^nl/hauntedmc/dungeons/(model/instance|content/instance|listener/instance|util/config|util/lang)/' <<<"$entries"; then
  echo 'Plugin jar contains retired internal packages; rebuild from a clean target directory' >&2
  exit 1
fi
echo "Artifact audit passed: $artifact"
