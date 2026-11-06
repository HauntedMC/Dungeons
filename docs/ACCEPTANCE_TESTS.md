# Paper integration and acceptance tests

The `platform-acceptance` profile runs the actual distributable with a separate consumer
plugin on the HauntedPlatform-pinned Paper runtime. It follows the real-server consumer
pattern used by the other HauntedMC projects. These tests use native server APIs and the
packaged Dungeons implementation, without Mockito or headless GUI replacements.

## Executable contracts

The runner produces nine JUnit test cases across two server processes. The original shell
checks also verify legacy generator migration, configuration commands, and help output.

| Case | Protected behavior |
| --- | --- |
| `packaged-bootstrap` | Actual plugin, command, runtime, migrated catalog entry, and all function/trigger/condition registrations are present. |
| `native-item-and-graph-persistence` | Native ItemStack quantities, Adventure names/lore, persistent data, configured and issued key identities, concrete graph types, nested fields, and parent ownership survive a real asynchronous YAML save/load. |
| `copied-world-lifecycle` | Two native copies retain template blocks and chest contents, receive distinct UUIDs, apply game rules, remain independent, and unload and delete temporary data. Includes negative chunk coordinates. |
| `native-gameplay-mechanics` | Real registered event relay honors cancellation and drives a remote trigger/function graph; native chunk tickets, finite living-mob spawn/health/damage modifiers, entity tracking, timed redstone pulses, and disposal cleanup execute. |
| `native-tile-structure` | The bundled NBT implementation captures a real chest, persists it in a structure file, and restores its inventory and delimiter-bearing name at another position. |
| `native-branching-generation` | Real room metadata, connectors, functions, and schematics save and reload; the actual layout worker creates two connected rooms; Paper generates both floors and executes the restored room mechanic. |
| `native-open-warm-pool` | Preloading coalesces requests, publishes an initialized world, respects reservation ownership/capacity, unloads the idle run, replenishes exactly one warm world, and releases the replacement. |
| `prepare-live-world-shutdown` | Leaves a real initialized run active for the script's graceful Paper shutdown. Records its actual native directory for verification in the next JVM. |
| `cold-restart-persistence-and-cleanup` | A second server process reloads native items, keys, graphs, and branching rooms with stable identity, while the previous run's temporary files and runtime registrations are gone. |

Cases advance through futures and server-scheduled predicates; server callbacks do not use
`Thread.sleep` or `Future.get` for test waits. A headless server has no players holding chunks open,
so the native mechanics case explicitly keeps its chunks active with the real chunk mechanic.

Assertions, timeouts, missing reports, skipped cases, process shutdown failures, and logged
Dungeons runtime/lifecycle failures produce a nonzero Maven result. A completion marker alone
is insufficient: the script also validates each JUnit report and checks both process logs.

## Running locally

Use JDK 25 with `JAVA_HOME` set. The runner also requires Bash, curl, sha256sum, jar, javac,
and Python 3. It downloads and verifies the exact Paper runtime checksum from HauntedPlatform.
No Minecraft account, database, Docker daemon, or running development server is needed.

```bash
./mvnw -B -ntp clean -Pplatform-acceptance verify
bash scripts/verify-artifact.sh
```

To retain the disposable server and reports:

```bash
DUNGEONS_ACCEPTANCE_WORK_DIRECTORY="$PWD/target/paper-acceptance" \
  ./mvnw -B -ntp clean -Pplatform-acceptance verify
```

The work directory must be fresh. `clean` removes the previous directory in this example.
The default is a temporary directory, removed when the runner exits. Set
`DUNGEONS_ACCEPTANCE_KEEP_WORK_DIRECTORY=true` to retain that temporary directory instead.
Explicit work directories are always retained, including on failure.

Reports and diagnostics inside the retained work directory:

- `paper/paper.log`: first boot, migration, and native gameplay cases;
- `paper/paper-restart.log`: second boot and restart assertions;
- `paper/plugins/DungeonsAcceptance/results-run.xml`: eight first-process cases;
- `paper/plugins/DungeonsAcceptance/results-restart.xml`: cold-restart case.

Unit reports and JaCoCo remain in the normal Maven locations. The forked Paper process is
not instrumented by the unit-test JaCoCo agent; acceptance results do not increase those
coverage percentages. The clean combined verification currently passes 269 unit tests and
all nine acceptance cases.

## CI, release, and packaging

The existing required `platform-acceptance` CI job runs the expanded suite. Its success is
required by `ci-required`; ordinary unit tests remain separate. CI uploads both server logs
and both JUnit reports, even when the suite fails. The release workflow runs the same profile
before Maven publication and retains those diagnostics on failure.

The profile builds a compilation classpath and compiles `src/acceptance/java` into a separate
plugin jar in the disposable server. It does not add sources to the production compilation or
publish another Maven artifact. `scripts/verify-artifact.sh` rejects acceptance, JUnit, and
Mockito classes in the distributable.

The suite exposed two production problems: modern dimension templates were copied into a
legacy server-root folder that Paper did not load, and shutdown attempted to submit work
through a disabled plugin. Copies now use the appropriate storage layout and remove native
UUID metadata; shutdown performs unload/cleanup inline while preserving files on unload
refusal. Legacy `level.dat` templates retain Paper's legacy import path. Paper describes the
modern storage layout in its [26.1 release notes](https://papermc.io/news/26-1/).

## Practical limits

These are real server integration tests, not connected-player end-to-end tests. They do not
exercise Minecraft login/proxy forwarding, player packets, interactive menus, real-client
respawn timing, or a complete multiplayer dungeon playthrough. Player entry/exit and gameplay
decisions remain protected by the unit contracts; network and interactive behavior still need
a staging smoke test or a separately maintained client harness.

The suite validates the pinned Paper build in isolation. It does not certify every production
plugin combination, alternate forks, other Minecraft versions, sustained load, or crash and
power-loss recovery. Its fixtures are disposable: never install the consumer plugin on a live
server or point the runner at a production server directory.
