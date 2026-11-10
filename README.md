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
