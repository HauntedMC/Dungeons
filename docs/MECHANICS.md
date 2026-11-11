# Dungeon mechanics

All built-ins are discovered automatically and available from the function, trigger and
condition browsers. Persisted aliases are recorded in
`src/test/resources/contracts/mechanics.csv`; registration tests verify every discovered
alias, restoration of its concrete class, inherited options and serialization round trips.
Existing aliases remain unchanged.

## New mechanics

| Alias (prefix `dungeons.`) | Settings | Behavior |
| --- | --- | --- |
| `trigger.timer` | `delay: 20`, `interval: 20`, `allowRetrigger: false` | Runs after the delay and retries at the interval. Stops after the first accepted activation unless retriggering is allowed. Both durations use server ticks. |
| `trigger.variable_change` | `variable: score`, `interval: 1`, `allowRetrigger: false` | Samples the variable and fires when its stored text changes, including creation/removal. Initial state does not fire. Multiple writes between samples collapse into one change. |
| `condition.player_health` | `minimum: 0`, `maximum: 20` | Inclusive health range for the event's source player; health points, two per heart. Missing source, observers and invalid ranges fail. |
| `condition.inventory` | `material: TRIPWIRE_HOOK`, `count: 1` | Requires at least this many items across storage slots; excludes armor/offhand and does not consume items. Material-only matching; use the existing key trigger for dungeon keys and their ownership rules. |
| `condition.block` | `material: REDSTONE_BLOCK`, `offsetX: 0`, `offsetY: -1`, `offsetZ: 0` | Tests a block relative to the function. Unloaded chunks, missing locations and positions outside world height fail without loading terrain. |
| `function.health` | `amount: 20`, `relative: false` | Sets health, or adds/subtracts with relative mode. Clamps to zero and the player's effective maximum. Skips observers, dead players and players outside this instance. Zero health kills; negative relative values directly reduce health without the damage-event/armor pipeline. |
| `function.conditional` | `comparison: ''`, `functions: []` | Evaluates a comparison at execution time, then runs its child group when true. Compose multiple groups for branches, including inside sequences and delayed functions. |

Every new setting has a hotbar editor control. Timers and variable watchers have no source
player: use a room/team-capable event or another player-originating trigger for functions
that need a player. Source-player conditions fail on events without a player; inversion
still negates that result as it does for all conditions. Timer/variable triggers can be
children of AND/OR gates. Their tasks belong to the instance and clones own separate tasks.
An open dungeon keeps its existing per-player activation rules; source-free timer events
use the normal instance-level single/repeat behavior.

Comparisons support `=`, `==`, `!=`, `>`, `>=`, `<`, `<=` and `<variable>` placeholders.
Equality compares decimal numbers exactly and other text case-sensitively. Ordered comparisons
require finite decimal operands; large integers and decimal precision are retained. Malformed expressions and nonnumeric ordered operands return
false. Both operands must be present and the entire expression must match; chained comparisons are unsupported. Existing
placeholder substitution preserves missing values as the literal text `null`.

## Correctness changes

- Successful condition pollers stop even when open-dungeon rules keep the trigger enabled.
  Failed conditions and cancelled events do not consume an open-dungeon player's activation.
  Retried events reset cancellation before being delivered again.
- Disabling releases active state and rejects subsequent queued trigger dispatch. Elements
  can be enabled again. Cloned detector scans cannot cancel their template's scan.
- Distance detectors evaluate conditions with the actual source player and record presence
  only after accepted execution. Failed conditions or cancelled events can retry; per-player
  detectors keep scanning for the other players.
- Room-limited chat, distance, death and remote triggers require an actual shared room;
  two missing rooms no longer count as the same room.
- Non-finite proximity radii, signal ranges and sound numbers are rejected. Bounded remote
  signals with missing receiver locations fail safely. Negative signal ranges are rejected.
- Mob checks handle missing entity names using their existing invalid-type fallback.
  The elapsed-time editor now labels its normal/inverted bounds correctly.

## Lifecycle and scheduling safeguards

- A waiting, non-retriggerable trigger owns at most one condition poller per source, including
  source-free signals. Scheduler rejection releases the admission slot so a later retry works.
- Queued/polling player triggers stop when their source leaves the instance or becomes an
  observer. Event delivery rechecks this before effects. Departure listeners preserve their
  original cause; player-targeted effects still exclude participants who have left the run.
- Failed startup rolls back listeners, conditions and scheduled work. Independent cleanup
  continues when a condition, gate child, nested trigger or resource hook throws.
- Delayed dungeon-start callbacks and variable writes are cancelled on disable and are not
  shared by clones. Start callbacks select a remaining participant if the first one left.
  Deferred variable writes capture their original operation/name/value; invalid numeric
  arithmetic is rejected before scheduling.
- Sequences restart from their first step when cloned or enabled for a new run. Completed
  non-looping sequences remain complete until enabled again, and removing a pending next
  step cannot leave the sequence index outside the list.
- Variable substitution is one literal pass. A stored value containing another placeholder,
  backslashes or dollar signs is retained verbatim.

## Validation

The unit suite covers discovery/persistence for every registered mechanic, function target
dispatch, trigger/gate lifecycle and cloning, condition inversion, variable expressions,
player/world effects, composition, scheduled functions and shutdown cleanup. New tests add
runtime contracts for the seven additions and the correctness changes, including boundary
values and malformed input. Run `./mvnw -B -ntp verify` for tests, packaging and formatting.
These tests mock the Paper boundary; they do not replace live gameplay acceptance on a
Paper server. Database integration and native platform acceptance use separate Maven profiles.
