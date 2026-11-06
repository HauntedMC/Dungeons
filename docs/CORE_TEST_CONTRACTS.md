# Core system test contracts

The suite protects observable dungeon behavior through state transitions, real persisted graphs,
and mechanic execution. It is not a requirement to test every line or helper independently.

The completed unit suite contains **264 tests across 82 classes**, with no failures, errors, or
skips in the clean shuffled verification on 6 November 2026. This final implementation pass
added **151 test methods** to the previous 113-test suite. Every new `@Test` method has its own
commit, including any production fix demonstrated by that scenario. All 113 existing test
methods remain. No production classes were excluded from JaCoCo to raise the percentages.

## Completed contract map

The following sixteen areas map the final work to executable scenarios. Class names refer to
files under `src/test/java/nl/hauntedmc/dungeons`; a scenario can check several related inputs
or a complete lifecycle. Existing contracts remain part of the same suite.

| Area | Contracts and representative tests |
| --- | --- |
| 1. Player state and run lifecycle | `PlayerStateContractTest`, `PlayerRunContractTest`, `RunDisposalContractTest`: independent inventory/armor/hotbar snapshots, health/food/XP restoration, entry/exit cancellation, duplicate operations, stale entry/checkpoint callbacks, starting, time limits, finite/unlimited lives, vanilla respawn ordering, final-life departure, spectators, progression-item handoff, completion, and independent cleanup after failures. |
| 2. World startup and preparation | `WorldStartupContractTest`, `PreparationContractTest`, `OpenPreparationContractTest`: real world-folder copy and identity removal, world-creation handoff, rules/readiness/latches, static and branching final admission, disconnects and moved players, failed preparation, open-run reservations, coalesced preloading, replacement runs, and stale catalog publication. |
| 3. Queues, capacity, and access keys | `QueueContractTest`, `QueueLaunchContractTest`, `TeamJoinContractTest`, `AdmissionContractTest`, `OpenAdmissionContractTest`, `OpenRunCapacityTest`, `AccessKeyReservationContractTest`, `IssuedKeyLedgerContractTest`, `AccessKeyPolicyContractTest`: FIFO/retry/invalidation, partial rollback, team readiness deadlines, capacity reservations, one reservation owner, exact refund/consumption, replay/revocation/rotation, malformed-ledger recovery, and generic versus metadata key policies. |
| 4. Teams and service teardown | `TeamLifecycleContractTest`, `TeamTransitionsContractTest`, `LifecycleContractTest`: invitation acceptance/revocation/expiry, member indexes, kick/leave/disband before and after start, cancelled dungeon departure, disconnect, delayed teardown, shutdown callbacks, full reload and single-dungeon reload isolation. |
| 5. Presence and gameplay rules | `PresenceContractTest`, `GameplayRulesContractTest`: authorized world entry, reconnect/offline expiry, block/bucket permissions and overrides, placement tracking, direct/projectile PvP, pearls/chorus/plugin teleports, spawn causes, durability, commands, banned crafting/pickup/storage, entity protection, growth and explosions, and world isolation. |
| 6. Conditions | `ConditionExecutionContractTest`: all seven registered conditions execute against real run state, including status/difficulty/variables, time/count/distance boundaries, spectators, chance endpoints, inversion, and fail-closed child errors. |
| 7. Triggers and event routing | `TriggerExecutionContractTest`, `GateMechanicsTest`, `GateGraphCloneContractTest`, `TriggerLifecycleContractTest`, `FunctionDispatchContractTest`, `EventRelayContractTest`, `RegistryContractTest`: every trigger family, source/run/location/action filtering, cancellation, async-chat bridging, rising edges/rate limits, proximity re-entry, distinct-player counters, AND/OR state, clone isolation, overlapping condition waiters, actual registered event executors, handler failures, and mutation during dispatch. |
| 8. Gameplay, player, and world effects | `GameplayFunctionContractTest`, `PlayerEffectContractTest`, `WorldFunctionContractTest`, `FunctionStartupContractTest`: actual start/finish/checkpoint/lives/difficulty/status/teleport/output/command/revival/item/key/leave/remote effects, block edits, doors, chunks, holograms, spawner and redstone scheduling, per-run clone ownership, failed enable rollback, and resource cleanup. Native effects are bounded as described below. |
| 9. Composition and variables | `FunctionCompositionContractTest`, `ScheduledFunctionContractTest`, `VariableMechanicsContractTest`, `MechanicGraphPersistenceContractTest`: child order and selected targets, sequence/random selection, finite repeats, overlapping delays/repeats, cancellation, independent parent graphs, failure isolation, and large-number arithmetic/comparisons. |
| 10. Loot and rewards | `LootExecutionContractTest`, `LootRepositoryContractTest`, `WeightedSelectionContractTest`, `RewardExecutionContractTest`, `RewardMenuContractTest`, `RewardClaimContractTest`, `RewardCooldownContractTest`, `CooldownContractTest`: eligible-pool selection with/without replacement, independent stacks and claim history, immediate/deferred click and close delivery, missing-table recovery, once-only XP/claims, stale cooldown records, calendar boundaries, month ends, leap years, and DST. |
| 11. Layout execution and recovery | `GenerationMechanicsTest`, `LayoutExecutionContractTest`, `BranchingExecutionContractTest`, `GenerationRecoveryContractTest`: combined filters and occurrence/depth limits, overlap/adjacency, connected trunk/branches, required versus optional failures, fresh subsequent runs, checkpoint rollback, exhausted minimum targets, rejected work, interruption, and executor cleanup. |
| 12. Rooms and doors | `RoomTransformContractTest`, `RoomMechanicContractTest`, `RoomPersistenceContractTest`: source-independent connector/door clones, rotation of positions and facings, actual placed-room door control and trigger graphs, entrance exceptions, whole-party room counts, individual sources, bad-mechanic cleanup, and real room-file loading with malformed/out-of-bounds neighbors. |
| 13. Structures and chunk assembly | `StructureFileContractTest`, `ChunkAssemblyContractTest`: real compressed files, coordinates/palette/empty and legacy formats, delimiter-bearing tile payloads, negative chunk boundaries, block placement, and tile-data application after placement. External SNBT/native tile application remains a boundary. |
| 14. Configuration and persistence | `DungeonGraphPersistenceTest`, `MechanicGraphPersistenceContractTest`, `RegisteredMechanicContractTest`, `ConfigSerializableModelTest`, `ConfigurationFileTest`, `RepositoryContractTest`, `DungeonRuleConfigurationTest`, `GeneratorConfigMigrationTest`: real YAML graphs and concrete types, defaults/false/empty overrides, stable IDs, malformed neighbors, operator edits and repeatable migration, failed serialization/write/scheduling completion, recovery after repair, player data, and save-time admission guards. |
| 15. Editing and saving | `EditorPersistenceContractTest`, `BranchingEditorPersistenceContractTest`, `EditorHotbarContractTest`, `PlayerStateContractTest`: real editor admission/departure, cancelled exit preserving inventory/clipboard, stale inventory callbacks, managed hotbar/chest drag/swap/drop/back behavior, world/function/room/schematic persistence, overlapping saves/autosaves, failure completion/latches, and pending-save shutdown. |
| 16. Commands and bootstrap | `CommandContractTest`, `ConfigAuthorizationContractTest`, `InputSafetyContractTest`, `RegistryContractTest`, `RegistryResourceContractTest`, `ResourceBootstrapContractTest`: permission and sender boundaries, typed persistence, finite inputs, player start routing, scoped reload/disable, confirmed deletion, safe import and actual copy, config/language resources, runtime/serializer/editor registrations, listener prototype cleanup, and registration outside the plugin entry-point package. |

The [mechanic coverage map](MECHANIC_TEST_COVERAGE.md) lists all **54 registered concrete
mechanics**, their pinned persisted IDs, and their execution contracts. Registration and
serialization checks support that map; they do not replace execution assertions.

## Final measurements and verification

Measurements compare the previous completed suite at `a4b3163` with the clean final suite.
JaCoCo counts the whole production codebase, including inner and anonymous classes. The
separate Paper acceptance process does not contribute to these unit measurements.

| Scope | Previous coverage | Final coverage |
| --- | ---: | ---: |
| Lines | 17.86% (4,212/23,587) | **45.95% (10,906/23,732)** |
| Branches | 15.56% (1,555/9,993) | **36.81% (3,714/10,091)** |

Selected production classes, excluding their inner/anonymous classes:

| Class | Lines | Branches |
| --- | ---: | ---: |
| `RegistryBootstrap` | 97.8% | 86.4% |
| `DungeonInstance` | 74.2% | 57.4% |
| `PlayableInstance` | 65.9% | 50.0% |
| `EditableInstance` | 58.2% | 34.2% |
| `DungeonPlayerSession` | 71.1% | 56.9% |
| `PlayListener` | 86.1% | 64.4% |
| `InstanceListener` | 61.6% | 43.0% |
| `DungeonTeamService` | 65.9% | 47.4% |
| `OpenInstance` | 69.4% | 56.8% |
| `Layout` | 83.7% | 65.3% |
| `BranchingLayout` | 75.1% | 60.6% |
| `ConfigurationFile` | 77.1% | 68.2% |

The final clean verification uses shuffled class and method order with seed `20261106`.
All 264 tests pass; Checkstyle reports zero violations, and `scripts/verify-artifact.sh`
passes for `target/Dungeons-1.2.11.jar`. The baseline's real-Paper acceptance run was already
successful; this final unit-suite implementation did not rerun that separate profile.
Commands and report locations are in [Testing](TESTING.md).

## Defects exposed during the final implementation

Regression scenarios demonstrated production defects before fixes were committed. Important
examples include:

- inventory snapshots sharing mutable items, rejected editor admission clearing another run's
  inventory, cancelled editor exit clearing state, and delayed editor callbacks overwriting
  restored survival items;
- stale entry/checkpoint callbacks teleporting or modifying departed players, hunger resetting
  above the playable maximum, final-life departure attempting to teleport a still-dead player,
  and tagged item transfer losing or duplicating equal stacks;
- delayed branch admission missing a final connectivity check, open-run admission consuming
  another run's reserved slots, and stale team-disband callbacks scheduling work after shutdown;
- cloned trigger counters/spawner/redstone state sharing runtime ownership, condition polling
  cancelling only the latest waiter, and cleanup failures abandoning other resources;
- reward menus sharing mutable stacks across players, loot draws terminating incorrectly when
  disabled entries outnumber eligible entries, and malformed loot records hiding valid tables;
- nested mechanic clones keeping the wrong parent, failed function/room startup leaking partial
  registration, rotated teleport targets losing their facing, and temporary remote listeners
  surviving failed dispatch;
- generation rejection/interruption and editor serialization/commit errors leaving incomplete
  cleanup or unresolved completion; overlapping autosaves reporting a false failure;
- bootstrap discovering graph models in the wrong package, malformed type discriminators
  constructing unintended fallback objects, registry metadata prototypes leaking listeners,
  and chest-view drag slot mapping protecting the wrong inventory.

Each relevant commit contains the executable contract and its corresponding fix. Assertions
were not weakened to accept the defective behavior.

## Remaining boundaries

This is broad core-system coverage, not a claim that every gameplay branch or user interaction
has been exercised. The remaining coverage is concentrated in GUI construction, administration,
rendering, native server operations, and alternate configuration branches. In particular:

- Server interfaces are substituted at explicit boundaries. Real world-folder operations,
  project serializers, domain graphs, run/session objects, and mechanic dispatch execute in
  the tests; Paper's actual world loading/unloading, chunk engine, threading, registry-backed
  mob health/damage attributes, spawn-room tagging on living mobs, dragon boss bars, minimaps,
  native game-rule constants, and network/plugin interoperability need server integration tests.
- Item fixtures model quantities, clones, inventory slots, copied metadata, and persistent-data
  tags. They test project key/tagging/ledger policies and exact handoff behavior. They do not
  certify native Paper ItemStack YAML serialization or native metadata interoperability.
- GUI boundaries substitute icon rendering/windows. Hotbar and reward callbacks execute, but
  visual layouts, every editor clipboard selection/paste gesture, and every administrative
  completion path are not covered by this suite.
- Ordinary file/directory write faults and scheduling/serialization errors are covered. Crash,
  power-loss, disk-full, and interrupted-write atomicity are not implemented or claimed. This
  pass does not introduce a new filesystem durability design.
- Refused/failed world unload retains files and the world reference. Automatic retry or
  re-registration after disposal is still outside the implemented contract.

Bukkit, serializer aliases, language resources, and RuntimeContext are process-wide APIs.
Fixtures scope and restore global state; tests run sequentially and capture scheduled tasks
for deterministic execution without wall-clock sleeps. Do not enable concurrent execution
of these fixtures. Coverage is used to locate gaps, not as an arbitrary acceptance threshold.

## Earlier passes (historical)

The initial pass exposed fifteen defects, fixed without relaxing their assertions:

1. Stale queue cleanup could erase a replacement queue and refund the wrong player state.
2. Cancelled child events could activate gates; gate isolation used world references rather than instances.
3. A non-string type discriminator aborted a whole persisted list.
4. Empty structure files could not reload.
5. Delimiter-bearing tile text split structure records and lost neighboring blocks.
6. Open-run capacity arithmetic overflowed for oversized requests.
7. Access and dungeon loot monthly resets could return dates in the past.
8. Reward reset day 31 drifted to day 28 after February.
9. An unrelated trigger of the same Java class could execute a function.
10. Infinite or unrepresentable weights poisoned room/reward pools.
11. Partial inventory acceptance dropped the entire supplied stack, duplicating items.
12. Variable display narrowed large integral values to an int and corrupted trigger comparisons.
13. Expiring an old loot claim could remove a newer claim at the same location.
14. Team targeting could fall back to a source player outside the requested run.
15. Door clones shared mutable location objects with their templates.

Structure writes now use a versioned payload while retaining the legacy reader. Calendar
reset logic is shared by access, loot, and reward functions. Operator-visible semantics
and file rollback limitations are documented in [Configuration](CONFIGURATION.md).

### Earlier coverage audit

The second pass added 30 scenarios, one new `@Test` per commit. Ten additional production
fixes were driven by failing tests:

1. Disabling a delayed function cancelled only its most recent activation.
2. Repeated child execution could mutate the target list used by later ticks.
3. Overlapping repeater activations shared their repeat count and only one could be cancelled.
4. Decimal editor input accepted non-finite numbers and threw on missing input.
5. Generator configuration commands persisted NaN/infinity.
6. Disposal deleted world files even when Paper refused to unload the world.
7. Initialization failure could delete a still-loaded world or leave initialization waiters blocked when unloading failed.
8. An unrelated player YAML filename could prevent dungeon loading; invalid YAML was registered as empty player data.
9. A wrong-type reward record prevented dungeon loading and made valid neighboring player state unavailable.
10. Issuing a key after a failed ledger read overwrote the unreadable ledger. Issuance now waits for a successful read and recovers after repair.

The audit compared a clean 83-test baseline at `eb0e067` with a clean 113-test run, with
class and method order shuffled using seed `20261106`. These are **unit-test** JaCoCo
measurements; the separate Paper acceptance process is not included. No production
classes were excluded to improve the percentages.

| Scope | Line coverage before | Line coverage after | Branch coverage before | Branch coverage after |
| --- | ---: | ---: | ---: | ---: |
| Whole codebase | 9.0% (2,109/23,544) | 17.9% (4,212/23,587) | 7.7% (772/9,981) | 15.6% (1,555/9,993) |
| Queue coordinator | 0% | 66.8% | 0% | 57.5% |
| Dungeon repository | 0% | 64.2% | 0% | 62.5% |
| Branching layout | 0% | 69.5% | 0% | 53.3% |
| Shared layout | 17.6% | 67.3% | 8.2% | 61.8% |
| Dungeon definition, including key ledger | 17.6% | 45.0% | 10.0% | 37.9% |
| Base dungeon instance | 3.6% | 26.0% | 0.7% | 10.1% |
| Numeric editor input | 0% | 100% | No branches | 100% |

Class rows refer to the named class, without anonymous/inner classes. Whole-codebase
coverage includes those classes. Source totals changed because the fixes added guards and
cleanup paths. These measurements identify exercised paths; they do not establish that the
whole system is covered.
