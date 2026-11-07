# System reliability review

This review follows the Paper 26.3 inventory regression. The previous test boundary
used the same incorrect 41-slot assumption as production. Test counts alone were
insufficient evidence of compatibility. No review can guarantee an absence of faults.

## Ownership and commit boundaries

| Resource | Owner and durable boundary | Failure behavior |
| --- | --- | --- |
| Player admission | One preparation registry entry per session, including static, open and editor requests | Cancellation releases its own reservation; stale callbacks cannot modify a newer attempt |
| Player inventory | Recovery snapshot before dungeon mutation; native transaction marker verified before journal acknowledgement | Failed restoration retains the record and prevents unsafe play; reconnect retries it |
| Access-key reservation | Journal escrow plus a post-removal inventory checkpoint before a queue is published | A crash before native saving first replays removal, then refunds escrow; a failed escrow write restores the original inventory |
| Key consumption and rotation | Atomic per-dungeon ledger replacement; failed replacement restores the last committed in-memory ledger | Failure propagates before join commands can reach other services |
| Rewards and key refunds | One write-ahead delivery protocol for both online and offline players | Overflow remains claimable; pending deliveries survive reconnect and session expiry |
| Editor save | Owner-thread capture, immutable metadata, ordered asynchronous filesystem work | Disposal waits for a fresh final save; failures retain managed worlds and recovery files |
| World disposal | Explicit retryable lifecycle state | Failed unload retains registration and capacity; deletion only targets detached unloaded folders |
| Catalog reload | Editor disposal barrier before replacing definitions; concurrent reload requests are rejected | Failed editor save aborts replacement; malformed definitions retain their previous working entries |
| Shutdown | Independent preparation, queue, team, player and world cleanup stages, followed by persistence draining | One failure does not prevent attempts to settle unrelated resources |
| Session expiry | Runtime ownership ends and durable checkpoint succeeds before memory is released | Pending recovery, escrow and rewards remain on disk; expired-session callbacks are rejected |

All mutable runtime and Bukkit state is owned by the server thread. Worker tasks
receive captured filesystem inputs and return to the owner thread for world or
session changes. Observer cancellation does not transfer world/save ownership.

## Verification

Regression coverage includes refused recovery admission, duplicate reserved joins,
cancelled open admission followed by a new attempt, rejected worker scheduling,
shutdown hook failure, expired editor callbacks, online refund save failure,
reservation crash before native saving, session loss during escrow, rejected key
ledger replacement, editor-save barriers and malformed dungeon reloads.

The shared player fixture uses 43 slots. The native Paper acceptance recovery case
uses actual Paper inventory/equipment containers, including body, saddle, armor
and offhand slots. The platform acceptance profile also exercises world lifecycle,
formats, unload retry, generation, warm pools and cold restart persistence.

Run `./mvnw -Pplatform-acceptance verify`. These checks do not represent a load test
or an end-to-end simulation of every plugin installed on the production server.

## Compatibility and limits

Journal version 2 reads version 1 records. Older plugin versions reject version 2
rather than silently ignoring key escrow; preserve the journal when upgrading or
investigating a failed transaction.

There is no single atomic transaction spanning the player journal, the dungeon key
ledger and external plugins. In particular, a process failure during unique-key
rotation can occur between ledger publication and settlement of the replacement
key in the player journal. Those two files retain evidence, but reconciliation may
be required. The implementation must not be described as exactly-once across that
boundary. An outbox/reconciliation protocol for ledger-to-player transfers is
further design work.

Join commands execute only after access-key finalization. Commands that grant money,
items or permissions through other plugins cannot be rolled back or replayed safely
without an idempotency protocol supported by the receiving service. Native player
saving and journal replay do not provide that protocol.

Atomic replacement and file flushing protect the tested process-loss boundaries.
They do not guarantee directory-entry durability through hardware power failure.
Other plugins can mutate inventory or cancel teleport/unload operations. The
corresponding plugin, network and storage combinations need staging verification;
missing worlds, rejected operations and corrupt records remain recovery conditions,
not grounds for deleting saved data.
