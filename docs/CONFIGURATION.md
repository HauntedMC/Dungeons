# Configuration Guide

This guide focuses on practical setup and safe operation of Dungeons in production-like environments.

## Runtime File Layout

Dungeons uses these primary runtime files:

- `plugins/Dungeons/config.yml` (global plugin behavior)
- `plugins/Dungeons/messages.yml` (chat/UI text)
- `plugins/Dungeons/loottables.yml` (loot table persistence)
- `plugins/Dungeons/dungeons/config_default.yml` (default template for dungeon config keys)

Each dungeon is loaded from its own folder under `plugins/Dungeons/dungeons/<dungeon-name>/`.

## Automatic File Synchronization

On startup, Dungeons synchronizes key files with bundled defaults:

- `config.yml`: missing keys are added, obsolete keys are removed, existing known values are preserved.
- `dungeons/config_default.yml`: missing keys are added and obsolete keys are removed.
- `messages.yml`: bundled missing keys are written so new messages appear after upgrades.

This keeps operator files aligned with code changes while preserving local custom values where possible.

## Global Config (`config.yml`)

Key sections:

- `global_rules`: default gameplay rules inherited by every dungeon.
- `instances`: global active-run caps and startup trigger timing.
- `commands`: command output pagination and destructive-action confirmation windows.
- `editor`: editor timeout/autosave/tool materials, targeting reach, and preview behavior.
- `generation`: layout timeout and safe-spawn search timeout.
- `team`: invite expiry and team-disband cleanup warnings.

## Per-Dungeon Defaults (`dungeons/config_default.yml`)

The default dungeon template controls behavior for newly created or synced dungeons:

- `dungeon`: type, display behavior, world environment, generator id.
- `dungeon.start_title`: per-dungeon title timing shown at run start when enabled.
- `locations`: lobby/start/exit destinations.
- `players`: gamemode, lives, spectating, entry-state preservation, and per-player join commands.
- `team`: team-size limits and disband shutdown delay.
- `runs` / `open`: run concurrency, time limit, time-limit warning thresholds, open-instance player limits, idle unload timing, and optional preloading.
- `rewards`: finish delivery and cooldown policy.
- `access`: key consumption and access cooldown policy.
- `difficulty`: named difficulty presets and scaling.
- `rules`: optional overrides for global spawning, building, movement, world, combat, commands, items, and entity rules.
- `generator`: layout, connector sealing, room targets, and nested `generator.branching` settings.
- `map`: floor-depth controls for map rendering.

### Global Rules and Dungeon Overrides

Set shared rules under `global_rules` in `plugins/Dungeons/config.yml`. Each dungeon
inherits every omitted setting. Only explicit values under its own `rules` section override
the corresponding global value; nested sections merge per setting, and lists replace the
global list. Explicit `false` and `[]` are valid overrides.

For example, to override only PVP for one dungeon, put this in its `config.yml`:

```yaml
rules:
  combat:
    pvp: true
```

All other rules still inherit globally. Remove an override to restore inheritance, or use
`/dungeon config <dungeon> <setting> inherit`. For example:

```text
/dungeon config crypt allowed-commands inherit
```

Config listings mark inherited values with `(globaal)`. Using `clear` on a list still creates
an explicit empty list; use `inherit` to use the global list again.

To allow a command across inheriting dungeons, add it once to
`global_rules.commands.allow_list` in the main config, then run `/dungeon reload`.
This reload refreshes shared command rules and cached dungeon rules without reloading
each map. World settings applied during instance creation take effect in new instances.
An explicit dungeon command list replaces the entire global list, so that dungeon will
keep its own commands until its override is removed.
New maps have commented rule examples and inherit all global rules. Dungeon config
synchronization and saves do not fill in omitted rules, including when an older
`config_default.yml` still contains active rules.

Existing maps keep their explicit rule values. Remove or comment out their `rules`
section (or individual settings) to adopt global defaults.

### Access Keys

Global plugin config:

- `access.keys.unique_validation_enabled: false` by default
- `false`: keys are treated as generic configured items, with no per-item token rotation and no `access_keys.yml` tracking
- `true`: issued keys get plugin-owned ids and per-item instance tokens, and each dungeon uses `access_keys.yml` to track issued, reserved, and invalidated tokens

Per-dungeon `access.keys.items` is managed as a serialized list of key definitions, not just raw item stacks.

Each key entry stores:

- the key item itself
- a stable internal key id
- when it was added
- which player added it

When `access.keys.unique_validation_enabled` is enabled, issued physical keys are tracked separately in `access_keys.yml`. A usable key must have:

- the configured key-definition id
- a plugin-issued per-item instance id
- a live issued-instance record that matches the configured key

When a run is being prepared, that issued instance is temporarily reserved so a copied key cannot
start a second run in parallel with the same token.

On successful dungeon entry, the plugin:

- invalidates the instance id for consumable keys
- rotates the instance id for reusable keys

That makes the system resilient to normal item-meta/NBT drift across Minecraft updates and limits exact-copy dupes to a single successful use before the old token is invalidated.

Preferred management commands:

- `/dungeon dungeon keys add <dungeon>`
- `/dungeon dungeon keys remove <dungeon> [id]`
- `/dungeon dungeon keys clear <dungeon>`
- `/dungeon dungeon keys list <dungeon> [page]`
- `/dungeon dungeon keys give <dungeon> <player> <id> [amount]`

With unique validation disabled, matching falls back to the configured item itself. With unique validation enabled, only plugin-issued tagged keys are considered valid and old raw player-held copies should be reissued through the current key commands.

Access-cooldown bypass requires `dungeons.bypass.cooldown`. Administrative access does not implicitly bypass gameplay. See [staff operations](STAFF_OPERATIONS.md) for permissions and maintenance.

## Generator Settings (`config.yml`)

Generation settings live in each dungeon's normal `config.yml` under `generator`.
The shared `dungeons/config_default.yml` provides defaults when maps are created or synced.
These settings apply to branching dungeon maps; static and open maps ignore them.

```yaml
generator:
  layout: branching
  seal_open_connectors_with: STONE
  room_target: # Used by the minecrafty layout.
    min: 8
    max: 16
    enforce_minimum: true
  branching:
    trunk:
      room_pool: []
      end_room_pool: []
      straightness: 0.5
      room_target:
        min: 12
        max: 24
        enforce_minimum: true
    branches: {}
```

Only `config.yml` and `dungeons/config_default.yml` are read. Generator settings live under
`generator.branching`; separate generation files and old configuration names are not imported.

The existing `/dungeon config` layout, trunk, and branch commands now read and write
`config.yml`. Changes apply to future generation runs.

## Player Join Commands

Per-dungeon `players.join_commands` runs console commands once for every player accepted into a playable run, including team members and open-dungeon joins.

Example:

```yaml
players:
  join_commands:
    - "god {player} disable"
    - "fly {player} disable"
    - "speed walk 1 {player}"
```

Supported player-name placeholders are `{player}`, `{player_name}`, `<player>`, `%player%`, and `%player_name%`. UUID placeholders are `{uuid}` and `%uuid%`.

## Recommended Rollout Workflow

1. Start with conservative limits and rules.
2. Create one test dungeon and validate queue/team/start/end flow.
3. Validate editor behavior (`open`, autosave, preview visibility).
4. Validate reward and cooldown behavior before enabling for all players.
5. Roll out additional dungeon definitions incrementally.

## Troubleshooting Tips

- If a dungeon does not appear, verify its folder path and `config.yml` validity under `plugins/Dungeons/dungeons/`.
- If generation fails, validate room pools, connector compatibility, and target ranges.
- If teams cannot start, validate team size, key/cooldown constraints, and queue state.
- If command text is missing after an upgrade, restart once and re-check synchronized message keys.

## Cooldown reset boundaries

Access cooldowns, dungeon loot cooldowns, and reward-function cooldowns use the server's
configured time zone. TIMER is a relative delay in minutes. HOURLY resets at the next full
hour and ignores the configured reset-hour value. DAILY, WEEKLY, and MONTHLY use the reset
hour (0–23). Weekly days follow Calendar numbering: 1 is Sunday and 7 is Saturday.

Calendar resets always choose a time strictly after the current instant. A monthly day
that does not exist in a month uses that month's last day, retaining the configured day
for later months. For example, day 31 uses February 28 or 29 and then March 31. Local reset
times skipped by daylight saving advance to the next valid local time.

## Structure file compatibility

Room structures must use the deflate-compressed `DUNGEONS-STRUCTURE-2` format. Tile SNBT is
encoded so delimiters cannot split records. Unversioned structures are rejected.


## Invalid saved data and failed world unloads

Player files must use `<player-uuid>.yml` filenames. Unrelated filenames and files with
invalid YAML are logged and skipped without rewriting them or preventing valid player
records from loading. An invalid `RewardsInfo` value is ignored while other readable
fields, such as completion and access cooldowns, remain available.

When unique key validation is enabled and reading `access_keys.yml` fails, new key issuance
is refused until reading succeeds. Issuance does not replace the unreadable file with an
empty ledger. A loaded dungeon can retry issuance after repair; a dungeon that failed its
initial load must be reloaded.

If Paper refuses or fails to unload an instance world, cleanup retains the world files
and reference instead of deleting files that the server may still be using. Initialization
failure still releases waiting initialization tasks. Failed disposal is automatically retried up to three times at 100-tick intervals. Staff can inspect and explicitly retry retained instances through the staff dashboard.
