# Dungeons Docs

This folder is the practical guide for running, maintaining, and contributing to Dungeons.

## Start Here

If you run the plugin:

- [Configuration](CONFIGURATION.md): global config, dungeon defaults, and safe rollout flow.
- [Architecture](ARCHITECTURE.md): startup sequence, runtime graph, and lifecycle responsibilities.

If you contribute code:

- [Development](DEVELOPMENT.md): local setup and day-to-day workflow.
- [Testing](TESTING.md): test strategy and local validation commands.
- [Paper acceptance](ACCEPTANCE_TESTS.md): real-server contracts, restart checks, reports, and limits.
- [Core contracts](CORE_TEST_CONTRACTS.md): executable guarantees, coverage measurements, and boundaries.
- [Mechanic coverage](MECHANIC_TEST_COVERAGE.md): execution contracts for every registered mechanic.
- [Contributing Guide](../CONTRIBUTING.md): pull request expectations.

## Releases

From clean `main`, run `./tools/release/update-version patch --pr`. The shared tooling opens a reviewed PR; CI verifies it. After merge, the release workflow publishes the Maven package, confirms that it resolves, then creates the tag and downloadable jar with a checksum. See [release tooling](../tools/release/README.md).
