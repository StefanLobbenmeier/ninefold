package de.sudokupad

/** Toggle shared notes or colours consistently across the selection. */
internal fun applyInput(cells: List<CellState>, selected: Set<CellPosition>, mode: InputMode, number: Int?): List<CellState> {
    if (mode == InputMode.Colour) {
        if (number != null && CellColour.fromNumber(number) == null) return cells
        val addColour = selected.any { number !in cells[it.index].colours }
        return cells.toMutableList().also { next -> selected.forEach { position ->
            val old = cells[position.index]
            next[position.index] = old.copy(colours = when {
                number == null -> emptySet()
                addColour -> old.colours + number
                else -> old.colours - number
            })
        } }
    }
    val editable = selected.map { it.index }.filter { !cells[it].given }
    val addNote = editable.any { index ->
        val cell = cells[index]
        cell.value == null && number !in (if (mode == InputMode.Corner) cell.cornerNotes else cell.centreNotes)
    }
    return cells.toMutableList().also { next -> editable.forEach { index ->
        val old = cells[index]
        next[index] = when {
            number == null -> old.copy(value = null, cornerNotes = emptySet(), centreNotes = emptySet(), colours = emptySet())
            mode == InputMode.Digit -> old.copy(value = number, cornerNotes = emptySet(), centreNotes = emptySet())
            old.value != null -> old
            mode == InputMode.Corner -> old.copy(cornerNotes = if (addNote) old.cornerNotes + number else old.cornerNotes - number)
            else -> old.copy(centreNotes = if (addNote) old.centreNotes + number else old.centreNotes - number)
        }
    } }
}
