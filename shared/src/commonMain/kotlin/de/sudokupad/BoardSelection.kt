package de.sudokupad

/** Keep the gesture's initial operation even when it crosses cells in the opposite state. */
internal class BoardSelectionGesture(
    private val initial: Set<CellPosition>,
    start: CellPosition,
    additive: Boolean,
) {
    private val removing = additive && start in initial
    private val base = if (additive) initial else emptySet()
    private val visited = mutableSetOf(start)

    fun visit(cells: Set<CellPosition> = emptySet()): Set<CellPosition> {
        visited.addAll(cells)
        return if (removing) base - visited else base + visited
    }
}

internal enum class SelectionBorder { Top, Right, Bottom, Left }

internal fun selectionBorders(position: CellPosition, selected: Set<CellPosition>): Set<SelectionBorder> {
    if (position !in selected) return emptySet()
    return buildSet {
        if (CellPosition(position.row - 1, position.column) !in selected) add(SelectionBorder.Top)
        if (CellPosition(position.row, position.column + 1) !in selected) add(SelectionBorder.Right)
        if (CellPosition(position.row + 1, position.column) !in selected) add(SelectionBorder.Bottom)
        if (CellPosition(position.row, position.column - 1) !in selected) add(SelectionBorder.Left)
    }
}

/** Sven's tool-sensitive matching order, excluding cosmetic clues that are not cell state. */
internal fun toolSensitiveSelection(
    cells: List<CellState>,
    position: CellPosition,
    mode: InputMode,
): Set<CellPosition> {
    val source = cells[position.index]
    val selectors: List<(CellState) -> Boolean> = when (mode) {
        InputMode.Digit -> listOf(
            { it.value != null && it.value == source.value },
            { it.colours.intersect(source.colours).isNotEmpty() },
            { source.centreNotes.isNotEmpty() && it.centreNotes.containsAll(source.centreNotes) },
            { it.cornerNotes.intersect(source.cornerNotes).isNotEmpty() },
        )
        InputMode.Centre -> listOf(
            { it.value != null && it.value == source.value },
            { source.centreNotes.isNotEmpty() && it.centreNotes.containsAll(source.centreNotes) },
            { it.cornerNotes.intersect(source.cornerNotes).isNotEmpty() },
            { it.colours.intersect(source.colours).isNotEmpty() },
        )
        InputMode.Corner -> listOf(
            { it.value != null && it.value == source.value },
            { it.cornerNotes.intersect(source.cornerNotes).isNotEmpty() },
            { source.centreNotes.isNotEmpty() && it.centreNotes.containsAll(source.centreNotes) },
            { it.colours.intersect(source.colours).isNotEmpty() },
        )
        InputMode.Colour -> listOf(
            { it.colours.intersect(source.colours).isNotEmpty() },
            { it.value != null && it.value == source.value },
            { source.centreNotes.isNotEmpty() && it.centreNotes.containsAll(source.centreNotes) },
            { it.cornerNotes.intersect(source.cornerNotes).isNotEmpty() },
        )
    }
    val selector = selectors.firstOrNull { it(source) } ?: return emptySet()
    return cells.indices.filterTo(linkedSetOf()) { selector(cells[it]) }.mapTo(linkedSetOf()) {
        CellPosition(it / 9, it % 9)
    }
}

/** Board coordinates are normalized to 0..1; outside clues never select a cell. */
internal fun boardCellAt(x: Float, y: Float): CellPosition? {
    if (!x.isFinite() || !y.isFinite() || x < 0f || y < 0f || x >= 1f || y >= 1f) return null
    return CellPosition((y * 9).toInt(), (x * 9).toInt())
}

/** Drag targets span 75% of each cell, or the full cell for additive selection. */
internal fun boardCellsAlongSegment(startX: Float, startY: Float, endX: Float, endY: Float, additive: Boolean = false): Set<CellPosition> {
    if (!listOf(startX, startY, endX, endY).all { it.isFinite() }) return emptySet()
    val inset = if (additive) 0f else .125f
    val dx = endX - startX
    val dy = endY - startY
    return buildSet {
        for (row in 0..8) for (column in 0..8) {
            var enter = 0f
            var exit = 1f
            fun intersectsAxis(start: Float, delta: Float, min: Float, max: Float): Boolean {
                if (delta == 0f) return start in min..max
                val a = (min - start) / delta
                val b = (max - start) / delta
                enter = maxOf(enter, minOf(a, b))
                exit = minOf(exit, maxOf(a, b))
                return enter <= exit
            }
            // Intersect the whole segment so fast drags still include every crossed target.
            if (intersectsAxis(startX, dx, (column + inset) / 9f, (column + 1f - inset) / 9f) &&
                intersectsAxis(startY, dy, (row + inset) / 9f, (row + 1f - inset) / 9f)) {
                add(CellPosition(row, column))
            }
        }
    }
}
