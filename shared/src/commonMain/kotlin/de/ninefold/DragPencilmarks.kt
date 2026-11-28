package de.ninefold

/** Walk the drag in pointer order. Each newly selected cell gets one chance to change. */
internal fun clearDraggedPencilmarks(
    puzzle: Puzzle,
    cells: List<CellState>,
    path: List<CellPosition>,
    added: Set<CellPosition>,
    targets: Set<CellPosition> = added,
    lit: Set<CellPosition> = puzzle.litCells(cells),
): List<CellState> {
    if (added.size < 2) return cells

    val next = cells.toMutableList()
    fun cell(position: CellPosition): CellState {
        val state = next[position.index(puzzle.size)]
        return if (state.given && position !in lit) CellState() else state
    }

    val rulesByCell = puzzle.sudokuRules().flatMap { rule -> rule.cells.map { it to rule } }
        .groupBy({ it.first }, { it.second })
    val previous = mutableListOf<CellPosition>()
    val visited = mutableSetOf<CellPosition>()
    for (target in path) {
        if (target !in added) {
            previous.clear()
            continue
        }
        val old = next[target.index(puzzle.size)]
        if (visited.add(target) && target in targets && !old.given && old.value == null) {
            val digits = 1..puzzle.size
            val activeRules = rulesByCell[target].orEmpty().mapNotNull { rule ->
                rule.duringDrag(target, previous, lit, digits, ::cell)
            }
            fun allowed(digit: Int): Boolean = activeRules.none { it.rejects(digit) }
            val fillRules = activeRules.filter { it.canFill }
            val inferred = if (fillRules.isEmpty()) null else digits.singleOrNull { digit ->
                fillRules.none { it.rejects(digit) }
            }
            val centre = old.centreNotes.filterTo(mutableSetOf(), ::allowed)
            val corner = old.cornerNotes.filterTo(mutableSetOf(), ::allowed)
            val replacement = when {
                inferred != null && allowed(inferred) &&
                    (old.centreNotes.isEmpty() || inferred in old.centreNotes) ->
                    old.copy(value = inferred)
                fillRules.isNotEmpty() -> old
                old.centreNotes.isNotEmpty() && centre.isEmpty() -> old
                centre.size == 1 && centre != old.centreNotes ->
                    old.copy(value = centre.single(), centreNotes = centre, cornerNotes = corner)
                else -> old.copy(centreNotes = centre, cornerNotes = corner)
            }
            next[target.index(puzzle.size)] = replacement
        }
        previous.add(target)
    }
    return next
}
