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
- Audit/queue commit: `d6761a9` (24/24 live imports, 1,571 baseline entries, 182 JVM tests).
- Baseline: `docs/import-audit-results.md`; input: `docs/import-audit-links.txt`; methodology: `docs/import-audit.md`.

## Ordered work

Each item is a coherent feature investigation/fix, not one task per duplicate puzzle. Source paths below are actual report findings. Confirm source semantics before changing behavior. If redundant, document that with evidence and mark resolved without inventing implementation.

1. **Q1 — Cage uniqueness and special-rule correctness. Priority P1. Status: completed; shading-dependent semantics remain manual as documented below.**
   Report: `$.cages[].unique`, e.g. https://sudokupad.app/peoe36s43j and https://sudokupad.app/9xd4vu47vt and https://sudokupad.app/erin-toler/cross-threaded . Current cage model assumes no repeats. Inspect whether source true/false/absent means killer, sum-only, or decorative cage; preserve explicit semantics and do not apply false repeat conflicts. Add encountered unsupported cage semantic rules to special-rule handling, retaining original text. Update report only for data actually supported. Use tiny synthetic cases for repeats and checking special rules on/off.
   Thread: `mcp:ecb5e1a3-997a-4661-a641-26ceb3229ff4`. Implementation commit: `f531ceb`. Queue bookkeeping follows in the next commit.
   Evidence (2026-10-18): all three cited sources explicitly set `unique:true`; this is redundant with the old checker, not evidence of repeats. Temporary inspection of SudokuPad `script.js?v=0.611.0` (`handleCageFeature`, `withKiller`) confirms explicit flags are preserved and missing uniqueness is inferred from killer rules. Imported SCL cages now preserve true/false; absent flags use killer/no-repeat prose, otherwise impose no uniqueness. f-puzzles killercage defaults to unique; existing saved/native cages retain their default. Numeric totals remain sum constraints. Decorative unlabelled cages impose neither sums nor repeats without rule evidence. Boolean `unique` is now supported in the report schema; malformed flags remain reported.
   Added special-rule support for the exact whole-line shared-total instruction “Cages sum to fifteen” (also numeric totals and optional “All”/terminal punctuation). Stored separately from printed totals to avoid inventing artwork. Original instructions remain unchanged. Unlabelled cages without a supported total now receive a specific manual-review notice. Yin Yang shading, doubled shaded sums, mixed shading per cage, and equal shaded/unshaded sums cannot be validated using the current arbitrary color annotations (no modeled binary shading); explicitly deferred/manual, with original special rules preserved. Other wording still requires review; this is not a prose interpreter.
   Targeted live re-audit: 4/4 imports succeeded; report counts: peoe36s43j 4, 9xd4vu47vt 3, cross-threaded 8, a48228snwx 993. The latter has nine hidden standard-box region cages with `unique:true`; `type`/`hidden` remain reported for Q3/Q4. No new rendering support is claimed. Parity lines/Kropki/XV and negative constraints remain Q5/manual. Temporary published payload diagnostics removed before handoff.
   Validation: 187 JVM tests passed, zero failures/errors, including synthetic repeat and pencil-mark checks with special rules on/off, missing flags, f-puzzles defaults, prose totals, serialization, unsupported cage prose, and malformed flags.

2. **Q2 — Cell import state. Priority P1. Status: completed; ambiguous exported marks remain reported/manual.**
   Report: `$.grid[][].given`, `color`, `givenPencilMarks` at https://sudokupad.app/0k1m4dyzji ; `$.cells[][].given` at https://sudokupad.app/c2jsb4l7dg and https://sudokupad.app/7vat08rjx9 . Confirm fixed versus editable values and meaningful authored colors/marks. Preserve what the app model can represent; explicitly report remaining loss. Do not treat a redundant true flag as an import defect.
   Thread: `mcp:da134477-d28d-4d35-987a-15a0b296e370`. Implementation commit: pending.
   Evidence (2026-10-18): temporary inspection of all three sources found only `given:true` flags on populated values, redundant with fixed clues. SudokuPad 0.611.0 `handleCellFeature` treats SCL values as fixed regardless of `given`; its f-puzzles `createBlankPuzzle` consults `given`. We preserve explicit f-puzzles false values as editable starting digits (serialized separately from fixed clues); absent flags retain existing importer compatibility. SCL boolean flags are redundant and recognized without changing its fixed-value semantics. Malformed flags remain reported.
   Authored CSS hex cell colors now become full-cell underlays, including the gray shading in the cited f-puzzles example. Unsupported color encodings remain reported. Graphical shading does not implement any color-dependent validation. Original special-rule text remains unchanged.
   `givenPencilMarks` remains an explicit omission: the cited export populates it on already fixed digits; SudokuPad's f-puzzles decoder reads `centerPencilMarks`/`cornerPencilMarks`, and its SCL converter reads `centremarks`/`pencilMarks`, not this field. No evidence establishes corner versus center or fixed-mark semantics for this exporter extension. Mapping it to editable notes would invent semantics; faithful fixed authored notes require further model/UI work. These marks remain manual with the original rules available. No new semantic rules were established by this investigation.
   Validation: 189 JVM tests passed, zero failures/errors. Synthetic tests cover explicit fixed/editable values, SCL semantics, legacy absent-flag compatibility, serialization of starting state, shading and malformed flags/colors, and continued reporting of unsupported marks. Targeted live re-audit: 3/3 succeeded; 0k1m4dyzji 15 entries, c2jsb4l7dg 3, 7vat08rjx9 27. Temporary published payloads, source scripts and diagnostics removed before handoff.

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
