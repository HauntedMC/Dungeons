# Architecture Overview

Dungeons is a Paper plugin with an explicit composition root and a stable runtime graph. The goal is predictable lifecycle behavior for dungeon loading, queueing, team coordination, and instance execution.

## Design Goals

- Keep startup and shutdown ordering explicit in one place.
- Keep long-lived runtime managers stable across reload paths.
- Keep dungeon behavior data-driven through configuration and registered content types.
- Separate bootstrap concerns from runtime domain behavior.

## Core Components

- `DungeonsPlugin`: Bukkit entrypoint and world-generator hook delegation.
- `DungeonsApplication`: composition root that owns startup ordering and shutdown calls.
- Bootstrap package:
  - `CoreBootstrap`: config sync, runtime manager creation, language setup.
  - `RegistryBootstrap`: serializer/type registry setup for dungeon elements.
  - `CommandBootstrap`: command registration and wiring.
  - `ListenerBootstrap`: Bukkit listener registration.
  - `IntegrationBootstrap`: optional plugin integration registration.
  - `MenuBootstrap`: GUI/menu initialization.
  - `LifecycleCoordinator`: shutdown and reload orchestration.
- `DungeonsRuntime`: mutable runtime state container initialized once per plugin lifecycle.
- Runtime services:
  - `DungeonRepository`: dungeon discovery, config-template sync, load/reload.
  - `DungeonQueueRegistry` + `DungeonQueueCoordinator`: queue storage and queue-to-instance flow.
  - `DungeonTeamService`: team lifecycle, invites, and queue/start constraints.
  - `PlayerSessionRegistry` + `ActiveInstanceRegistry`: player/session and active-run tracking.
  - `LootTableRepository`: persisted loot table data.

## Runtime Flow

Startup:

1. Runtime bridge and config state are initialized.
2. Core managers and registries are created.
3. Runtime type metadata is registered.
4. Dungeon catalogue is loaded from disk.
5. Commands/listeners/integrations are registered.
6. Menu state is initialized.
7. Online players are restored into runtime session state.

Reload:

- `LifecycleCoordinator` keeps manager instances stable and refreshes data in place.
- Queue starts and key reservations are cleaned before dungeon/content reloads.
- Config-backed runtime values and localization state are refreshed without rebuilding the whole graph.

Shutdown:

- Active queue state is discarded.
- Reserved keys are refunded.
- Player session hotbars are restored.
- Active dungeon instances are disposed.
- Team service shutdown hooks run.

## Room Schematic Persistence

`BranchingRoomDefinition.captureSchematic` captures world blocks on the server thread and schedules file serialization separately while the plugin is enabled. It admits one save per room at a time. Its boolean future returns `false` for capture, scheduling, or write failures and for overlapping requests. Cancelling the returned future does not cancel an admitted save.

Rooms remain dirty until persistence succeeds. A revision counter keeps edits made during an in-flight save dirty for the next capture. Successful saves publish the captured schematic and invalidate rotated block caches; failed saves retain the previous cache.

`SchematicUtils.saveStructurePiece` throws `IOException` on storage failure. It serializes into a unique temporary file beside the destination, flushes the file, and atomically replaces the destination. Failed writes clean up the temporary file where possible and preserve the previous destination. The filesystem must support atomic replacement; unsupported replacement fails the save. The existing versioned format and legacy read compatibility are unchanged. This guarantees replacement of an individual schematic, rather than a transaction across room metadata, schematics, and world files. A power loss can leave temporary files, and directory-entry durability depends on the filesystem.

## Player Recovery and Reward Delivery

Each player has a versioned `players/<uuid>.recovery.yml` journal separate from their existing progression YAML. Before dungeon entry commands, inventory replacement, editor tools, or temporary hotbars can change player state, the plugin synchronously writes a complete pre-entry snapshot. Snapshots include inventory/offhand, armor, held slot, return world UUID and location, game mode, XP totals/level/progress, health, food, saturation, exhaustion, flight settings, movement speeds, active potion effects, fire, fall distance, and remaining air. Item payloads use Paper's native item bytes so custom components and metadata survive.

On reconnect or plugin enable, recovery restores the snapshot when no live instance owns the player. Dead players recover after respawn. Missing worlds, rejected teleports, corrupt records, and storage errors retain the journal. Unsafe restoration or delivery disconnects the player to prevent further inventory changes; operators should fix the logged storage/world problem before reconnecting the player. Corrupt journals are retained for manual recovery, rather than overwritten with an empty session. Existing return-location-only files remain supported; inventories lost before this journal existed cannot be reconstructed.

Normal departures continue to follow the configured inventory/experience keep rules, including during shutdown. Reconstructed sessions derive their restoration baseline from the persisted snapshot. Hotbar closure acknowledges only a recovery record created by that hotbar; editor and explicit inventory snapshots remain protected until their own restoration completes. Successful recovery clears stale editor controls and cached snapshots before the session can resume. Before acknowledgement, the plugin saves the resulting player state and a transaction marker through Minecraft's player-data save, reads the marker back from the native `playerdata/<uuid>.dat` file, and flushes that file. Only then does it atomically remove the recovery entry. A marker already loaded from a completed transaction prevents replay after a crash between native saving and journal acknowledgement.

Rewards become durable when added to a player's session, including rewards selected during a run; `pushPlayerRewards` no longer transfers an in-memory balance on finish. Earned rewards therefore remain claimable after early departure or restart. Claiming records the exact resulting inventory and remaining rewards together before touching the live inventory. Native save verification precedes clearing that pending delivery. Available slots and stack capacity determine partial delivery; overflow stays claimable instead of being dropped into the world. Returned reward lists contain independent item copies. Callers should use `claimReward` instead of the former remove-then-give sequence.

Journal writes use unique sibling temporary files, file flushes, and atomic replacement. Atomic replacement support and writable native player-data storage are required. Transactions run on the server thread; journal I/O is intentionally synchronous before destructive state changes. This protects plugin transitions from process loss; it does not provide a cross-player transaction for trades, protect against unrelated plugins rewriting inventory after a transition, or guarantee directory-entry durability through a hardware power failure. Acceptance uses native Paper item/NBT codecs with a controlled player/network boundary; restart fault tests cover transaction replay separately.

## Why This Matters

For operators, this structure reduces surprise during reloads and restarts.

For contributors, boundaries are clear: bootstrap wiring in `bootstrap`, runtime domain logic in `runtime`, and gameplay behavior in content/listener/command packages.
