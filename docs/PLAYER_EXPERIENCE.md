# Player experience and optional mechanics

Every dungeon gets an adaptive HUD, entry information, a guide and exit instructions. Existing loot maps need **no objectives, stages, waves or experience file**. Their gameplay rules stay in the existing configuration.

## Players

- `/dungeon guide` (or `/dungeon` while participating): status, rules, party, objectives when present, recent feedback, settings and leaving.
- `/dungeon status`: current information and recent feedback; after departure, the last report for this connection.
- `/dungeon objectives`: revealed current goals. Click one to pin it, or use `/dungeon objectives pin <id>`.
- `/dungeon objectives hint`: builder-authored hints for visible unfinished goals.
- `/dungeon hud balanced|minimal|immersive|inherit`: saved presentation preference.
- `/dungeon spectate <player>`: follow a visible living teammate while spectating.
- `/dungeon hud sounds|titles|navigation true|false`: independent saved preferences. Navigation is direct bearing/distance, not pathfinding.
- `/dungeon leave`: immediate queue/preparation cancellation, or confirmation of an active departure. Confirmation expires in 30 seconds and is checked against the current participation and consequences.

Presentation preferences do not disable gameplay. Critical operational chat remains available. For already admitted/queued players and observers, self controls remain available even without the normal play/core permissions; this does not authorize starting other dungeons. Help, guide, settings, objectives and leaving remain accessible through dungeon command restrictions, including `/du` and namespaced commands.

Respawn checkpoints affect death recovery in a live run. Persistent save points are separate. Compatible personal progress can be saved with a persistent position; shared world state and encounters are never reconstructed from a personal save. Branching maps still do not support persistent save points. Incompatible saved objective state is preserved until the player explicitly chooses a fresh start; it is not applied to the new dungeon.

## Presentation configuration

Global `config.yml` uses `experience.defaults`. Per-dungeon `config.yml` uses sparse `experience` overrides:

```yaml
experience:
  enabled: true
  profile: balanced
  description: '<gold>Explore the city and collect its treasures.'
  confirm_exit: true
  channels:
    sidebar: true
    actionbar: true
    bossbar: true
    titles: true
  sidebar:
    title: '{dungeon}'
    rows: [state, difficulty, '', stage, objective, remaining, lives, party, respawn, '', help, leave]
  widgets:
    elapsed: shown
    lives: auto
    party: auto
    remaining: auto
    respawn: auto
  notifications:
    objective-completed:
      enabled: true
      chat: true
      actionbar: false
      title: false
      sound: true
```

Sidebar rows are widget IDs (`state`, `difficulty`, `stage`, `objective`, `remaining`, `elapsed`, `lives`, `party`, `respawn`, `help`, `leave`), blank separators, or trusted text templates. Missing/inapplicable widgets are omitted. Templates accept literal component placeholders `{dungeon}`, `{difficulty}`, `{state}`, `{time_left}`, `{elapsed}`, `{lives}`, `{stage}`, `{objective}`, `{party}`, and `{respawn}`. An explicit empty list replaces inherited rows. At most 15 rows are accepted. Invalid presentation reloads retain the last valid snapshot and produce an operator diagnostic.

Every listed widget supports `auto`, `shown` or `hidden` visibility in both default and custom layouts. Elapsed time and remaining time can be shown together. Leading/trailing and repeated blank separators are removed when widgets are omitted. Irrelevant widgets are omitted. No goals means no objective/stage rows or progress percentage. Balanced shows contextual detail, minimal reduces it, and immersive suppresses persistent overlays. Channels can be independently disabled. `experience.enabled` controls presentation, not authored mechanics. The description and authored titles accept existing trusted formatting; system messages use HauntedMC themes and player language resolution.

Existing start-title settings/timings remain authoritative. No duplicate entry title is added. Use `/dungeon map settings <map> list experience` for exposed presentation settings, with existing `set`, `clear` and `inherit` operations. Gameplay rules such as `players.lives` and `runs.time_limit_minutes` are not duplicated under experience settings.

## In-game authoring

Use the existing authorized editor and maintenance workflow:

```text
/dungeon map maintenance crypt on Updating the adventure
/dungeon edit open crypt
/dungeon edit experience
```

The inventory editor browses nested configuration, edits scalar/list/inline-map values through chat, adds keys, validates, previews and saves. Inputs are YAML values, for example `[seals, guardian]` or `{title: 'Break the seals', source: signal, target: 3}`. Type `cancel` to cancel chat input or `null` to remove a value. Changes stay in a draft until a validated save. External file changes require reloading the draft before overwriting them.

Equivalent commands:

```text
/dungeon edit experience get [path]
/dungeon edit experience set <path> <YAML value>
/dungeon edit experience remove <path>
/dungeon edit experience validate
/dungeon edit experience save
/dungeon edit experience reload
/dungeon edit experience preview
/dungeon edit experience recipe loot|dragon|waves|adventure
```

`/dungeon edit save` also saves an open experience draft before saving the world. Recipe buttons change only the draft. They never change lives, difficulty, key consumption or time limits, and refuse to overlap existing authored objectives/encounters. The loot recipe adds no mechanics. The dragon recipe selects an arena around the builder: review its radius and the selected fight before saving. Adventure recipes need graph wiring for their custom signals and encounter activation.

HUD previews have an isolated ten-second lease and restore the underlying display. Existing runs retain their gameplay definitions; saved changes apply to future instances. `experience.yml` participates in existing repository publication, validation and rollback.

## Optional experience.yml

No file means no additional mechanics runtime. `enabled: false` disables this entire definition without disabling the HUD. Individual definition sections can also use `enabled: false`.

```yaml
version: 1
completion: manual # manual | objectives; never implicitly inferred
objectives:
  seals:
    title: 'Break the three seals'
    source: signal
    selector: seal
    scope: run
    target: 3
    hint: 'Look behind the statues.'
  treasure:
    title: 'Find the hidden vault'
    source: reach
    scope: player
    optional: true
    hidden: true
    persistent: true
    location: {x: 20, y: 65, z: 40, radius: 3}
```

Objectives work without tracks, encounters or waves. Sources are `signal`, `kill`, `destroy`, `reach`, `interact`, and `variable`.

- `signal`: selector matches an Experience Controller signal.
- `kill` / `destroy`: selector is `type:ZOMBIE`, `type:END_CRYSTAL` or `group:<id>`. Entity events are counted once; plugin removal/unloading is not a kill. Personal kills require an eligible participating killer.
- `capture: true` on kill/destroy: snapshot the matching loaded arena cohort when the objective activates and use its size as the target, including zero. Configure an arena `location` or selected existing group so the intended fight is unambiguous; ensure the arena is loaded when activated.
- `reach`: complete when reaching `location`; branching maps may instead select `room:<room-id>`.
- `interact`: selector is block coordinates, for example `'10,65,12'`, and records successful interaction once. Use graph signals for custom puzzle semantics or repeated interactions.
- `variable`: selector is an instance-variable name; progress increases toward the integer target as the variable increases. Use signals for other comparisons.

`scope` is `run` or `player`. A personal objective may opt into `persistent: true`; shared, captured and variable-derived state cannot be written as personal saved progression. Hidden objectives are revealed through the graph adapter and never appear in player projections before reveal. Optional objectives do not gate automatic completion.

### Optional tracks

```yaml
tracks:
  story:
    scope: run
    auto_start: true
    optional: false
    stages:
      entrance:
        title: 'The sealed entrance'
        objectives: [seals]
      chamber:
        title: 'The burial chamber'
        objectives: [guardian]
```

Each referenced objective must exist, match the track scope and belong to only one stage. Stages advance after all their required objectives complete. Flat objectives and multiple personal/shared tracks can coexist. An `optional: true` track has its own required steps but does not gate overall dungeon completion. `auto_start: false` keeps its objectives inactive until an Experience Controller invokes `activate-stage` with an unlocked `track/stage` ID; earlier required stages cannot be skipped. Persistent personal snapshots also retain revealed goals and activated tracks. Stage event IDs are qualified as `story/entrance`, preventing collisions between tracks.

### Boss tracking and waves

```yaml
groups:
  guardian:
    entity: WITHER_SKELETON
    count: 1
    boss: 'The Crypt Guardian'
    phases: [0.75, 0.5, 0.25]
    location: {x: 0, y: 65, z: 0, radius: 32}
  dragon:
    entity: ENDER_DRAGON
    existing: true
    boss: 'Ender Dragon'
    location: {x: 0, y: 80, z: 0, radius: 128}
encounters:
  arena:
    title: 'The final chamber'
    auto_start: false
    preparation_seconds: 3
    timeout_seconds: 180
    waves:
      first: {groups: [guardian]}
      second: {groups: [guardian]}
```

An `existing` group binds living/existing entities in its selected loaded arena, independently of wave spawning. Boss tracking does not require objectives or waves. Native dragon bars are retained where supplied by Paper rather than duplicated by an encounter bar. Wave groups must spawn living entities and cannot use `existing: true`. Counts, health and damage use existing difficulty scaling. Preparation, active, completed, failed and reset states are explicit. Unexpected removal fails an active encounter; unloading does not manufacture a victory. Reset cancels pending spawns and removes owned wave entities.

A wave recipe supplies working spawns. A progression recipe supplies stages and signal-driven goals. Connect those through existing editor functions; no new mob AI or external mob dependency is required.

## Graph adapters and API

The function browser adds **Experience Controller**, the trigger browser adds **Experience Event**, and conditions add **Objective State**. All retain stable persisted type IDs.

Controller operations: `signal`, `advance`, `reveal`, `activate-stage`, `start-encounter`, `reset-encounter`. Event kinds include `objective-progress`, `objective-revealed`, `objective-completed`, `stage-started`, `stage-completed`, `track-completed`, `encounter-started`, `wave-started`, `encounter-completed`, `encounter-failed`, `encounter-reset` and `boss-phase`. Boss-phase IDs are `<group>:<threshold>`, for example `guardian:0.5`. Conditions check `active` or `completed`.

`DungeonExperienceApi` is an owner-thread Bukkit service exposing immutable viewer-specific snapshots and closeable viewer subscriptions. It does not expose mutable worlds or historical statistics. Subscriptions receive revealed mechanics changes (not periodic HUD updates or an initial replay); each registration has an independent, idempotent close handle. Callbacks may unsubscribe during delivery. Consumers must close subscriptions on the owner thread when unloading. The existing `DungeonsApi` stays compatible.

## Haunted HUD integration

This release uses FeatureFramework 2.2.5 and the coordinated ServerFeatures 5.4.7 HUD service. For local builds from the companion checkouts:

```bash
(cd ../FeatureFramework && ./mvnw -B -ntp install)
(cd ../ServerFeatures && ./mvnw -B -ntp verify)
./mvnw -B -ntp verify
```

Install ServerFeatures 5.4.7 or newer before Dungeons 1.5.3 for shared channel ownership. ServerFeatures publishes `HudService` independently of whether its ambient scoreboard feature is enabled.

Ownership priorities are ambient (0), dungeon session (100), contextual (200), and critical (300). Leases are per player and channel, expire automatically, and restore the **latest** lower-priority content when released. Sidebar rendering preserves ServerFeatures glow teams and other objectives. Ambient action bars and rotating boss bars participate in the same ownership system.

Without ServerFeatures, Dungeons uses a standalone renderer. With an older ServerFeatures lacking this contract, persistent conflicting channels are disabled with an operator diagnostic; chat, the guide and gameplay remain usable. Arbitrary plugins writing packets directly cannot be coordinated by this service.

## Validation

Run `./mvnw -B -ntp verify` and `bash scripts/verify-artifact.sh`. Runtime acceptance is `./mvnw -B -ntp -Pplatform-acceptance verify`, with Java 25 and Docker available. MySQL integration is `./mvnw -B -ntp -Pintegration-tests verify`.

The native experience scenario verifies durable personal preferences/checkpoints/revealed goals/activated tracks, an untouched loot dungeon, real crystal explosions, successive waves, reset, and unexpected removal. Shared HUD tests verify latest-content restoration, preserved board ownership, expiry, stable priority ordering and a 100-player/10-run simulation.

For an acceptance-only debugger attachment, set `DUNGEONS_ACCEPTANCE_DEBUG_PORT` to a localhost port. The first healthy Paper process waits for JDWP attachment; failed-database boot cases and restart cases run normally.
