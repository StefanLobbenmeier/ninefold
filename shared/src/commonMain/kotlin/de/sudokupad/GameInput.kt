package de.sudokupad

/** Add a shared note to all empty selected cells, or remove it if all already contain it. */
internal fun applyInput(cells: List<CellState>, selected: Set<CellPosition>, mode: InputMode, number: Int?): List<CellState> {
    val editable = selected.map { it.index }.filter { !cells[it].given }
    val addNote = editable.any { index ->
        val cell = cells[index]
        cell.value == null && number !in (if (mode == InputMode.Corner) cell.cornerNotes else cell.centreNotes)
    }
    return cells.toMutableList().also { next -> editable.forEach { index ->
        val old = cells[index]
        next[index] = when {
            number == null -> old.copy(value = null, cornerNotes = emptySet(), centreNotes = emptySet())
            mode == InputMode.Digit -> old.copy(value = number, cornerNotes = emptySet(), centreNotes = emptySet())
            old.value != null -> old
            mode == InputMode.Corner -> old.copy(cornerNotes = if (addNote) old.cornerNotes + number else old.cornerNotes - number)
            else -> old.copy(centreNotes = if (addNote) old.centreNotes + number else old.centreNotes - number)
        }
    } }
}
