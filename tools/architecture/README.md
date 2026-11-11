# Source inventory

Run from the repository root using the project's JDK:

```bash
java tools/architecture/SourceInventory.java
```

The tool uses the JDK parser, so it understands nested classes, anonymous callbacks,
records and modern switch syntax without requiring the Paper classpath. It includes
package documentation files so every production package appears in the inventory.
Method sizes include nested callbacks within that method. Paths and row order are
deterministic; the output has no timestamps or machine-specific absolute paths.

The 500-line class and 80-line method signals are review priorities, not build
failures or architecture rules. Small files can still mix responsibilities; large
files can be cohesive. This is structural coverage of every source file, not a
substitute for reviewing behavior, ownership, dependencies and test boundaries.

## Package ownership

| Package | Responsibility |
| --- | --- |
| `bootstrap` | Builds the runtime graph and orders startup, quiescing and cleanup. |
| `api` | Public service contract for peer plugins; does not expose mutable engine objects. |
| `integration` | External identity, language preferences, privacy capabilities and the public service adapter. |
| `config` | YAML I/O, serialization, rule fallback, typed configuration views and default synchronization. |
| `localization` | Message resource loading, formatting, language selection and delivery. |
| `model.dungeon`, `model.element`, `model.cooldown` | Dungeon definitions, mechanic abstractions and their value types. |
| `content` | Bundled dungeon definitions, functions, triggers, conditions and variable mechanics. |
| `generation` | Layout generation, room definitions/transforms and structure data. |
| `runtime.dungeon` | Definition catalog, durable identity, access keys and instance-world cleanup. |
| `runtime.instance` | Live instances, admission, save/disposal coordination, holograms and dungeon map rendering. |
| `runtime.instance.play`, `.edit`, `.listener` | Concrete live runs, branching editors and instance-scoped Bukkit rules. |
| `runtime.player`, `.queue`, `.team`, `.staff` | Player recovery/session state, launch orchestration, team transitions and staff operations. |
| `persistence` | Database lifecycle, schema mappings, transactional repositories and snapshot/cache boundaries. |
| `gui` | Inventory and hotbar presentation, editor controls and their callbacks. |
| `command`, `listener` | Command and plugin-wide Bukkit ingress adapters. Instance-scoped listeners live with instances. |
| `registry` | Discovery and construction metadata for authored content types. |
| `world.generator` | Paper chunk-generator adapters. |
| `util` | Shared low-level codecs, math, item/entity/world operations and metadata helpers. Domain-owned services belong with their owner. |

The package tree describes ownership, not a strict layered dependency graph. In
particular, dungeon definitions still coordinate preparation and editors, and
mechanics build editor controls. `model` is therefore not an isolated pure domain
module. Splitting those contracts into interfaces is useful only when it removes
an actual dependency or provides a separate implementation; duplicating every
class into an interface and implementation would obscure the current lifecycle.

Instance types belong together under `runtime.instance`: `DungeonInstance` owns
the copied world and disposal state; `PlayableInstance` and `EditableInstance`
extend that lifecycle. Keeping their concrete implementations and listeners in
that subtree makes runtime ownership visible without mixing them with authored
content definitions. Tests follow the production package when they need package
access.

`GuiService` owns its plugin reference and window namespace map for one runtime.
It creates windows, replaces registrations by identity and unregisters its windows
on close. Bootstrap registers that service with the runtime resource owner. GUI
callbacks use `Consumer<EventType>` rather than separate equivalent callback
interfaces. Hologram state also belongs to an instance and receives its plugin
explicitly. Instance listeners obtain sessions and scheduling context from their
owning instance.

`RuntimeContext` remains a compatibility bridge for static menu, localization and
utility adapters. Do not use it in code that already has an instance, runtime or
service owner. Prefer the owning object's dependencies, so independent runtime
lifecycles cannot accidentally select one another's services.

The supported peer-plugin boundary remains `DungeonsApi`. Internal Java package
moves require rebuilding code that imported internal classes; no compatibility
wrappers are retained for those packages. Serialized mechanic IDs, configuration
keys, message resource paths and database mappings do not depend on these moves.
Use a clean build after moving packages so stale class files cannot enter the jar.
