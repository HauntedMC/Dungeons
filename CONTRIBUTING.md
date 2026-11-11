# Contributing

Thanks for taking the time to contribute.

## Before You Start

- Use Java 25 and Maven.
- Make sure you can run a local compile and test pass.
- If your change affects runtime behavior, test it in a Paper environment.

## Setup

```bash
git clone https://github.com/HauntedMC/Dungeons.git
cd Dungeons
./mvnw -B -ntp -DskipTests compile
```

## Contribution Workflow

1. Create a branch from `main`.
2. Keep the change focused on one clear problem.
3. Add or update tests with the code change.
4. Run local checks before opening a PR.
5. Update the README when operator behavior or configuration expectations change.

## Local Validation

Run the complete local unit, formatting, Checkstyle and packaging checks:

```bash
./mvnw -B -ntp clean verify
bash scripts/verify-artifact.sh
```

Java sources use Google Java Format 1.33.0 in AOSP mode (four spaces), including
unit tests, Paper acceptance fixtures and the source inventory tool. Apply the
pinned formatter before validation:

```bash
./mvnw -B -ntp spotless:apply
```

`verify` checks formatting; it does not rewrite source files. Avoid generated files
under `target/` when editing or reviewing changes. Use `git diff -w` to review
behavioral edits separately from formatting changes.

For persistence or runtime changes, run the corresponding CI checks with Docker
available for the MySQL fixtures:

```bash
./mvnw -B -ntp -Pintegration-tests verify
./mvnw -B -ntp -Pplatform-acceptance verify
```

The integration profile exercises real MySQL schema and transaction behavior.
The acceptance profile starts Paper with the real DataProvider/DataRegistry stack
and checks boot, gameplay and recovery across restarts. Acceptance also requires
`JAVA_HOME` to point to JDK 25. Neither profile is included by plain `verify`.

Use [the source inventory](tools/architecture/README.md) to locate large classes
and methods during architecture reviews. Size signals identify review priorities;
they are not a reason to split cohesive code mechanically.

## Pull Request Expectations

- Use a clear title and summary.
- Explain what changed and why.
- Call out configuration or migration impact.
- Link related issues where relevant.
- Keep commits readable and review-friendly.

## Coding Principles

- Prefer clear, explicit code over clever shortcuts.
- Keep bootstrap, runtime, and domain boundaries clean.
- Handle malformed runtime state defensively.
- Avoid broad side effects in event handlers.
- Log failures in a way operators can act on.

## Security

Do not report vulnerabilities in public issues.
Follow [SECURITY.md](SECURITY.md) for private reporting.
