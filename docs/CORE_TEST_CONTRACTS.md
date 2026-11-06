# Core system test contracts

These tests protect observable system guarantees, not individual implementation lines.
Each new scenario is committed separately, alongside any production fix it proves necessary.

| System | Required guarantee |
| --- | --- |
| Configuration and persistence | A saved graph restores real types and values; a bad record cannot discard valid neighbors; a failed save cannot destroy the last good file. |
| Configuration inheritance | Missing values inherit; explicit false and empty collections override; migration preserves operator edits and can be repeated safely. |
| Queues and teams | One player belongs to one pending start; removing or replacing a queue refunds the right reservations and does not disturb another queue; FIFO skips starts that cannot fit. |
| Capacity and admission | Disabled/full dungeons cannot start; global and dungeon limits are independent; reserved open-run slots cannot be double allocated. |
| Lifecycle | Reload/shutdown cancel pending starts, refund keys, detach players, and dispose runs without one failed run abandoning other cleanup. |
| Trigger mechanics | AND waits for every child and resets; OR fires for each configured child; other runs and unrelated triggers cannot activate either gate. |
| Functions and variables | Function ordering and player targets are preserved; numeric variables retain their values through arithmetic and comparisons. |
| Generation | Room eligibility honors filters and occurrence limits; placement rejects overlap; rotating or cloning rooms cannot mutate the source. |
| Structure files | Coordinates, block states, and tile data survive save/load, including delimiter-like text; empty structures round-trip. |
| Rewards | Weighted selection respects interval boundaries; invalid weights cannot poison the pool; reward claims/cooldowns cannot leak between runs or players. |
| Cooldowns | Reset timestamps are strictly in the future and follow the selected hour/day/month, including month ends and DST. |

Tests use real domain objects and services. Mockito is test-scoped and supplied by the
HauntedMC dependency platform; only external collaborators are substituted. The existing
real-Paper acceptance suite remains the check for server startup, migration, and commands.

A test suite cannot establish correctness of every Paper implementation or multiplayer race.
The final coverage notes must distinguish executable contracts from acceptance-only areas.
