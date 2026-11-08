# Dungeons Docs

This folder is the practical guide for running, maintaining, and contributing to Dungeons.

## Start Here

If you run the plugin:

- [Staff operations](STAFF_OPERATIONS.md): dashboard, permissions, maintenance, player support, and recovery.
- [Clean installs](CLEAN_INSTALLS.md): supported formats and current command/permission contracts.
- [Configuration](CONFIGURATION.md): global config, dungeon defaults, and safe rollout flow.
- [Architecture](ARCHITECTURE.md): startup sequence, runtime graph, and lifecycle responsibilities.

If you contribute code:

- [Development](DEVELOPMENT.md): local setup and day-to-day workflow.
- [Refactoring worklist](REFACTORING.md): completed extractions, full source inventory, and remaining review.
- [System reliability](SYSTEM_RELIABILITY.md): persistence, runtime boundaries, and recovery.
- [Staff validation](STAFF_VALIDATION.md): regression fixes, class coverage, and native acceptance results.
- [Contributing Guide](../CONTRIBUTING.md): pull request expectations.

## Releases

From clean `main`, run `./tools/release/update-version patch --pr`. The shared tooling opens a reviewed PR; CI verifies it. After merge, the release workflow publishes the Maven package, confirms that it resolves, then creates the tag and downloadable jar with a checksum. See [release tooling](../tools/release/README.md).
