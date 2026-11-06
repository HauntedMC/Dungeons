# Core system test contracts

These tests protect observable system guarantees, not individual implementation lines.
Each new scenario is committed separately, alongside any production fix it proves necessary.

| System | Required guarantee |
| --- | --- |
| Configuration and persistence | A saved graph restores real types and values; a bad record cannot discard valid neighbors; a serialization failure cannot overwrite the last good file. |
| Configuration inheritance | Missing values inherit; explicit false and empty collections override; migration preserves operator edits and can be repeated safely. |
| Queues and teams | One player belongs to one pending start; removing or replacing a queue refunds the right reservations and does not disturb another queue; FIFO skips starts that cannot fit. |
| Capacity and admission | Disabled/full dungeons cannot start; global and dungeon limits are independent; reserved open-run slots cannot be double allocated. |
| Lifecycle | Reload/shutdown cancel pending starts, refund keys, detach players, and dispose runs without one failed run abandoning other cleanup. |
| Trigger mechanics | AND waits for every child and resets; OR fires for each configured child; other runs and unrelated triggers cannot activate either gate. |
| Functions and variables | Function ordering and player targets are preserved; numeric variables retain their values through arithmetic and comparisons. |
| Generation | Room eligibility honors filters and occurrence limits; placement rejects overlap; rotating or cloning rooms cannot mutate the source. |
| Structure files | Coordinates, block states, and tile data survive save/load, including delimiter-like text; empty structures round-trip. |
| Rewards | Weighted selection respects interval boundaries; invalid weights cannot poison the pool; reward claims/cooldowns cannot leak between runs or players. |
| Cooldowns | Calendar reset timestamps are strictly in the future and follow the selected hour/day/month, including month ends and DST. |

Tests use real domain objects and services. Mockito is test-scoped and supplied by the
HauntedMC dependency platform; only external collaborators are substituted. The existing
real-Paper acceptance suite remains the check for server startup, migration, and commands.

## Executable coverage map

The suite contains **83 tests** across 37 test classes: 44 baseline tests and 39 new
system scenarios. Each new `@Test` method was added in its own commit. No test is disabled,
skipped, or marked as an expected failure to accommodate a production defect.

| Contract area | Tests |
| --- | --- |
| Actual YAML graph save/load and malformed-neighbor recovery | `DungeonGraphPersistenceTest` |
| Save failure completion and preservation of the last good file | `ConfigurationFileTest` |
| Inheritance and repeatable generator migration | Existing `DungeonRuleConfigurationTest`, `GeneratorConfigMigrationTest` |
| Queue replacement, stale cleanup, ordering, offline removal, leader/member departure | `QueueContractTest` |
| Admission limits and active-run registration | `AdmissionContractTest` |
| Open-run team slot reservations and release | `OpenRunCapacityTest` |
| Key ownership, consumption, replay protection, offline refund delivery | `AccessKeyReservationContractTest` |
| Team invitation/start/shutdown and target isolation | `TeamLifecycleContractTest` |
| Reload refunds, pending starts, player restoration/detachment, disposal failure isolation | `LifecycleContractTest` |
| AND/OR gate state and cancelled/unrelated events | `GateMechanicsTest` |
| Condition polling, scheduled dispatch, task cancellation, preserved event/player source | `TriggerLifecycleContractTest` |
| Bound-trigger dispatch and cancellation | `FunctionDispatchContractTest` |
| Composite execution order, child failures, isolated targets, looping sequence steps | `FunctionCompositionContractTest` |
| Large numeric variables through real run state and placeholder comparisons | `VariableMechanicsContractTest` |
| Placed/queued overlap, adjacency, combined filters, occurrence/depth limits | `GenerationMechanicsTest` |
| Clone/rotation coordinate and filter isolation | `RoomTransformContractTest` |
| Disk structure round trips, empty files, old files, tile-text delimiters | `StructureFileContractTest` |
| Deterministic weighted intervals, invalid weights, safe reuse | `WeightedSelectionContractTest` |
| Claim isolation across players and world copies, old-versus-new cooldown records | `RewardClaimContractTest` |
| Calendar reset wiring and boundary tables, DST, leap months | `CooldownContractTest`, `RewardCooldownContractTest` |

The table-driven date cases and deterministic random-source tests assert known outcomes;
there are no statistical pass thresholds or wall-clock sleeps. Tests of data persistence
use temporary directories and real YAML/compressed structure files. Tile payload fidelity
substitutes the SNBT normalizer; it does not claim to test the external NBT library itself.

## Defects exposed and fixed

Fifteen production fixes were driven by failing contracts, without relaxing their assertions:

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

## Verification and boundaries

A clean, shuffled-order Maven verification passes, including all 83 tests and Checkstyle.
The real-Paper profile also passes startup, configuration migration, and command smoke
checks. The packaged artifact audit passes. JaCoCo remains available for inspecting which
code paths a scenario exercises; a high overall percentage is not the acceptance criterion.

Unit contracts cover decisions and effects at explicit service/server boundaries. They do
not substitute for live multiplayer tests of world loading, inventory metadata signing,
network reconnect ordering, or plugin interoperability. Crash/power-loss atomicity and
disk-full failures during an actual filesystem write need fault-injected filesystem tests;
the failed-save preservation scenario covers serialization failure before writing.

Bukkit and RuntimeContext are process-wide APIs. Scoped fixtures restore them after each
test, and these tests run sequentially. Shuffled class and method execution is supported
and checked using a fixed seed; concurrent test execution is not enabled.
