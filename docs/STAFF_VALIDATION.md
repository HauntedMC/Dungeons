> Historical validation of 1.2.21. The clean-install cleanup removes obsolete command and storage contracts.

# Staff implementation validation

Validation date: 2026-11-08. This review covers the staff implementation and its connections to
admission, queues, preparations, editors, instance disposal, recovery, content storage and shutdown.

## Regression fixes

| Boundary | Fixed behavior |
| --- | --- |
| Disposal and operation history | Synchronous or exceptional disposal failures terminate the operation, retain maintenance and release the content lock. History write failures cannot run unrecorded work or strand a pending lock. |
| Shutdown | Staff interface, observer and history cleanup failures cannot interrupt the remaining plugin cleanup. Late operation callbacks cannot overwrite shutdown outcomes. New staff mutations reject closed services. |
| Restore and unarchive | Both rejected loads and thrown loader exceptions roll back files. Unarchive failure returns the directory to the archive. Safety-backup pruning cannot delete the selected restore source. |
| Filesystem boundaries | Traversal, source symlinks and redirected managed-storage directories are rejected. Scheduler rejection completes the archive future exceptionally. |
| Validation order | Reasons and correction values are checked before timers, lives, keys, loot or durable player records can change. Cooldown cadence changes validate the hour and reset day they activate. |
| Scope and permissions | Queued/preparing players resolve to their actual dungeon. Restrictions leave other dungeons' queues intact. Reports and target completions filter unauthorized scopes. Explicit denials override grants; builders do not inherit scripts or gameplay bypasses. |
| Authoring | Pending content operations block concurrent configuration/correction/key writes. Editor windows recheck ownership and permissions; revoked access blocks callbacks, editable slots, bottom-inventory shifts and drags. |
| Staff menus | Refreshes preserve valid queued clicks. Closed inventories, stale connections and revoked permissions cannot run callbacks. Invalid prompt input remains retryable; cancel, timeout, quit and shutdown release callbacks. |
| Confirmation | Tokens remain actor/action/target/parameter/revision bound and single use. Evacuation uses the actual participant UUIDs, allowing timer ticks while rejecting changed rosters. |
| Observation and references | Both observer victims and attackers are isolated. Revocation ends observation through recovery. Every branching room graph is traversed even when rooms share relative coordinates. |

## Automated coverage

All eleven new production classes have executable unit/contract coverage. Tests use real staff
services, command dispatch, menu event handlers, persistence and filesystem operations; mocks
substitute Paper scheduling, inventory rendering and connected players where a server is required.
The shared editor window and room whitelist tests run their real guards and callbacks.

Coverage percentages below are line and branch counters for the top-level classes from JaCoCo;
native acceptance is separate and is not counted in those percentages. Full HTML and XML reports
are generated under `target/site/jacoco/`, with individual test results under
`target/surefire-reports/`.

| Class | Lines | Branches |
| --- | ---: | ---: |
| `StaffCommandHandler` | 76% | 60% |
| `StaffMenus` | 86% | 77% |
| `StaffOperations` | 84% | 67% |
| `StaffContentStorage` | 91% | 74% |
| `StaffPermissions` | 100% | 97% |
| `StaffEditGuard` | 92% | 86% |
| `SettingValidation` | 98% | 78% |
| `ContentReferences` | 93% | 100% |
| `StaffObserverListener` | 100% | 69% |
| `StaffPlayerRecords` | 100% | 100% |
| `DepartureCause` | 100% | 100% |

These figures measure executed code, not complete proof of every branch. The command tests cover
readouts, scoped discovery, canonical staff routes, editor backup/open sequencing, maintenance/reload,
archive/restore/purge, player support and corrections, compensation, keys, loot and confirmation
replay. Menu tests cover every screen, pagination/search, action wizards and event ownership.
Failure tests inject corrupt state, blocked storage, failed/exceptional loads, disposal failure,
revoked permissions, scheduler rejection and shutdown during running operations.

## Verification

```sh
./mvnw -B -ntp -Pplatform-acceptance verify
bash scripts/verify-artifact.sh
bash -n src/acceptance/run-acceptance.sh
git diff --check
```

Final result: **479 unit/contract tests passed, zero failures, errors or skips**. This adds 45
regression tests to the previous 434-test suite. All thirteen native integration scenarios and
the cold-restart scenario passed. Checkstyle reported zero violations; the artifact, shell syntax
and whitespace audits passed. The distributable is `target/Dungeons-1.2.20.jar`.

Native acceptance runs the packaged plugin on Paper, including thirteen integration scenarios
and one cold-restart scenario. Staff checks exercise actual Bukkit permission inheritance,
registered command dispatch, real inventory/item metadata, persisted maintenance, offline reward
compensation and replay rejection. The other scenarios cover native player recovery, world
formats, generation, mechanics, unload retries, warm pools and shutdown/restart persistence.

## Connected-client boundary

No connected Minecraft client was available during this validation. Inventory appearance, network
interaction and observation return still need a client smoke test. Automated tests cover
pagination/search, prompt retry/cancel/timeout, shift/number-key/drag protection, stale targets,
connection replacement, revoked permissions and recovery; they do not certify visual presentation
or behavior with every external plugin/permission manager combination.
