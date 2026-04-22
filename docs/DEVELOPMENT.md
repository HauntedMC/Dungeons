# Development Notes

This page is for contributors who want a fast, reliable local workflow for Dungeons.

## Local Setup

```bash
mvn -q -DskipTests compile
```

Useful commands during development:

```bash
mvn -q test
mvn -B -DskipTests checkstyle:check
mvn -q test jacoco:report
mvn -B verify
mvn -B package
```

## Recommended Workflow

1. Create a branch for one focused change.
2. Implement the change with tests in the same pass.
3. Run local validation (`test` and Checkstyle at minimum).
4. Update docs when command behavior or configuration expectations change.
5. Open a PR with context, impact, and migration notes (if any).

## Engineering Guidelines

- Keep composition and ordering logic in the bootstrap package.
- Keep runtime state changes in runtime services, not scattered across commands/listeners.
- Prefer explicit validation and actionable operator-facing messages.
- Treat queue/team/dungeon lifecycle paths as high-risk regression areas.
- Keep configurable behavior in config and message files instead of hardcoded constants.

## Before You Open a PR

- Build succeeds locally.
- Relevant tests pass.
- New behavior is covered by tests.
- Checkstyle passes.
- Config or operational impact is documented.
