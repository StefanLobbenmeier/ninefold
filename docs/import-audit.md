# SudokuPad omission audit

This audit uses the shared importer and its actual `Puzzle.importOmissions`, not guesses based on raw JSON keys. The 24 public links in `import-audit-links.txt` were selected from the app's configured catalog, https://stefanlobbenmeier.github.io/sudoku-index/index.json : the ten newest and two oldest available entries from each of Lucian's Place and Cracking the Cryptic, deduplicated by puzzle URL. This bounded selection spans authors, dates, SCL/f-puzzles encodings and graphical/semantic variants; it is not representative of every SudokuPad feature.

## Reproduce (explicit live network tool)

From the repository root:

```sh
export JAVA_HOME=/Users/stefan/.sdkman/candidates/java/current
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :shared:importAudit \
  -PauditLinks="$PWD/docs/import-audit-links.txt" \
  -PauditOutput="$PWD/docs/import-audit-results.md"
./gradlew :shared:jvmTest
```

The task runs sequentially with a 30-second per-link timeout, reports fetch/parse failures individually and continues. It retains only links and omission reports. No published puzzle payloads are committed or used as test fixtures. `importAudit` is separate from test tasks and never runs implicitly with tests. Network responses may change; the generated timestamp marks the actual run. Repeated indices in the document are normalized to `[]` for readability; runtime report objects retain full indices.

## Findings and interpretation

Final baseline run: **24/24 imports succeeded, zero fetch/parse failures, 1,571 report entries** including repeated paths and conservative prose-review notices. **182 JVM tests passed**, zero failures/errors. Implementation commit: `53d46e7`.

The generated results document contains each URL, report counts and all distinct path patterns. Meaningful candidate fixes are deduplicated in `import-fix-queue.md`:

- Cage `unique` semantics are ignored; existing validation assumes killer-style no-repeat cages.
- Authored cell `given`, color and pencil mark fields are ignored; non-given values need review before being treated as fixed clues.
- Rotation, font size, border/stroke aliases, line width/caps/joins/fill are ignored. One artwork-heavy import drops hundreds of short line geometries.
- Explicit regions are not read. Standard boxes are already enforced; only inspection can establish whether this represents loss of a custom region rule.
- Many styled lines have no assigned validation rule. Original instruction text remains in `Puzzle.rules`, used by the existing rule displays, and graphical import is reported separately from validation support. Further semantic work must extend special-rule checking without discarding the original prose.
- Provenance (`metadata.source`, `SudokuMakerURL`) and exporter settings (`disabledlogic`, decorative `class`) are also reported. These are lower-priority informational findings, not demonstrated solving errors.

The report is deliberately conservative: unknown populated properties (including explicit false and zero) are visible; empty/null extensions are ignored. A `validation` entry for custom prose requests manual review and does not claim that every such rule is unsupported. It is a schema/geometry loss report, not proof of visual equivalence or a natural-language rule interpreter. Some malformed source data still causes import failure; those failures belong in the live results rather than being treated as successful zero-omission imports.

## Validation and run history

Synthetic common tests cover unknown and empty extensions, nested styling, dropped overlays/coordinates, unsupported trigger effects, preserved rules, validation-only lines, explicit false flags and discarded solutions. Existing importer callers still return `Puzzle`, with a default-empty serializable report added to that model. The review UI groups repeated findings rather than rendering thousands of rows.

During tool development an initial audit invocation failed due to Gradle configuration-cache restrictions on execution-time `project` access. The task was corrected to use configuration-time providers; subsequent live runs and JVM tests succeeded. The final generated results below are the authoritative run, not this development failure.
