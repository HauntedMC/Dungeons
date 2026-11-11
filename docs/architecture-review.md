# Architecture review

Reviewed 2026-11-11. The review covered the production package inventory and traced
bootstrap, persistence, external identity/language/privacy, public API publication,
content repository, mechanic execution and player recovery ownership. This is a
structural review with targeted behavioral verification, not a certification of
every branch in every system.

## Findings and changes

| Priority | Finding | Result |
| --- | --- | --- |
| High | Player sessions, recovery journals and per-dungeon progression selected the current global store during construction, even when their caller already owned a runtime. Offline staff recovery accepted a runtime but ignored it for journal selection. | Sessions and session registries now receive their owning runtime; session sweeping uses that same owner without a second runtime parameter; journals and progression stores receive their `StateStore`. Bootstrap, dungeon construction, offline staff support, tests and native acceptance consumers use those explicit dependencies. |
| High | Functions and triggers obtained configuration, scheduling plugins, sessions and editor services from `RuntimeContext`, despite already having an instance owner. This made their behavior depend on unrelated global lifecycle state. | All direct `RuntimeContext` dependencies were removed from `content`. Mechanics use the existing `DungeonElement` owner accessors. Regression coverage replaces the global runtime and exercises scheduling, configuration and durable record construction. |
| Medium | `DungeonService.close()` removed the public registration but did not cancel its refresh task. Repeated `start()` calls created extra registrations/tasks; failures during startup could leave partial publication until broader application cleanup. | The service owns its timer, starts once, rejects restart after close, and closes idempotently. Partial startup immediately unpublishes the service. Cleanup attempts privacy shutdown, timer cancellation and service removal even if one step fails. |
| Medium | Branching generation and validation reporting required global plugin access solely to log diagnostics. | Generation uses its dungeon environment. Validation reporting receives a logger explicitly. |
| Low | The language event adapter received the entire mutable runtime just to access profiles. | `ProfileListener` receives `RegistryProfiles` directly. |

The public `DungeonsApi` contract, persistence keys, serialized mechanic IDs and
configuration paths retain their meanings. Internal constructor signatures changed;
code importing internal implementation classes must rebuild and supply the owner.

## Existing boundaries worth retaining

- `DungeonDatabase` owns the DataProvider scope, ORM, server lease and transactional
  boundary. Provider-specific persistence code belongs here and in its repositories;
  a second general persistence abstraction would obscure ownership.
- `StateStore`/`StateDocument` separate durability from player and dungeon operations.
  Injecting this existing boundary fixes selection without inventing parallel APIs.
- `RegistryProfiles` bounds refresh work, checks canonical identity, backs off after
  failures and discards stale asynchronous completions. Message delivery uses cached
  language state rather than waiting for a query.
- `DungeonPrivacy` treats disclosure as a separate asynchronous policy, bounds work
  and fails closed. `ServerFeaturesPrivacy` resolves the live optional capability.
  The optional API types stay behind a separate class loaded only on the supported path.
- The public service publishes immutable snapshots and gates reads on database health.
- Bootstrap explicitly orders persistence readiness, registry setup, content loading,
  ingress registration and public publication. FeatureFramework owns ingress quiescing
  before player/world cleanup and database shutdown.

## Remaining design debt

The repository is not yet uniformly independent of global state. These are concrete
follow-up boundaries, not claims that the corresponding behavior is broken:

1. Static menus, `LangUtils`, hotbar controls and shared item/world utilities still
   use `RuntimeContext`. The next migration should introduce owner-bound presentation
   contexts and thread them through menu creation. Bulk replacement would be unsafe:
   registry prototypes exist before they have an instance and static windows currently
   receive their owner at bootstrap. Detached elements must not acquire mutable services
   from whichever runtime happens to be global.
2. `DungeonCommand` receives several services but also reaches globally for repository,
   health and player-readiness checks. A command context assembled by bootstrap should
   carry these collaborators; command and menu migration should share the same owner.
3. `AccessKeyDefinition` still consults global configuration for validation policy.
   Resolve policy from the owning dungeon/runtime at the issuance and validation
   boundary. Preserve existing transactional rotation and recovery guarantees while
   doing so; this is more than a configuration getter cleanup.
4. `RepoService` combines transfer execution, admission/drain coordination, permissions,
   approvals, status rendering and filesystem installation. Its constructor also
   performs blocking recovery. Separate publication and installation workflows behind
   the existing service, with a shared bounded operation owner. Keep recovery before
   catalog discovery and retain fencing, confirmation binding and journal guarantees.
   Splitting by line count alone would hide those invariants.
5. `DungeonsRuntime` is a mutable assembly object with public setters described as
   bootstrap-only. This convention is not enforced by the type system. A later change
   can separate bootstrap assembly from read-only runtime access after optional-service
   and partial-startup semantics are made explicit. Avoid interfaces for every class.
6. Model elements also build editor controls, and some constructors call overridable
   initialization methods (visible in compiler warnings). Separating authored metadata,
   editor presentation and live activation is the longer-term boundary. Preserve
   deserialization/clone behavior and nested mechanic lifecycle contracts during that work.

## Verification

The pre-change default `verify` build passed. Final validation passed with:

```sh
./mvnw -B -ntp spotless:apply clean verify -Pintegration-tests,platform-acceptance
bash scripts/verify-artifact.sh
```

- 547 unit tests, zero failures or skips.
- 22 real MySQL integration tests, zero failures or skips.
- 15 native Paper gameplay cases and two cold-restart cases passed.
- Native startup rejected unavailable persistence and an invalid schema as expected.
- Formatting and Checkstyle passed; packaged jar audit passed.

The regression additions exercise runtime ownership after global bridge replacement,
direct/repeated public-service cleanup, repeated startup and partial startup failure.
The native harness compiled against the updated internal constructors and exercised
player recovery through the real DataProvider/DataRegistry/Paper stack.
