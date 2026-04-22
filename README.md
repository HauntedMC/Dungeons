# Dungeons

[![CI Lint](https://github.com/HauntedMC/Dungeons/actions/workflows/ci-lint.yml/badge.svg?branch=main)](https://github.com/HauntedMC/Dungeons/actions/workflows/ci-lint.yml)
[![CI Tests and Coverage](https://github.com/HauntedMC/Dungeons/actions/workflows/ci-tests-and-coverage.yml/badge.svg?branch=main)](https://github.com/HauntedMC/Dungeons/actions/workflows/ci-tests-and-coverage.yml)
[![Latest Release](https://img.shields.io/github/v/release/HauntedMC/Dungeons?sort=semver)](https://github.com/HauntedMC/Dungeons/releases/latest)
[![Java 21](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![License](https://img.shields.io/github/license/HauntedMC/Dungeons)](LICENSE)

Dungeons is a Paper plugin for creating, editing, and running configurable dungeon content with queueing, teams, triggers, functions, difficulty scaling, and loot table rewards.

## Quick Start

1. Place `Dungeons.jar` in your server `plugins/` directory.
2. Start the server once to generate runtime files under `plugins/Dungeons/`.
3. Configure global settings in `plugins/Dungeons/config.yml`.
4. Create or edit dungeon content with `/dungeon` editor commands.
5. Start a run with `/dungeon play <dungeon>[:difficulty]`.

## Requirements

- Java 21
- Paper 1.21.x

## Build From Source

```bash
mvn -B package
```

Output jar: `target/Dungeons.jar`

## Version Bump Workflow

Use the helper script to bump semver, commit, and tag:

```bash
scripts/bump-version.sh patch
scripts/bump-version.sh minor --push
```

Options:

- `major|minor|patch`: required bump type
- `--push`: push branch + tag after creating them
- `--remote <name>`: push/check against a remote (default: `origin`)

## Learn More

- [Configuration Guide](docs/CONFIGURATION.md)
- [Documentation Index](docs/README.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Development Notes](docs/DEVELOPMENT.md)
- [Testing and Quality](docs/TESTING.md)
- [Contributing](CONTRIBUTING.md)

## Community

- [Support](SUPPORT.md)
- [Security Policy](SECURITY.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)
