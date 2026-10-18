# Serial SudokuPad import fix queue

Branch: `feat/report-import-omissions`
Checkout: `/Users/stefan/.t3/worktrees/sudokupad/t3-3df1bf18`
Coordinator thread: `mcp:49d6e02a-c677-43e4-8441-e40389c4bef9`

User authorized implementation, public live audits, commits, and a serial chain of ordinary top-level T3 threads sharing this exact branch/checkout. No PR or push. Never create another branch/worktree or run workers concurrently.

## Completed foundation

- Structured `Puzzle.importOmissions` with expanded JSON paths, ignored/partial/validation categories, serialized with backwards-compatible default.
- Import dialog groups report entries and no longer promises all graphics survived.
- Original rule text remains in `Puzzle.rules`, displayed by `RulesText`; special-rule validation currently checks modeled constraints only. An arbitrary prose rule is not automatically supported just because its artwork is imported.
- Synthetic common tests and opt-in JVM live audit task; downloaded published payloads are never retained.
- Implementation commit: `53d46e7` (reporting, UI, synthetic tests and live tool).
- Baseline: `docs/import-audit-results.md`; input: `docs/import-audit-links.txt`; methodology: `docs/import-audit.md`.

## Ordered work

Each item is a coherent feature investigation/fix, not one task per duplicate puzzle. Source paths below are actual report findings. Confirm source semantics before changing behavior. If redundant, document that with evidence and mark resolved without inventing implementation.

1. **Q1 — Cage uniqueness and special-rule correctness. Priority P1. Status: pending; next thread owns this.**
   Report: `$.cages[].unique`, e.g. https://sudokupad.app/peoe36s43j and https://sudokupad.app/9xd4vu47vt and https://sudokupad.app/erin-toler/cross-threaded . Current cage model assumes no repeats. Inspect whether source true/false/absent means killer, sum-only, or decorative cage; preserve explicit semantics and do not apply false repeat conflicts. Add encountered unsupported cage semantic rules to special-rule handling, retaining original text. Update report only for data actually supported. Use tiny synthetic cases for repeats and checking special rules on/off.
   Thread: to be recorded by worker from its own orchestration identity. Commit: pending.

2. **Q2 — Cell import state. Priority P1. Status: pending.**
   Report: `$.grid[][].given`, `color`, `givenPencilMarks` at https://sudokupad.app/0k1m4dyzji ; `$.cells[][].given` at https://sudokupad.app/c2jsb4l7dg and https://sudokupad.app/7vat08rjx9 . Confirm fixed versus editable values and meaningful authored colors/marks. Preserve what the app model can represent; explicitly report remaining loss. Do not treat a redundant true flag as an import defect.
   Thread: pending. Commit: pending.

3. **Q3 — Graphical fidelity. Priority P1. Status: pending.**
   Report: `angle`, `fontSize`, `stroke-width`, `stroke-linejoin`, `borderSize`, `line[].width`, line caps/fills and dropped short line geometry. Examples https://sudokupad.app/hbd5hddadw , https://sudokupad.app/meo7nfw6x5 , https://sudokupad.app/7vat08rjx9 , https://sudokupad.app/rf7exik4cu , https://sudokupad.app/a48228snwx . The latter baseline reports 239 dropped/incomplete line geometries; inspect whether these are intentional point marks. Group related render/model changes; if too large, split into sequential subitems in this document. Distinguish artwork fidelity from semantic validation.
   Thread: pending. Commit: pending.

4. **Q4 — Region definitions. Priority P1 if nonstandard, otherwise P3 report accuracy. Status: pending.**
   Report: `$.regions` on most SCL examples including https://sudokupad.app/hbd5hddadw . Determine standard redundant boxes versus custom regions. Current checker enforces standard boxes. Preserve/check custom regions if encountered; otherwise classify standard regions accurately without declaring them lost information. Keep unusual unsupported region rules visible in original special rules.
   Thread: pending. Commit: pending.

5. **Q5 — Semantic special rules from unassigned artwork. Priority P2. Status: pending.**
   Report: validation at `$.rules`; line rules remain `Unspecified` in many imports. Start with https://sudokupad.app/philip-newman/20261015-youve-got-a-pal-in-drome , https://sudokupad.app/philip-newman/20261012-octurnal-inversion , https://sudokupad.app/erin-toler/cross-threaded . Inspect original instructions temporarily. Add encountered missing semantic rules to the existing special-rule system where feasible; preserve exact original instruction content and never infer semantics solely from a color. Document genuinely ambiguous/manual rules. Generic prose-review notices are not proof of an unsupported specific rule. Add synthetic validation tests, not game-specific assertions.
   Thread: pending. Commit: pending.

6. **Q6 — Final audit, report accuracy and remaining informational data. Priority P2. Status: pending.**
   Re-run all 24 links and tests after preceding fixes. Review ignored `metadata.source`, `metadata.SudokuMakerURL`, `disabledlogic`, decorative `class` and other remaining paths; distinguish provenance/exporter configuration from solve-affecting loss. Improve report classification without silently hiding meaningful data. Record completed, deferred, failed and manual-only findings, updated counts, commit/thread IDs. Report final outcome; do not launch a further thread when no meaningful pending work remains.
   Thread: pending. Commit: pending.

## Mandatory serial handoff protocol (pass this onward)

1. Read this queue, baseline audit, repository instructions and current git status. You exclusively own the checkout after predecessor handoff. Record your own thread ID in your queue item. Implement only the next coherent item (or record an accurately explained failure/deferment), updating related reporting and preserving original rule text.
2. Real payloads are temporary diagnosis only. Commit only synthetic fixtures using `SudokuFixtures`; no tests fetch live data. Remove downloaded published payloads before handoff.
3. Set `JAVA_HOME=/Users/stefan/.sdkman/candidates/java/current` and prepend `$JAVA_HOME/bin` to `PATH`. Run `./gradlew :shared:jvmTest` for shared changes plus appropriate focused checks. Opt-in audit invocation is in `docs/import-audit.md`.
4. Update item status and evidence; commit your completed work. Record implementation commit IDs in a subsequent queue bookkeeping commit when needed. Finish ALL mutations and tests and ensure clean git status before launching anything.
5. Launch exactly ONE next ordinary top-level T3 thread via `t3_thread_launch`, with `workspaceStrategy:{type:"existing_worktree",worktreePath:"/Users/stefan/.t3/worktrees/sudokupad/t3-3df1bf18",branch:"feat/report-import-omissions"}`. Give it the user objective, queue path, next item, your commit IDs and this entire serial lifecycle. Never batch launch or use subagents in parallel. No new branches/worktrees. Each thread must commit before launching its successor.
6. Stop all file mutations after launch. The successor must record its own ID, and can record the predecessor's returned launch ID if supplied. Include the returned next thread ID/link in your final response. If launch fails, inspect T3 thread list before retry (launch has no idempotency key). Record actual failures; do not silently stall the chain. Do not skip pending items simply because one item was deferred.
7. The last worker performs final audit/tests, commits results, and reports completed/remaining findings without launching invented work.
