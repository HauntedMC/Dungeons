# Testing and Quality

Testing in this project is designed to catch regressions in dungeon lifecycle behavior while keeping contributor workflow practical.

## Core contract suite

The [core-system contracts and coverage map](CORE_TEST_CONTRACTS.md) describe the behavior
protected by the suite and the bugs exposed while implementing it. The suite contains 113
tests: 44 baseline tests, 39 initial behavioral scenarios, and 30 coverage-audit scenarios.
Each new scenario was added in a separate commit. The coverage map records measured before/after
coverage and prioritizes the remaining untested gameplay paths.
Several scenarios cover tables of inputs or an entire state transition rather than one
assertion or helper method.

Tests run without downloading or starting Paper. Mockito is managed by HauntedPlatform and
loaded as a test-only Java agent alongside JaCoCo, supporting Java 25 without dynamic agent
attachment. Real configuration graphs, files, registries, dungeon/run objects, and mechanics
are exercised; server interfaces and collaborators are substituted at explicit boundaries.
Scheduler tests capture tasks and run them deterministically rather than sleeping.

Tests touching Bukkit or RuntimeContext use a scoped fixture and run sequentially because
these APIs hold process-wide state. A shuffled-order check helps catch leaked state:

```bash
./mvnw -B -ntp clean verify \
  '-Djunit.jupiter.testclass.order.default=org.junit.jupiter.api.ClassOrderer$Random' \
  '-Djunit.jupiter.testmethod.order.default=org.junit.jupiter.api.MethodOrderer$Random' \
  -Djunit.jupiter.execution.order.random.seed=20261106
```

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

The test downloads the HauntedPlatform-pinned Paper runtime and checks plugin startup and `/dungeon help`. It also migrates legacy generator files, loads a branching dungeon, and verifies that branch edits and layout switches persist to the normal `config.yml`.

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

Use a clean verification run when comparing coverage; repeated targeted test runs can append
to the existing execution data. The current clean baseline is 17.9% line and 15.6% branch
coverage across the whole codebase. This is a gap-finding measure, not a claim of complete
gameplay coverage. The separate Paper acceptance process does not contribute to these unit
coverage numbers.

## CI

CI validates Checkstyle, tests, and coverage report generation on pull requests and `main` updates.
