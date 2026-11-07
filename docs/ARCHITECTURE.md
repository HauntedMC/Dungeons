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

## Recoverable Instance Disposal

`DungeonInstance.disposeAsync()` reports completion of one disposal attempt. Concurrent requests observe the same underlying attempt; cancelling an observer future does not interrupt cleanup. A failed attempt returns `false` and can be retried with `dispose()` or `disposeAsync()`. `getDisposalState()` distinguishes active, scheduled, preparing, unloading, retryable failure, and disposed states; `getDisposalFailure()` exposes the most recent failure. Once disposal starts, the instance remains unavailable for new players and gameplay startup.

Final persistence must succeed before unloading. Occupied worlds, failed saves, scheduler rejection, and refused or exceptional unloads retain world references, files, and dungeon/global registrations. The plugin retries failures up to three times at 100-tick intervals while enabled. Explicit retries reset that budget. Reload commands retry empty managed instances; shutdown also traverses the global registry to reach retained instances from older catalog definitions. Per-dungeon admission counts include those older registrations, so a catalog reload cannot bypass capacity limits.

The disposal hook runs once to stop gameplay. Completion hooks, warm-pool replenishment, registry removal, and `DungeonDisposeEvent` occur only after successful unload (or after disposal of an instance that never acquired a world). Consumers can use `getInstanceWorldName()` after the live world reference is released. World operations run on the server thread. Shutdown drains delayed attempts or supersedes pending persistence safely; late callbacks from superseded attempts cannot unload a retained world.

Temporary-file deletion remains best effort after unload. Before releasing capacity or invoking completion hooks, the server thread atomically renames owned folders to unique `<world>.disposed-<uuid>` paths. Worker tasks delete only those detached paths, so a new world can safely reuse the original name. Failed detachment or deletion retains files and logs the path; startup cleanup recognizes both ordinary instance folders and the strictly validated retired-folder names.

## Configuration Snapshots and Ordered Writes

Mutable Bukkit configurations and model graphs belong to the server thread. `ConfigurationFile.saveAsync` captures the complete YAML string there before admitting a disk write. Calls from other threads first marshal capture to the server thread; their snapshot reflects state when that capture executes. `ConfigurationFile.onOwnerThread` applies the same rule before rooms build metadata and before static dungeons gather function graphs. Editor function and gamerule saves also use this snapshot path. Continuing to edit a model after capture cannot change its queued YAML.

All wrappers share a FIFO disk lane per canonical destination, including existing symbolic-link aliases. Workers receive immutable text, write and flush a sibling temporary file, then atomically replace the destination. Separate destinations progress independently. Failed serialization never admits a write; disk failures complete that save as `false` and do not poison later snapshots. Rejected worker scheduling completes the queued batch as failed. Cancelling an observer future does not cancel an admitted snapshot.

Synchronous `save` and `load` use the same destination lane and drain earlier admitted operations, so an old asynchronous write cannot overwrite a newer synchronous save. Loading reads bytes through the lane and applies YAML on the owner thread. Synchronous storage failures throw `UncheckedIOException`; callers no longer receive apparent success after a failed write. Live configuration mutation outside its owning thread remains unsupported, including mutation through nested collections or model references.

Shutdown captures pending owner-thread requests and drains disk lanes in a `finally` block, even when Paper has cancelled their scheduled tasks. No disk worker needs a Bukkit callback to finish its file operation. Atomic replacement support is required; failed replacements preserve the last good file. Ordering covers callers using this persistence path, rather than unrelated plugins or direct filesystem writes.

## Why This Matters

For operators, this structure reduces surprise during reloads and restarts.

For contributors, boundaries are clear: bootstrap wiring in `bootstrap`, runtime domain logic in `runtime`, and gameplay behavior in content/listener/command packages.

## Command, Persistence and Editor Boundaries

The command refactor is incremental. `DungeonCommand` retains Bukkit registration, root permission checks, aliases and the remaining dungeon/player/instance/editor branches. `TeamCommandHandler`, `LootCommandHandler` and `DebugCommandHandler` own their branch dispatch and completion with constructor-injected dependencies. `CommandArguments` handles argument slicing and discovery; `CommandOutput` handles presentation, filtering and pagination. Extracted handlers do not retain the root executor or obtain dependencies through `RuntimeContext`.

`DungeonPlayerDataStore` owns each dungeon's progression documents and access/reward cooldown indexes. `DungeonDefinition` keeps its public progression methods as delegates. The store loads checked snapshots and saves through the same atomic destination lanes as other configuration files. Unreadable or mismatched records remain protected from writes until repaired and reloaded; valid fields in partially invalid records remain readable. Save failures propagate to the caller and keep in-memory changes available for retry. Date values and cooldown collections are detached. Models and the legacy `getPlayerData` configuration view still belong to the server thread; external callers must not mutate them from workers.

`EditorSaveCoordinator` owns admission and the explicit `IDLE -> CAPTURING -> WRITING -> IDLE` lifecycle. `EditableInstance` supplies world capture, persistence and shutdown recovery through a narrow target interface. Capture runs on the server thread; disk work receives the captured inputs. Success and failure both release save ownership. Observer cancellation cannot release that ownership or cancel an admitted write. Disposal waits for earlier work and takes a fresh final snapshot; a failed final save retains the managed world for retry. `DungeonInstance` continues to own unload/disposal transitions independently of this save lifecycle.

`LootTable` and `LootTableItem` contain validated data and generation logic, without menu constructors, persistence calls, player references or runtime lookups. `LootTableEditor` owns UUID editing leases, main/item navigation, close/quit checkpoints and shutdown cleanup. The two menu builders render views and invoke validated model operations only when the current generation remains writable and the player holds its lease. Successful repository replacement invalidates old windows; a failed reload preserves the last working data and prevents further edits. Replacement observers run after commit and cannot turn a successful write into a reported failure. Unregistering a window removes its exact global registration without removing a newer replacement.

The regression baseline was run before these extractions: command routing and permission checks, room write failures/dirty revisions, immutable ordered configuration snapshots, editor final saves and shutdown draining, preparation timeout cancellation and retryable unload failures. Additional tests cover protected progression records, atomic save retry, editor leases across navigation/reload/quit and window retirement. Run `./mvnw -Pplatform-acceptance verify` for the complete unit, contract, style and native Paper acceptance checks.

Further extractions can use the same boundaries: move the remaining root command branches one at a time, separate the access-key ledger from dungeon definitions, and split `EditorMenus` by editor workflow. Preserve public gameplay entry points and failure-boundary tests while migrating those slices; moving every method at once would obscure lifecycle changes during review.

## Retained Editor Recovery Files

Editor world reservations contain `.dungeons-editor-recovery` before they can accept edits.
If a final save fails or the process exits unexpectedly, startup cleanup preserves that world
and its companion legacy/dimension folder. These files require manual recovery: back them up
before copying recovered world data into a template. Normal successful disposal removes them;
retired `.disposed-<uuid>` folders have already passed final persistence and remain eligible
for startup deletion. Recovery markers never enter templates through the editor save path.
