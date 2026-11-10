package de.ninefold

/** Apply optional automations when cells leave the selection, preserving notes under digits. */
internal fun finalizeCellInput(
    puzzle: Puzzle,
    cells: List<CellState>,
    leaving: Set<CellPosition>,
    editing: Set<CellPosition>,
    settings: CheckingSettings,
): List<CellState> {
    val promoted = if (settings.promoteSingleCentreDigit) cells.mapIndexed { index, cell ->
        if (CellPosition(index / puzzle.size, index % puzzle.size) in leaving &&
            !cell.given && cell.value == null && cell.centreNotes.size == 1) {
            cell.copy(value = cell.centreNotes.single())
        } else cell
    } else cells
    return if (settings.centreDigitsRemoveCornerDigits) cleanCornerNotes(puzzle, promoted, leaving, editing) else promoted
}

/** Finalize centre edits only after leaving the cells, keeping narrower corner reminders. */
internal fun cleanCornerNotes(
    puzzle: Puzzle,
    cells: List<CellState>,
    leaving: Set<CellPosition>,
    editing: Set<CellPosition>,
): List<CellState> {
    if (leaving.isEmpty()) return cells
    val next = cells.toMutableList()
    leaving.forEach { position ->
        val index = position.index(puzzle.size)
        val cell = cells[index]
        if (!cell.given && cell.value == null && cell.centreNotes.isNotEmpty()) {
            next[index] = cell.copy(cornerNotes = cell.cornerNotes intersect cell.centreNotes)
        }
    }
    puzzle.regionCells().forEach { region ->
        val groups = region.filter { position ->
            val cell = cells[position.index(puzzle.size)]
            !cell.given && cell.value == null && cell.centreNotes.size >= 2
        }.groupBy { cells[it.index(puzzle.size)].centreNotes }
        groups.forEach { (digits, positions) ->
            // A complete pair/triplet makes a corner reminder redundant only when it
            // covers the whole group. Wait until every member has left the selection.
            if (positions.size == digits.size && positions.any { it in leaving } && positions.none { it in editing }) {
                val redundant = digits.filter { digit -> positions.all { digit in cells[it.index(puzzle.size)].cornerNotes } }.toSet()
                positions.forEach { position ->
                    val index = position.index(puzzle.size)
                    next[index] = next[index].copy(cornerNotes = next[index].cornerNotes - redundant)
                }
            }
        }
    }
    return next
}

/** Toggle shared notes or colours consistently across the selection. */
internal fun applyInput(cells: List<CellState>, selected: Set<CellPosition>, mode: InputMode, number: Int?): List<CellState> {
    if (mode != InputMode.Colour && number != null && number in 1..9 && number > gridSize(cells)) return cells
    if (mode == InputMode.Colour) {
        if (number != null && CellColour.fromNumber(number) == null) return cells
        val addColour = selected.any { number !in cells[it.index(gridSize(cells))].colours }
        return cells.toMutableList().also { next -> selected.forEach { position ->
            val old = cells[position.index(gridSize(cells))]
            next[position.index(gridSize(cells))] = old.copy(colours = when {
                number == null -> emptySet()
                addColour -> old.colours + number
                else -> old.colours - number
            })
        } }
    }
    val editable = selected.map { it.index(gridSize(cells)) }.filter { !cells[it].given }
    if (number == null) {
        val eraseMode = listOf(mode, InputMode.Corner, InputMode.Centre, InputMode.Colour, InputMode.Digit).distinct()
            .firstOrNull { category -> editable.any { index ->
                val cell = cells[index]
                when (category) {
                    InputMode.Digit -> cell.value != null
                    InputMode.Corner -> cell.cornerNotes.isNotEmpty()
                    InputMode.Centre -> cell.centreNotes.isNotEmpty()
                    InputMode.Colour -> cell.colours.isNotEmpty()
                }
            } } ?: return cells
        return cells.toMutableList().also { next -> editable.forEach { index ->
            val old = cells[index]
            next[index] = when (eraseMode) {
                InputMode.Digit -> old.copy(value = null)
                InputMode.Corner -> old.copy(cornerNotes = emptySet())
                InputMode.Centre -> old.copy(centreNotes = emptySet())
                InputMode.Colour -> old.copy(colours = emptySet())
            }
        } }
    }
    val addNote = editable.any { index ->
        val cell = cells[index]
        cell.value == null && number !in (if (mode == InputMode.Corner) cell.cornerNotes else cell.centreNotes)
    }
    return cells.toMutableList().also { next -> editable.forEach { index ->
        val old = cells[index]
        next[index] = when {
            mode == InputMode.Digit -> old.copy(value = number)
            old.value != null -> old
            mode == InputMode.Corner -> old.copy(cornerNotes = if (addNote) old.cornerNotes + number else old.cornerNotes - number)
            else -> old.copy(centreNotes = if (addNote) old.centreNotes + number else old.centreNotes - number)
        }
    } }
}
