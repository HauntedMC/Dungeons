# Testing and Quality

Testing in this project is designed to catch regressions in dungeon lifecycle behavior while keeping contributor workflow practical.

## Test Structure

Tests are organized under `src/test/java` and should mirror production package boundaries:

- runtime tests for queue, team, and lifecycle decisions;
- domain/model tests for deterministic parsing and state rules;
- command/listener tests for visible behavior and branching outcomes.

## Local Commands

Run tests:

```bash
./mvnw -B -ntp test
```

Run full quality checks:

```bash
./mvnw -B -ntp verify
```

Run lint checks:

```bash
./mvnw -B -ntp -DskipTests verify
```

Generate local coverage report:

```bash
./mvnw -B -ntp verify
```

Run the real Paper boot and command smoke test for packaging or runtime changes:

```bash
./mvnw -B -ntp -Pplatform-acceptance verify
```

The test downloads the HauntedPlatform-pinned Paper runtime and checks that the plugin starts and responds to `/dungeon help`.

## What to Test

When changing behavior, add or update tests close to that behavior:

- queue/team changes: membership transitions, leader-only constraints, and edge cases;
- dungeon loading changes: config handling and fallback behavior;
- utility changes: parsing, range checks, and deterministic helper outputs;
- command changes: permission checks and user-visible outcomes.

Focus on behavior that operators and players observe directly.

## Coverage Reports

After `./mvnw -B -ntp verify`:

- HTML report: `target/site/jacoco/index.html`
- XML report: `target/site/jacoco/jacoco.xml`
- CSV summary: `target/site/jacoco/jacoco.csv`

## CI

CI validates Checkstyle, tests, and coverage report generation on pull requests and `main` updates.
