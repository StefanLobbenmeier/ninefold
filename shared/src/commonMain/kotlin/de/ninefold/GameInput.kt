package de.ninefold

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
            mode == InputMode.Digit -> old.copy(value = number, cornerNotes = emptySet(), centreNotes = emptySet())
            old.value != null -> old
            mode == InputMode.Corner -> old.copy(cornerNotes = if (addNote) old.cornerNotes + number else old.cornerNotes - number)
            else -> old.copy(centreNotes = if (addNote) old.centreNotes + number else old.centreNotes - number)
        }
    } }
}
