# Staff operations

Open `/dungeon admin` for the in-game dashboard. Console users get a text overview.
`/dungeon admin help` lists authorized commands. `/du` remains an alias. Menu actions
and commands use the same capability checks, confirmation tokens, and lifecycle operations.
Names resolve exactly against online players or the local Paper cache; use a UUID for an
unknown offline player. Support commands do not perform blocking account lookups.

## Assigning access

Assign these permission presets through your permission manager:

| Permission | Intended access |
| --- | --- |
| `dungeons.role.moderator` | Inspection, observation, rescue, removal, queue cancellation, entry restrictions |
| `dungeons.role.builder` | Inspection, editing, maintenance and content backups |
| `dungeons.role.administrator` | Support corrections, content, keys, loot, reload, recovery and restoration |
| `dungeons.role.operator` | All staff capabilities, including console scripts, archival and permanent purge |

Only the operator preset defaults to server operators. Individual capabilities are
`dungeons.staff.<capability>`; targeted checks also accept
`dungeons.staff.<capability>.<dungeon>`. Give `dungeons.core` to custom permission groups.
Global inspection is useful for navigation; grants to mutate content can remain dungeon-specific.
Use explicit negative permissions to deny individual capabilities.
Scoped inspection filters player progression and target completion to authorized dungeons.
Global recovery summaries require global inspection access.

Staff capabilities use only `dungeons.staff.<capability>[.<dungeon>]`. Old administrative,
editing and cooldown permission nodes have no effect. Gameplay bypasses are separately assigned
with `dungeons.bypass.cooldown` and `dungeons.bypass.commands`. Console-command mechanics and
`players.join_commands` require `dungeons.staff.scripts`.

## Maintaining or editing a dungeon

1. Open its dashboard and enable maintenance with a reason, or request a reload/editor action.
2. Existing runs finish. New starts and open-run joins stop. Queued and preparing starts are
   cancelled through their owners, returning uncommitted reserved keys.
3. The operation waits for affected play worlds to unload. A failed disposal leaves the world
   registered and maintenance enabled. Inspect the instance and retry disposal after fixing it.
4. Editors open after drain and a content backup. Gameplay configuration changes also require
   drained maintenance. Display-name changes can apply immediately.
5. Save and close the editor, validate readiness, then explicitly resume admissions.

Pending content operations also block configuration changes, progression corrections and key
issuance for their dungeon. Changing cooldown cadence validates the hour and reset day that the
new cadence will activate; correct those values first if the command rejects the change.

Commands:

```text
/dungeon admin maintenance crypt on Repairing a broken room
/dungeon admin dungeon crypt validate
/dungeon admin reload crypt Apply repaired content
/dungeon admin maintenance crypt off Validation complete
/dungeon admin operations
/dungeon admin cancel <operation-id> Work postponed
```

Maintenance and recent operation results live in `staff.yml`. Maintenance survives restarts.
Interrupted operations are shown as cancelled; they are never automatically replayed.
Operation IDs and instance UUIDs remain usable while worlds load or unload. A short ID is
accepted only when unambiguous. Ending active runs is an explicit, confirmed evacuation,
not a side effect of an ordinary reload. Global configuration reload drains all dungeons;
world-creation settings take effect on future instances.

Use `/dungeon admin` for staff operations, including `/dungeon admin edit <dungeon> <reason>`.
Authoring commands remain under `/dungeon edit`, `/dungeon dungeon` and `/dungeon config`.
Flat command aliases and old player/instance administration groups are not registered.

## Supporting players

```text
/dungeon admin player <player-or-uuid>
/dungeon admin instance <instance-id> observe
/dungeon admin stop-observing
/dungeon admin player <player> rescue Stuck outside the map
/dungeon admin player <player> remove Disrupting the run
/dungeon admin player <player> queue-clear Cancel requested
/dungeon admin player <player> revive Mechanic failed
/dungeon admin player <player> lives 3 Correcting lost lives
/dungeon admin instance <instance-id> extend 300 Compensating downtime
/dungeon admin cooldown <player> crypt clear Failed admission
/dungeon admin loot-cooldown <player> crypt clear Broken reward
/dungeon admin completion <player> crypt true Verified successful finish
/dungeon admin restrict <player> crypt 7d Repeated disruption
/dungeon admin restrict <player> crypt clear Review complete
/dungeon admin compensate <player> DIAMOND 3 Verified missing reward
```

Observation is a separate spectator presence. It does not execute join commands, consume
keys, create participant lives, earn rewards, satisfy player triggers, or count toward run
capacity. Ending observation restores the durable pre-observation state. Departure, world
unload, disconnect, and plugin shutdown also release observation through recovery.

Removal follows normal departure rules. Rescue and evacuation restore the pre-entry recovery
snapshot and do not add access penalties or completion. Durable earned rewards remain claimable.
Keys already committed to an admitted run are not automatically duplicated as refunds.
Compensation and issued keys are delivered through `/dungeon rewards`, including for offline
players. Compensation receipts are written in the same journal update as the pending items,
so retrying the same operation cannot grant twice. A new confirmed compensation is a new grant.

Progression and cooldown corrections work by UUID and commit only after persistence succeeds.
Unreadable records remain protected. Recovery retries require the player online, outside a run,
and respawned. No support action deletes a corrupt journal or guesses missing inventory data.

## Content and destructive actions

```text
/dungeon admin backup crypt Before generator changes
/dungeon admin backups crypt
/dungeon admin restore crypt <backup-id> Revert broken content
/dungeon admin archive crypt Retiring this dungeon
/dungeon admin archives
/dungeon admin unarchive <archive-id> Reopen retired content
/dungeon admin purge <archive-id> Retention review complete
/dungeon admin keys crypt
/dungeon admin keys crypt give <player> <key-id> Replacement authorized
/dungeon admin loot
/dungeon admin loot-remove <table> Unused table retired
```

Content backups retain the latest ten copies per dungeon under `backups/`. They exclude player
progression, key ledgers, native player data, and recovery markers. Restoration preserves current
progression and key ledgers and makes a safety backup first. Failed loading restores the previous
template; interrupted filesystem swaps retain staging data for operator recovery. Retained data
under `staging/` is never discovered as dungeon content or automatically deleted on startup.
The selected restore backup remains pinned while the safety backup is created. A rejected unarchive
returns the directory to its archive location so the operation can be retried.

Archives are stored outside catalog discovery under `archives/`. Archival requires a drained
and unloaded dungeon and preserves its complete directory. Archives are never automatically
purged. Restoration refuses a name already in use. Purge is permanent and separately confirmed.
Loot-table removal refuses references from loaded dungeon mechanic graphs, including nested
wrappers. Check failed/unloaded dungeon content separately before permanently removing shared loot.

Confirmations bind the actor, action, target, parameters, and observed revision. They expire after
30 seconds and work once. Console receives an explicit confirmation command; players can use
clickable chat or the confirmation inventory. Changes to the target require a new confirmation.
Evacuation binds to the participant UUIDs; normal timer ticks do not invalidate its confirmation.

## Failures and recovery

`/dungeon admin problems` shows load and recent operation failures. Instance details show disposal
state and the latest failure category. `/dungeon admin instance <id> retry <reason>` retries safe
unload after the underlying issue is fixed. It refuses occupied worlds.

Dungeon configuration writes use atomic ordered replacement. Failed writes restore the last durable
configuration and report failure. External exits now persist a world UUID/name. Coordinate-only exit locations are invalid.
Internal lobby/start positions must be selected inside that dungeon's editor world.

Staff actions are recorded in `staff-audit.log` and the server log with actor, target, reason,
outcome, and operation ID where applicable. Inventory payloads and console-script contents are not
included. Audit write failures are reported as operational problems. Logs are administrative history,
not a transaction spanning external plugins: arbitrary configured console commands can have effects
that Dungeons cannot roll back.

Retained editor worlds with `.dungeons-editor-recovery` still require manual recovery: preserve a
copy before inspecting or recovering their content. Do not delete player recovery journals to work
around storage, missing-world, or native player checkpoint failures.

## Validation

Run `./mvnw -B -ntp -Pplatform-acceptance verify` for unit/contracts, packaging, style, and native
Paper acceptance. The suite exercises maintenance persistence, confirmation boundaries, drain and
failed unload, observation recovery, save rollback, offline corrections, compensation receipts,
plus the existing dungeon recovery/reward contracts. Native acceptance verifies packaged staff
permissions and inheritance, command binding, real inventory/item metadata, compensation replay
protection, and persisted admission policy alongside world/restart tests. See
[staff validation](STAFF_VALIDATION.md) for the reviewed failures, coverage and test boundaries.

Client sign-off still requires a connected Minecraft client: test dashboard pagination/search,
reason prompts/cancel/timeout, number-key/shift/drag inventory protection, revoked permissions,
stale targets, and observation return. Synthetic native player tests do not substitute for network
interaction or visual inspection.
