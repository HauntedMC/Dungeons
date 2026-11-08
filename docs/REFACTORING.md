# Refactoring worklist

The repository-wide refactoring is **in progress**. Every production source file and
package has structural inventory coverage; semantic review of every class and the
full second review pass remain unfinished. An unflagged file is not an assertion
that its design is finished.

## Inventory

[REFACTORING_INVENTORY.tsv](REFACTORING_INVENTORY.tsv) records all 323 production
Java files across 68 packages, including package documentation. It reports source
size, method count and the longest method for each file. Regenerate it with:

```bash
java tools/architecture/SourceInventory.java > docs/REFACTORING_INVENTORY.tsv
```

See [the inventory tool](../tools/architecture/README.md) for interpretation.
The 500-line class and 80-line method signals prioritize review, rather than impose
arbitrary split requirements. They cannot identify ownership problems in small
classes, hidden dependencies, or missing behavioral tests.

## Completed boundaries

Each extraction or method decomposition has a separate commit, with its relevant
existing contract tests and Checkstyle checked before committing.

- Root command branches: editor workflows, world import/creation, dungeon
  administration and player run commands. A later pass separated world-copy and
  completion phases and consolidated shared completion policy.
- Dungeon definition: issued-key lifecycle and durable ledger rollback moved to
  `DungeonAccessKeyService`; stale-world cleanup moved to `InstanceWorldCleanup`.
  Existing public dungeon operations delegate to these services.
- Editor menus: conditions, gates, composites, function and trigger browsers have
  separate workflow builders and shared access-controlled window support.
- Hotbars: function, room and connector/door editor builders are separate from
  each other and from layout generation. A second pass extracted connector
  clipboard validation and transforms from the menu callback.
- Staff screens: the large screen switch was decomposed into screen builders,
  then screen content was separated from inventory/prompt lifetime and refresh.
- Content menus: named item factories replace large assembly methods in
  `HologramFunction`, `BlockEditorFunction`, `SpawnMobFunction`, `CommandFunction`,
  `RewardFunction`, `LootTableRewardsFunction` and `RepeaterFunction`.

The extraction pass retained command routing, permission decisions, serialization
identities, dungeon public entry points and failure behavior. New tests exercise
creation phase ordering, dynamic connector door-menu/range references, and
connector copy/move and rejected placement.

## Size comparison

Compared with baseline commit `c127c149e467c421034b6b0280359d84551ad4f7`:

| File | Before | After |
| --- | ---: | ---: |
| `DungeonCommand` | 2,411 | 454 |
| `DungeonDefinition` | 1,731 | 1,212 |
| `Layout` | 1,286 | 841 |
| `EditorMenus` | 1,076 | 40 |
| `HotbarMenus` | 851 | 30 |
| `StaffMenus` | 1,020 | 387 |

The longest method decreased from 593 to 277 lines. Production source increased
from 60,187 to 60,586 lines as focused classes, explicit dependencies and delegates
replaced nested ownership. There are still 30 files over 500 lines and 56 files
with a method over 80 lines. Those counts include the extracted builders and
must not be interpreted as a completed architecture cleanup.

## Next semantic review priorities

1. `DungeonInstance`: isolate disposal attempts, retry admission, persistence,
   unload completion and detached-directory deletion. Preserve single-attempt
   observers, cancellation isolation, retryable failures and retained capacity.
   Use `RunDisposalContractTest`, `WorldStartupContractTest` and native disposal
   acceptance before altering state ownership.
2. `DungeonPlayerSession`: separate editor selection/clipboard state and hotbar
   navigation from recovery, key escrow and reward transactions. Review every
   session getter/caller, not just long methods. Preserve reconnect, expired-session
   and native checkpoint behavior using the player recovery and state contracts.
3. `DungeonQueueCoordinator` and `DungeonTeamService`: separate policy decisions
   from reservation/launch and membership transitions. Preserve rollback and
   capacity rules using admission, queue launch, team join and transition contracts.
4. `StaffCommandHandler` and `command.config`: extract command groups and dynamic
   entry builders. Keep execution-time authorization and stale catalogue checks
   together with the operation they protect.
5. `BranchingRoomDefinition`, `Layout`, `BranchingLayout` and `PlayableInstance`:
   separate persistence, generation search/checkpoint state and run lifecycle where
   each has an independent owner. Preserve clone isolation, generation recovery,
   capacity and final-save guarantees.
6. Remaining UI work: `RoomMenus`, `StaffScreenRows`, `RoomEditHotbar`, loot menus,
   and content function/trigger/condition callbacks. Prefer workflow or reusable
   editor actions when they reduce coupling; a factory alone does not resolve a
   complex interaction callback.
7. Review every remaining inventory row, including small classes and packages
   without size signals. Bootstrap, configuration, listeners, registries,
   serialization, utilities and world-generation adapters require ownership and
   dependency review as well. Repeat the semantic review after those changes.

## Verification

Before the first change, `./mvnw -B -ntp verify` passed. After the completed batch,
`./mvnw -B -ntp -Pplatform-acceptance verify` passed with 476 unit/contract tests,
zero failures/errors/skips, zero Checkstyle violations, and successful native
Paper integration/acceptance including restart phases. Inventory generation was
also checked for deterministic output.
