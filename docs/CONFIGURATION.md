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
- `runs` / `open`: run concurrency, time limit, open-instance player limits.
- `rewards`: finish delivery and cooldown policy.
- `access`: key consumption and access cooldown policy.
- `difficulty`: named difficulty presets and scaling.
- `rules`: spawning, building, movement, world, combat, commands, items, entities.
- `map`: floor-depth controls for map rendering.

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
