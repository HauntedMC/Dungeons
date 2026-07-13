# Configuration Guide

This guide focuses on practical setup and safe operation of Dungeons in production-like environments.

## Runtime File Layout

Dungeons uses these primary runtime files:

- `plugins/Dungeons/config.yml` (global plugin behavior)
- `plugins/Dungeons/messages.yml` (chat/UI text)
- `plugins/Dungeons/loottables.yml` (loot table persistence)
- `plugins/Dungeons/dungeons/config_default.yml` (default template for dungeon config keys)
- `plugins/Dungeons/dungeons/generator_settings_default.yml` (default generator settings template)

Each dungeon is loaded from its own folder under `plugins/Dungeons/dungeons/<dungeon-name>/`.

## Automatic File Synchronization

On startup, Dungeons synchronizes key files with bundled defaults:

- `config.yml`: missing keys are added, obsolete keys are removed, existing known values are preserved.
- `dungeons/config_default.yml` and `dungeons/generator_settings_default.yml`: missing keys are added and obsolete keys are removed.
- `messages.yml`: bundled missing keys are written so new messages appear after upgrades.

This keeps operator files aligned with code changes while preserving local custom values where possible.

## Global Config (`config.yml`)

Key sections:

- `instances`: global active-run caps.
- `commands`: command output pagination.
- `editor`: editor timeout/autosave/tool materials and preview behavior.
- `generation`: layout timeout and safe-spawn search timeout.
- `team`: invite expiry and team-disband cleanup warnings.

## Per-Dungeon Defaults (`dungeons/config_default.yml`)

The default dungeon template controls behavior for newly created or synced dungeons:

- `dungeon`: type, display behavior, world environment, generator id.
- `locations`: lobby/start/exit destinations.
- `players`: gamemode, lives, spectating, entry-state preservation, and per-player join commands.
- `team`: team-size limits and disband shutdown delay.
- `runs` / `open`: run concurrency, time limit, open-instance player limits, idle unload timing, and optional preloading.
- `rewards`: finish delivery and cooldown policy.
- `access`: key consumption and access cooldown policy.
- `difficulty`: named difficulty presets and scaling.
- `rules`: spawning, building, movement, world, combat, commands, items, entities.
- `map`: floor-depth controls for map rendering.

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

Players with `dungeons.admin` bypass access-cooldown checks and do not receive new access cooldown entries from dungeon start/finish/leave flows.

## Generator Defaults (`dungeons/generator_settings_default.yml`)

Generation defaults are split by layout mode:

- `generator`: layout selection, room target ranges, connector sealing material.
- `branching`: trunk path shaping, end-room pools, branch rules.

Use these defaults to standardize generated dungeons before per-dungeon overrides.

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
