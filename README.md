# Dungeons

[![CI](https://github.com/HauntedMC/Dungeons/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/HauntedMC/Dungeons/actions/workflows/ci.yml)
[![Latest Release](https://img.shields.io/github/v/release/HauntedMC/Dungeons?sort=semver)](https://github.com/HauntedMC/Dungeons/releases/latest)
[![Java 25](https://img.shields.io/badge/Java-25-007396?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![License](https://img.shields.io/github/license/HauntedMC/Dungeons)](LICENSE)

Dungeons is a Paper plugin for creating, editing, and running configurable dungeon content with queueing, teams, triggers, functions, difficulty scaling, and loot table rewards.

## Quick Start

1. Install DataProvider, DataRegistry and `Dungeons-<version>.jar` in `plugins/`.
2. Configure DataProvider's MySQL connection and DataRegistry, then set Dungeons' connection and stable server identity below.
3. Start the server. Dungeons enables only after the database, schema and exclusive server lease are ready.
4. Create or edit dungeon content with `/dungeon` editor commands.
5. Start a run with `/dungeon play <dungeon> [difficulty]`.

## Database configuration

Database persistence is mandatory. DataProvider owns credentials, pools, timeouts and ORM policy;
DataRegistry owns canonical player identities. Select a named MySQL connection that contains
DataRegistry's `player_entity` table and grants Dungeons access. No SQL files need to be installed.
Missing consumer settings are added to existing configurations; custom connection names are preserved.

Dungeons `config.yml`:

```yaml
database:
  connection-id: player_data_rw
  startup-timeout-seconds: 30
  shutdown-timeout-seconds: 10
  operation-timeout-ms: 1000
runtime:
  server-id: ''
```

DataProvider `config.yml` (authoritative ORM policy for consumers):

```yaml
orm:
  schema_mode: update
```

Dungeons uses `createConfiguredOrmContext`. Configure ORM policy in DataProvider; local
`database.orm.schema-mode` and `database.schema-mode` overrides are unsupported.
`update` creates/updates the mapped schema, `validate` checks it without DDL, and `none` skips ORM DDL.
Dungeons always independently verifies required tables, column types/widths/nullability, InnoDB
engines, primary keys, indexes and foreign-key rules. An absent or incompatible schema prevents boot.
**Do not use destructive `create` mode** on the shared database: its mappings include a minimal
canonical-player reference. Update/validate preserve canonical rows and the existing player table.

DataRegistry supplies backend identity. Set `runtime.server-id` if DataRegistry publishes none;
if both are set, they must match. Give every concurrent backend a distinct, stable identity.
Snapshots belong to this server identity and the dungeon's persistent UUID. Restart after changing
connection or identity settings. DataRegistry must provision a player's identity before dungeon gameplay;
Dungeons never invents canonical player rows. Player writes also verify the numeric ID and UUID
on the selected database connection before changing state. First-time backend joins remain allowed so
DataRegistry can complete its join initialization; commands that require a player session wait for the active identity.

Progression, completion, cooldowns, save points, rewards, recovery journals, unique-key ledgers,
staff state and audit events use the database. Legacy local gameplay files are ignored; there is
no import, migration or file fallback. Content definitions, world templates and content backups
remain files. Back up the database as well as content. Vanilla player saves remain necessary to
checkpoint inventory transactions against their database recovery receipts.

SQL executes on provider workers. Async login prefetch warms immutable player snapshots; dungeon
progress is loaded on demand. Owner-thread inventory operations wait for a bounded durability
barrier before applying effects. Revision checks and checksums reject stale or corrupt snapshots;
key settlement and its player recovery receipt commit together. A timeout closes admission even
when the commit outcome is ambiguous: cleanup drains admitted work, and the next boot reloads the
durable database state. No write is silently redirected to local storage.

Unavailable persistence or a lost server lease disables Dungeons. Shutdown waits at most the
configured deadline; cleanup continues on its worker. `/dungeon server status` reads cached health
without issuing SQL. One-connection pools are supported. Native Paper acceptance runs the real
DataProvider/DataRegistry stack, rejects invalid connection/schema boots, and verifies healthy boot
and cold-restart recovery; MySQL integration tests cover schema ownership and transactional failures.

## HauntedMC integration

Dungeons bundles FeatureFramework 2.2.4 and the HauntedMC Theme adapter 1.2.5 (palette 1.2.3),
matching ServerFeatures and ProxyFeatures. These are libraries, so no additional server plugin is
needed. The shared classes retain their public package identities; DataProvider/DataRegistry APIs
and Adventure stay provided by the server.

Messages now live in `lang/messages.yml`. On the first boot, an existing root `messages.yml` is
imported without deleting it: unchanged stock values adopt the HauntedMC palette, customized
values survive. Subsequent edits belong in `lang/messages.yml`. Missing bundled keys are merged;
custom keys and translations are retained. Restart or `/dungeon server reload` reloads messages.

Templates accept legacy formatting, MiniMessage and `<HauntedMC:Brand>`, `<HauntedMC:Success>`,
`<HauntedMC:Warning>`, `<HauntedMC:Error>` and the other shared theme items. Placeholder strings are
literal components, so a player name or staff reason cannot inject colors or click actions. Explicit
component placeholders preserve intentionally supplied interactive actions. Legacy UI conversion
does not parse the resulting text a second time. Chat, action bars, kick notices and command output
share the theme; authored dungeon narrative retains its configured formatting.

Language preferences are read asynchronously through DataRegistry's identity-bound API, cached
per connected player and refreshed after 30 seconds. Reconnect and shutdown discard pending
results. Overrides in `lang/messages_EN.yml` and `lang/messages_NL.yml` use the player's resolved
network preference, falling back to client locale and then the bundled Dutch defaults when a key
has no translation. Message delivery never waits for a language query or performs SQL.

DataProvider connections belong to the `dungeons.gameplay` scope, which closes only Dungeons'
resources after admitted writes drain and ORM closes. Online identity lookups use DataRegistry's
explicit active cache, with persisted asynchronous lookup for offline identities. Canonical UUIDs
are still checked in the write transaction. FeatureFramework owns ingress listener/service cleanup,
so stale callbacks and public services cannot survive shutdown.

Peer Paper plugins can obtain `nl.hauntedmc.dungeons.api.DungeonsApi` from Bukkit's services manager.
Its server identity, readiness and immutable catalog snapshots are safe to read from any thread and
perform no I/O. Catalog snapshots refresh once per second; unavailable persistence yields an empty
catalog. Mutable dungeon models, worlds and player records are never exposed through this service.

The service also provides asynchronous `canViewPlayerData(viewer, target)` disclosure decisions
and `publicPlayerIds(candidates)` batches (at most 512 canonical IDs). These query DataRegistry's
current privacy settings without caching grants. Only `PUBLIC` entries belong in public rankings;
both `FRIENDS` and `PRIVATE` entries are excluded before consumers assign ranks or paginate. A
player may view their own historical data. `FRIENDS` access resolves the live ServerFeatures
`PlayerPrivacyApi` capability, which delegates friendship decisions to ProxyFeatures. ServerFeatures
5.4.6 is an optional provided API; it is not bundled or required for ordinary dungeon gameplay.
Missing/disabled privacy support, absent friendship authority, failed/incomplete replies, timeouts,
overload and shutdown never grant disclosure. At most 128 disclosure operations are admitted, each
with a five-second deadline; shutdown terminates outstanding results.

There are currently no active public dungeon statistics or ranking commands, and the reserved
statistics tables are not populated by gameplay. The API exposes the policy boundary for consumers;
it does not claim to supply a working leaderboard. Existing self/gameplay state and capability-checked
staff inspection/recovery retain their separate authorization rules. Privacy controls disclosure,
not durability of progression or recovery state.

## Administration

Use `/dungeon help` to discover commands; staff inspection and confirmations appear in chat.
There is no admin dashboard inventory.

- `/dungeon edit open <dungeon>` opens the world editor; an optional reason may follow the name.
- `/dungeon edit save` saves changes, and `/dungeon edit close` leaves the editor.
- `/dungeon help map` covers creation, imports, validation, settings, keys, backups and archives.
- `/dungeon help instance`, `/dungeon help player`, `/dungeon help queue` and `/dungeon help server` cover runtime administration.
- `/dungeon help loot` covers loot tables, including their content editor.

Legacy world templates containing `level.dat` and `DIM1`/`DIM-1` terrain are copied intact
for Paper conversion. Current dimension templates keep their flat terrain layout.

## Requirements

- Java 25
- Paper 26.x
- DataProvider 3.5.2 and DataRegistry 1.19.2, configured with MySQL

## Build From Source

Use Java 25. HauntedPlatform is resolved from GitHub Packages; set `PACKAGES_USER` and `PACKAGES_TOKEN` (with `read:packages`) for a fresh local Maven cache. The committed `.mvn/settings.xml` reads these variables.

```bash
./mvnw -B -ntp verify
```

Output jar: `target/Dungeons-<version>.jar`

## Release workflow

From clean `main`, run `./tools/release/update-version patch --pr` to open a reviewed version PR. CI tests the PR; after merge, GitHub Actions publishes the Maven package, verifies that it resolves, and creates the tag and downloadable release jar with a SHA-256 checksum. See [release tooling](tools/release/README.md).

See [Contributing](CONTRIBUTING.md) for development guidelines.

## Community

- [Support](SUPPORT.md)
- [Security Policy](SECURITY.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)
