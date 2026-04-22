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
mvn -q test
```

Run full quality checks:

```bash
mvn -B verify
```

Run lint checks:

```bash
mvn -B -DskipTests checkstyle:check
```

Generate local coverage report:

```bash
mvn -q test jacoco:report
```

## What to Test

When changing behavior, add or update tests close to that behavior:

- queue/team changes: membership transitions, leader-only constraints, and edge cases;
- dungeon loading changes: config handling and fallback behavior;
- utility changes: parsing, range checks, and deterministic helper outputs;
- command changes: permission checks and user-visible outcomes.

Focus on behavior that operators and players observe directly.

## Coverage Reports

After `jacoco:report`:

- HTML report: `target/site/jacoco/index.html`
- XML report: `target/site/jacoco/jacoco.xml`
- CSV summary: `target/site/jacoco/jacoco.csv`

## CI

CI validates Checkstyle, tests, and coverage report generation on pull requests and `main` updates.
