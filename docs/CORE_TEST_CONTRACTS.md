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

The suite contains **113 tests** across 46 test classes: 44 baseline tests, 39 scenarios
from the initial contract pass, and 30 scenarios from the coverage audit. Each new `@Test` method was added in its own commit. No test is disabled,
skipped, or marked as an expected failure to accommodate a production defect.

| Contract area | Tests |
| --- | --- |
| Actual YAML graph save/load and malformed-neighbor recovery | `DungeonGraphPersistenceTest` |
| Save failure completion and preservation of the last good file | `ConfigurationFileTest` |
| Inheritance and repeatable generator migration | Existing `DungeonRuleConfigurationTest`, `GeneratorConfigMigrationTest` |
| Queue replacement, stale cleanup, ordering, offline removal, leader/member departure | `QueueContractTest` |
| Coordinator launch/retry, invalidated queue entries, partial team key rollback, revoked keys, leader-only policy changes | `QueueLaunchContractTest` |
| Admission limits and active-run registration | `AdmissionContractTest` |
| Open-run team slot reservations and release | `OpenRunCapacityTest` |
| Key ownership, consumption, replay protection, offline refund delivery | `AccessKeyReservationContractTest` |
| Real issued-key ledger save/load, reservation recovery, consumption/replay, rotation/revocation, malformed-ledger repair, mismatched tags | `IssuedKeyLedgerContractTest` |
| Team invitation/start/shutdown and target isolation | `TeamLifecycleContractTest` |
| Reload refunds, pending starts, player restoration/detachment, disposal failure isolation | `LifecycleContractTest` |
| Real base-instance disposal, hook failure isolation, unload refusal/exception, initialization waiter completion | `RunDisposalContractTest` |
| AND/OR gate state and cancelled/unrelated events | `GateMechanicsTest` |
| Condition polling, scheduled dispatch, task cancellation, preserved event/player source | `TriggerLifecycleContractTest` |
| Bound-trigger dispatch and cancellation | `FunctionDispatchContractTest` |
| Composite execution order, child failures, isolated targets, looping sequence steps | `FunctionCompositionContractTest` |
| Delayed/repeating execution, overlapping activations, target isolation, cancellation of every pending task | `ScheduledFunctionContractTest` |
| Large numeric variables through real run state and placeholder comparisons | `VariableMechanicsContractTest` |
| Placed/queued overlap, adjacency, combined filters, occurrence/depth limits | `GenerationMechanicsTest` |
| Complete connector graph generation, fresh subsequent runs, incompatible required rooms | `LayoutExecutionContractTest` |
| Complete trunk/side-branch placement, connectivity/overlap, required versus optional missing anchors | `BranchingExecutionContractTest` |
| Clone/rotation coordinate and filter isolation | `RoomTransformContractTest` |
| Disk structure round trips, empty files, old files, tile-text delimiters | `StructureFileContractTest` |
| Deterministic weighted intervals, invalid weights, safe reuse | `WeightedSelectionContractTest` |
| Claim isolation across players and world copies, old-versus-new cooldown records | `RewardClaimContractTest` |
| Actual folder discovery, malformed/unknown dungeon configs, bad player files and reward records, save-time launch/editor guard | `RepositoryContractTest` |
| Dungeon-specific edit permission, persisted changes, rejection of non-finite generator values | `ConfigAuthorizationContractTest` |
| Missing, malformed, non-finite, overflow, and valid numeric editor inputs | `InputSafetyContractTest` |
| Calendar reset wiring and boundary tables, DST, leap months | `CooldownContractTest`, `RewardCooldownContractTest` |

The table-driven date cases and deterministic random-source tests assert known outcomes;
there are no statistical pass thresholds or wall-clock sleeps. Tests of data persistence
use temporary directories and real YAML/compressed structure files. Tile payload fidelity
substitutes the SNBT normalizer; it does not claim to test the external NBT library itself.

## Defects exposed and fixed

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

## Coverage audit continuation

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

The most important remaining gaps, in priority order, are:

1. **Full player entry/exit and rollback.** `PlayableInstance.addPlayer/removePlayer`,
   `DungeonInstance.removePlayer`, player-state restoration, death/lives handling, and
   disconnect/reconnect ordering still need direct execution contracts. Existing lifecycle
   tests verify service coordination but substitute parts of the run/player implementation.
2. **Asynchronous run preparation.** Test player disconnects and team changes between world
   creation and final admission, failed-world cleanup, and release of every reserved slot.
3. **Active-team teardown.** Disband, kick, leave, invite expiration, and delayed shutdown
   need more coverage while players are already in a run.
4. **Generation recovery.** Checkpoint rollback, cancellation, timeout handling, and repeated
   impossible minimum targets remain only partly exercised. The new execution scenarios use
   deterministic room metadata and real layout workers, connectors, rotations, and placements.
5. **Filesystem write faults and item persistence.** Serialization failure and malformed-read
   recovery are covered, but disk-full/permission/interrupted-write durability is not. Ledger
   tests supply a real key definition directly and substitute Paper item/PDC storage; they
   exercise the real ledger and tagging helpers, not Paper's ItemStack YAML serialization.

A refused/failed world unload now retains the files and world reference. Disposal still
unregisters the run before the unload attempt; automatic retry/re-registration is not covered
or implemented by this change.

## Verification and boundaries

A clean, shuffled-order Maven verification passes, including all 113 tests and Checkstyle.
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
