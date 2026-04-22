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

## Why This Matters

For operators, this structure reduces surprise during reloads and restarts.

For contributors, boundaries are clear: bootstrap wiring in `bootstrap`, runtime domain logic in `runtime`, and gameplay behavior in content/listener/command packages.
