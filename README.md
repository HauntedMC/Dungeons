# Dungeons

[![CI](https://github.com/HauntedMC/Dungeons/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/HauntedMC/Dungeons/actions/workflows/ci.yml)
[![Latest Release](https://img.shields.io/github/v/release/HauntedMC/Dungeons?sort=semver)](https://github.com/HauntedMC/Dungeons/releases/latest)
[![Java 25](https://img.shields.io/badge/Java-25-007396?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![License](https://img.shields.io/github/license/HauntedMC/Dungeons)](LICENSE)

Dungeons is a Paper plugin for creating, editing, and running configurable dungeon content with queueing, teams, triggers, functions, difficulty scaling, and loot table rewards.

## Quick Start

1. Place `Dungeons-<version>.jar` in your server `plugins/` directory.
2. Start the server once to generate runtime files under `plugins/Dungeons/`.
3. Configure global settings in `plugins/Dungeons/config.yml`.
4. Create or edit dungeon content with `/dungeon` editor commands.
5. Start a run with `/dungeon play <dungeon> [difficulty]`.

Database integration is still in progress; gameplay persistence currently remains file-based.

## Database configuration

The optional database foundation uses the public DataProvider ORM API. No SQL scripts need to be
installed or executed. Missing consumer settings are added to existing `config.yml` files;
custom connection names are preserved.

Dungeons `config.yml`:

```yaml
database:
  enabled: false
  connection-id: player_data_rw
  shutdown-timeout-seconds: 10
runtime:
  server-id: ''
```

DataProvider `config.yml` (authoritative ORM policy for consumers):

```yaml
orm:
  schema_mode: update
```

DataProvider 3.5.2 ignores the legacy schema-mode argument on consumer factory calls. Dungeons uses
`createConfiguredOrmContext` and deliberately has no local mode override. Remove any experimental
`database.orm.schema-mode` or `database.schema-mode` setting from Dungeons; startup rejects these
instead of pretending they control the provider.

To initialize the foundation, enable DataProvider and DataRegistry, set `database.enabled: true`,
and select a named MySQL connection that points to the database containing DataRegistry's
`player_entity`. Credentials, pool sizes, timeouts and ORM policy belong to DataProvider.
`update` creates/updates the mapped schema, `validate` checks mapped types without DDL, and `none`
skips ORM DDL. Use these modes for the shared database; **do not use destructive `create` mode**.
The ORM mapping includes a minimal read-only canonical-player reference for foreign keys, so schema
creation mode is inappropriate for this shared database. Update/validate preserve canonical player
rows and the complete existing player table definition, covered by MySQL integration tests.

Startup also checks required tables, column types/widths/nullability, InnoDB engines, primary keys,
declared indexes, unique keys, and foreign-key delete/update rules without issuing DDL. Schema errors prevent readiness. A database
initialization failure or lost lease disables the enabled consumer on the server thread, drains
admitted work, closes ORM, and unregisters its provider. Shutdown waits at most the configured time;
remaining cleanup continues on its worker. `/dungeon server status` reports cached database state
without querying the database. One-connection pools are supported.

Backend identity comes from DataRegistry, or `runtime.server-id` when it publishes none. If both
are set they must match. Restart after changing database settings. Enabling this foundation
initializes ORM and the server lease; it does not import or replace file-based gameplay data.
Gameplay progression, rewards and staff operations still use files; end-to-end database gameplay
persistence is not complete. Native Paper acceptance currently covers disabled-foundation startup;
enabled database behavior is covered by public-API lifecycle tests and real MySQL/DataProvider ORM tests.

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
