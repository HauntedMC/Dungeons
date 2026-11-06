# Registered mechanic coverage

The pinned [manifest](../src/test/resources/contracts/mechanics.csv) contains all **54**
registered concrete mechanics. `RegisteredMechanicContractTest` compares discovery with
these literal IDs, rejects duplicates, and round-trips every concrete alias through Bukkit's
serializer dispatch with configured base fields. `RegistryContractTest` executes bootstrap
and checks runtime registration, serializer aliases and editor metadata against the manifest.

The table below separately identifies behavioral execution evidence. A registration or
round-trip assertion alone does not establish the mechanic's gameplay behavior.
`MechanicGraphPersistenceContractTest` additionally writes and loads actual YAML containing
nested composite/delay/repeater/status, remote and condition graphs, then executes and clones
them to verify independent parents, state and timer cancellation. Other room and dungeon
persistence contracts cover their file-owning graphs.

| Persisted type ID | Concrete class | Execution evidence |
| --- | --- | --- |
| `dungeons.condition.chance` | `ChanceCondition` | [ConditionExecutionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/condition/ConditionExecutionContractTest.java) — Chance endpoints and out-of-range probabilities. |
| `dungeons.condition.difficulty` | `DifficultyCondition` | [ConditionExecutionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/condition/ConditionExecutionContractTest.java) — Default, named and absent difficulty. |
| `dungeons.condition.instance_variable` | `InstanceVariableCondition` | [ConditionExecutionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/condition/ConditionExecutionContractTest.java) — Scheduled variable updates and comparisons; large values also in VariableMechanicsContractTest. |
| `dungeons.condition.mob_within` | `MobWithinCondition` | [ConditionExecutionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/condition/ConditionExecutionContractTest.java) — Living type, count and distance filters; invalid type fallback. |
| `dungeons.condition.players_within` | `PlayersWithinCondition` | [ConditionExecutionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/condition/ConditionExecutionContractTest.java) — Exact/minimum counts and spectator exclusion. |
| `dungeons.condition.status` | `StatusCondition` | [ConditionExecutionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/condition/ConditionExecutionContractTest.java) — Actual status changes, missing and different status. |
| `dungeons.condition.time_elapsed` | `TimeElapsedCondition` | [ConditionExecutionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/condition/ConditionExecutionContractTest.java) — Elapsed-time boundary and negative threshold clamp. |
| `dungeons.function.allow_block` | `AllowBlockFunction` | [GameplayRulesContractTest](../src/test/java/nl/hauntedmc/dungeons/listener/instance/GameplayRulesContractTest.java) — Active location overrides, permissions and other-world isolation. |
| `dungeons.function.block_editor` | `BlockEditorFunction` | [WorldFunctionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/function/WorldFunctionContractTest.java) — Directional block data and material-selective removal. |
| `dungeons.function.checkpoint` | `CheckpointFunction` | [GameplayFunctionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/function/GameplayFunctionContractTest.java) — Centered checkpoint and world-independent save point. |
| `dungeons.function.chunk_load` | `ChunkLoadFunction` | [WorldFunctionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/function/WorldFunctionContractTest.java) — Configured chunk neighborhood and release on disable. |
| `dungeons.function.command` | `CommandFunction` | [PlayerEffectContractTest](../src/test/java/nl/hauntedmc/dungeons/content/function/PlayerEffectContractTest.java) — Sanitized command contexts, online target deduplication and console anchor. |
| `dungeons.function.delayed` | `DelayedFunction` | [ScheduledFunctionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/function/meta/ScheduledFunctionContractTest.java) — Delay, captured targets, overlapping activation cancellation; YAML graph execution also covered. |
| `dungeons.function.door_control` | `DoorControlFunction` | [WorldFunctionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/function/WorldFunctionContractTest.java) — Locked interaction and actual block-state unlock. |
| `dungeons.function.finish_dungeon` | `FinishDungeonFunction` | [GameplayFunctionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/function/GameplayFunctionContractTest.java) — Completion persistence and once-only queued rewards. |
| `dungeons.function.give_item` | `GiveItemFunction` | [PlayerEffectContractTest](../src/test/java/nl/hauntedmc/dungeons/content/function/PlayerEffectContractTest.java) — Independent tagged target stacks and location drops. |
| `dungeons.function.give_key` | `GiveKeyFunction` | [PlayerEffectContractTest](../src/test/java/nl/hauntedmc/dungeons/content/function/PlayerEffectContractTest.java) — Independent key stacks and location drops; ledger contracts separately cover ownership. |
| `dungeons.function.hologram` | `HologramFunction` | [WorldFunctionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/function/WorldFunctionContractTest.java) — Visibility and cleanup of run-owned display entities. |
| `dungeons.function.instance_variable` | `InstanceVariableFunction` | [ConditionExecutionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/condition/ConditionExecutionContractTest.java) — Deferred state mutation and comparisons; VariableMechanicsContractTest covers arithmetic and large values. |
| `dungeons.function.leave_dungeon` | `LeaveDungeonFunction` | [PlayerEffectContractTest](../src/test/java/nl/hauntedmc/dungeons/content/function/PlayerEffectContractTest.java) — Targeted departure and original-world restoration. |
| `dungeons.function.lives` | `LivesFunction` | [GameplayFunctionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/function/GameplayFunctionContractTest.java) — Set/multiply/add with untargeted participant isolation. |
| `dungeons.function.loot_table_rewards` | `LootTableRewardsFunction` | [RewardMenuContractTest](../src/test/java/nl/hauntedmc/dungeons/content/function/reward/RewardMenuContractTest.java) — Missing-table recovery, real loot generation and once-only claim. |
| `dungeons.function.message` | `MessageFunction` | [GameplayFunctionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/function/GameplayFunctionContractTest.java) — Variable substitution and requested-recipient isolation. |
| `dungeons.function.multi` | `CompositeFunction` | [FunctionCompositionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/function/meta/FunctionCompositionContractTest.java) — Order, target isolation, triggered-child exclusion, execution/cleanup failure isolation. |
| `dungeons.function.play_sound` | `PlaySoundFunction` | [GameplayFunctionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/function/GameplayFunctionContractTest.java) — Configured sound and recipient routing at the server boundary. |
| `dungeons.function.random` | `RandomFunction` | [FunctionCompositionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/function/meta/FunctionCompositionContractTest.java) — Only selected child executes and caller targets remain intact. |
| `dungeons.function.random_reward` | `RandomRewardFunction` | [RewardMenuContractTest](../src/test/java/nl/hauntedmc/dungeons/content/function/reward/RewardMenuContractTest.java) — Clamped selection counts and independent per-player stacks. |
| `dungeons.function.redstone_block` | `RedstoneBlockFunction` | [WorldFunctionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/function/WorldFunctionContractTest.java) — Pulse/reset scheduling, overlapping cleanup and clone ownership. |
| `dungeons.function.remote_trigger` | `RemoteTriggerFunction` | [PlayerEffectContractTest](../src/test/java/nl/hauntedmc/dungeons/content/function/PlayerEffectContractTest.java) — Signal source and temporary-listener cleanup on dispatch failure. |
| `dungeons.function.repeater` | `RepeaterFunction` | [ScheduledFunctionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/function/meta/ScheduledFunctionContractTest.java) — Finite counts, target capture, independent overlapping repeats and cancellation. |
| `dungeons.function.revive_player` | `RevivePlayerFunction` | [PlayerEffectContractTest](../src/test/java/nl/hauntedmc/dungeons/content/function/PlayerEffectContractTest.java) — Dead-member eligibility, restored lives and successful-use accounting. |
| `dungeons.function.reward` | `RewardFunction` | [RewardExecutionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/function/reward/RewardExecutionContractTest.java) — Once-only XP, independent claims/stacks, immediate and deferred menu delivery. |
| `dungeons.function.room_door` | `RoomDoorFunction` | [RoomMechanicContractTest](../src/test/java/nl/hauntedmc/dungeons/generation/room/RoomMechanicContractTest.java) — Named/action door control, adjacent rooms and entrance exception. |
| `dungeons.function.sequence` | `SequenceFunction` | [FunctionCompositionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/function/meta/FunctionCompositionContractTest.java) — One step per activation and configured loop order. |
| `dungeons.function.set_difficulty` | `SetDifficultyFunction` | [GameplayFunctionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/function/GameplayFunctionContractTest.java) — Known preset resolution and invalid-input preservation. |
| `dungeons.function.set_status` | `SetStatusFunction` | [ConditionExecutionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/condition/ConditionExecutionContractTest.java) — Actual status mutation observed by its condition; YAML graph execution also covered. |
| `dungeons.function.spawn_mob` | `SpawnMobFunction` | [WorldFunctionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/function/WorldFunctionContractTest.java) — Spawn type fallback, centered position, finite count, delay/interval and clone task ownership; living attributes and room tagging remain native boundaries. |
| `dungeons.function.start_dungeon` | `StartDungeonFunction` | [GameplayFunctionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/function/GameplayFunctionContractTest.java) — Every participant ready, duplicate readiness and once-only start. |
| `dungeons.function.teleport` | `TeleportFunction` | [GameplayFunctionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/function/GameplayFunctionContractTest.java) — Runtime world/target isolation; RoomMechanicContractTest also covers rotated facing. |
| `dungeons.function.title` | `TitleFunction` | [GameplayFunctionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/function/GameplayFunctionContractTest.java) — Variable substitution and targeted title output. |
| `dungeons.trigger.block_detector` | `BlockDetectorTrigger` | [TriggerExecutionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/trigger/TriggerExecutionContractTest.java) — Configured place/break action, location and world. |
| `dungeons.trigger.chat` | `ChatTrigger` | [TriggerExecutionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/trigger/TriggerExecutionContractTest.java) — Text rules, membership, cancellation and main-thread bridge success/failure. |
| `dungeons.trigger.distance` | `DistanceTrigger` | [TriggerExecutionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/trigger/TriggerExecutionContractTest.java) — Current membership, proximity, exit/re-entry; room whole-party counts also covered. |
| `dungeons.trigger.dungeon_start` | `DungeonStartTrigger` | [TriggerExecutionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/trigger/TriggerExecutionContractTest.java) — Owning run, scheduled firing and original player source. |
| `dungeons.trigger.gate_and` | `GateAndTrigger` | [GateMechanicsTest](../src/test/java/nl/hauntedmc/dungeons/content/trigger/gate/GateMechanicsTest.java) — All children, reset and cancelled/unrelated signals; GateGraphCloneContractTest covers independent child graphs. |
| `dungeons.trigger.gate_or` | `GateOrTrigger` | [GateMechanicsTest](../src/test/java/nl/hauntedmc/dungeons/content/trigger/gate/GateMechanicsTest.java) — Each child signal, cancelled/unrelated events and run isolation. |
| `dungeons.trigger.interact` | `InteractTrigger` | [TriggerExecutionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/trigger/TriggerExecutionContractTest.java) — Main hand, requested block/action, spectator and denied-action filtering. |
| `dungeons.trigger.key_item` | `KeyItemTrigger` | [TriggerExecutionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/trigger/TriggerExecutionContractTest.java) — Matching key and main-hand consumption at the configured block. |
| `dungeons.trigger.leave_dungeon` | `LeaveDungeonTrigger` | [TriggerExecutionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/trigger/TriggerExecutionContractTest.java) — Run isolation and original departure cancellation. |
| `dungeons.trigger.mob_death` | `MobDeathTrigger` | [TriggerExecutionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/trigger/TriggerExecutionContractTest.java) — Cancellation, type, range, count and reset. |
| `dungeons.trigger.player_death` | `PlayerDeathTrigger` | [TriggerExecutionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/trigger/TriggerExecutionContractTest.java) — Cancellation, distinct player counts/reset and independent clone progress. |
| `dungeons.trigger.redstone` | `RedstoneTrigger` | [TriggerExecutionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/trigger/TriggerExecutionContractTest.java) — Rising edge, rate limit and cleanup. |
| `dungeons.trigger.remote` | `RemoteTrigger` | [TriggerExecutionContractTest](../src/test/java/nl/hauntedmc/dungeons/content/trigger/TriggerExecutionContractTest.java) — Name, run, inclusive bounds and cancelled signals. |
| `dungeons.trigger.room_door_change` | `RoomDoorChangeTrigger` | [RoomMechanicContractTest](../src/test/java/nl/hauntedmc/dungeons/generation/room/RoomMechanicContractTest.java) — Actual door change to matching room/action function graph. |

The shared trigger/function lifecycle contracts also test bound-source dispatch, cancellation,
overlapping condition waits, child failures and cleanup. This table identifies representative
execution evidence; it does not imply that every setting combination has a dedicated test.

Paper's world/entity/inventory interfaces, native registries, visual menus and task submission
are boundaries. Spawner tests verify real scheduling and spawn requests using a non-living
entity result; they do not execute registry-backed living health/damage or room tagging.
Item fixtures exercise project metadata policies rather than native ItemStack serialization.
See [remaining boundaries](CORE_TEST_CONTRACTS.md#remaining-boundaries) for the full limits and
[Testing](TESTING.md) for deterministic execution and coverage report commands.
