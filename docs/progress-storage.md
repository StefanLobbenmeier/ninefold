# Progress storage

The JS and Wasm PWA implementations use synchronous browser `localStorage`.
`GameRepository` serializes each `SavedGame` as JSON under
`ninefold.game.<puzzle ID>.v1`. A game contains the puzzle definition, current
cells, delta-encoded undo and redo stacks, colour settings, and faded outside clues.
Timers, the game index, recent play order, and cached offline puzzles have
separate records. Timer ticks do not rewrite game records.

Previously every undo/redo entry was a complete list of `CellState` objects.
For a 9×9 puzzle, a single-cell edit added another 81-cell snapshot.
History now stores only changed cells, as described below. Each move, undo, and
redo still serializes and synchronously writes the whole compact game record;
this is delta encoding within a record, rather than append-only browser writes.
The puzzle definition also appears in both the game and the offline cache.
For puzzles without a source URL, the storage ID itself is a serialized puzzle,
so keys and game indexes can be large too.

The storage screen estimates browser usage as two bytes per character in keys
and values (UTF-16). It is not a measurement of browser disk usage, and excludes
the app download and browser caches. No measurement of the user's actual 7.5 MB
dataset was available during this investigation.

## Implemented retention

Keep undo and redo for every started, incomplete puzzle and the two most
recently completed puzzles. Clear both stacks for other games, preserving their
current cells, puzzle, settings, and independent timer. Cleanup runs at repository
startup and on game saves, so existing installations shrink without replaying
old puzzles. Failed cleanup writes leave the in-memory history intact and use
the existing storage error reporting; a later cleanup can retry.

Completion order is stored separately from play order. Opening an old completed
puzzle does not promote it. Undoing completion and solving again does. Older
installations have no completion timestamps, so their most recent play order is
used once as the migration fallback.

## Implemented delta encoding

`SavedGameSerializer` keeps the player's in-memory snapshots unchanged but writes
`historyEncoding: 2`, `undoChanges`, and `redoChanges` in the existing game key.
Each step is a map from cell index to its replacement `CellState`. Undo patches
start at the current board and walk backwards from the most recent undo step;
redo patches start independently at the current board and walk forwards.
Only the destination cell is needed, since the preceding board is already known.
Multi-cell edits remain one step, and empty patches preserve no-op steps.
Clears are explicit empty cell objects, so removing values, notes, or colours
is preserved. Grid dimensions come from the saved puzzle.

The decoder accepts legacy snapshot records. Repository startup rewrites those
records after retention cleanup, without requiring another move. A failed write
leaves the previous record available and reports the existing storage error.
Reimports and rollback writes use the same serializer, so reconciled givens are
included in the recomputed patches. Older app versions cannot read delta history;
they can still read the current board but would lose history if they save it.

Synthetic tests cover legacy migration, grouped edits, clears, no-op steps,
undo/redo ordering, colours, notes, and smaller grids. A 60-step single-cell
history must serialize to less than one third of its legacy payload size.

## Further improvements

1. Store puzzle definitions once and reference them from progress records. This
   needs safe migration and careful handling of standalone puzzles and failed
   offline-cache writes; the current embedded puzzle lets games reopen on their own.
2. Consider IndexedDB if synchronous writes cause visible stalls. Moving the same
   JSON there does not itself reduce payload size. Delta encoding addresses the
   repetition first; compression adds codec and migration complexity and should
   be measured against representative synthetic solves before adoption.

Avoid a fixed history-depth limit unless desired as a product change: it would
remove undo steps from puzzles that the retention policy explicitly protects.
