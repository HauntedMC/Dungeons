# Source inventory

Run from the repository root using the project's JDK:

```bash
java tools/architecture/SourceInventory.java > docs/REFACTORING_INVENTORY.tsv
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
