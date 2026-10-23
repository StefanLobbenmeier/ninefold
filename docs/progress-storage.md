# Progress storage

The JS and Wasm PWA implementations use synchronous browser `localStorage`.
`GameRepository` serializes each `SavedGame` as JSON under
`ninefold.game.<puzzle ID>.v1`. A game contains the puzzle definition, current
cells, full undo and redo stacks, colour settings, and faded outside clues.
Timers, the game index, recent play order, and cached offline puzzles have
separate records. Timer ticks do not rewrite game records.

Every undo/redo entry is a complete list of `CellState` objects. For a 9×9
puzzle, a single-cell edit adds another 81-cell snapshot. JSON omits default
properties, but still repeats unchanged values, notes, colours, and givens.
Each move, undo, and redo serializes and synchronously writes the whole game
record. This increases both storage and write work as a solve gets longer.
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

## Further improvements

1. Persist history as cell deltas with an index and previous/new `CellState` for
   each changed cell. A typical single-cell move then stores one changed cell
   instead of 81. Keep grouping for multi-cell edits. Test undo/redo ordering,
   colours, notes, givens reconciliation on reimport, and legacy snapshot saves.
   This is the most direct improvement for the histories retained by this change.
2. Store puzzle definitions once and reference them from progress records. This
   needs safe migration and careful handling of standalone puzzles and failed
   offline-cache writes; the current embedded puzzle lets games reopen on their own.
3. Consider IndexedDB if synchronous writes cause visible stalls. Moving the same
   JSON there does not itself reduce payload size. Delta encoding addresses the
   repetition first; compression adds codec and migration complexity and should
   be measured against representative synthetic solves before adoption.

Avoid a fixed history-depth limit unless desired as a product change: it would
remove undo steps from puzzles that the retention policy explicitly protects.
