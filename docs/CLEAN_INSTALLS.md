# Clean-install contract

Dungeons supports fresh installations on the configured Paper 26.3 runtime. Older installations
are not imported or converted. Recovery supports data written by the current implementation,
including crash recovery and normal restart.

Supported storage:

- Dungeon and generator settings: `config.yml`; defaults: `dungeons/config_default.yml`.
- Native Paper dimension templates, without pre-26 world conversion or registry rewrites.
- Deflate-compressed `DUNGEONS-STRUCTURE-2` structures with encoded tile data.
- Version 2 recovery journals with exactly 43 inventory slots.
- Native boolean/integer gamerule values. String-valued saves are rejected.
- Editor tools identified by persistent tags and configured material, never by display name.

Separate `generation.yml` and `generator_settings_default.yml` files are ignored and are never
imported, archived or rewritten. Unversioned structures and old journal formats are rejected.
The duplicate `DungeonReturnLocation` mechanism and mutable progression-record view are removed.

Staff operations use `/dungeon admin`, including `edit <dungeon> <reason>`. Current authoring
commands remain under `/dungeon edit`, `/dungeon dungeon`, `/dungeon config` and `/dungeon loot`.
Loot removal uses the confirmed staff operation. Flat administration aliases, old player/instance
administration groups, setting aliases and layout-name synonyms are removed. `/du` remains an
intentional current shortcut.

Permissions use `dungeons.staff.<capability>[.<dungeon>]` and the documented role presets.
Old admin/edit/cooldown/loot/editor permission nodes are not translated or inherited.

Rollback, backups, native checkpoints, abandoned editor recovery, safe unload retry, configuration
defaults and color formatting remain current features.

Validation: 471 unit/contract tests passed without failures or skips, together with thirteen
native Paper scenarios and cold restart. Checkstyle, ShellCheck, shell syntax, artifact and
whitespace checks passed. Reproduce with:

```sh
./mvnw -B -ntp -Pplatform-acceptance verify
bash scripts/verify-artifact.sh
```
