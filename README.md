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

The mandatory database integration is still in progress; gameplay persistence currently remains file-based.

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
