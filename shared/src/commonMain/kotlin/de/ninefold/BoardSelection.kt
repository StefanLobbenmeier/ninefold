package de.ninefold

/** Keep the gesture's initial operation even when it crosses cells in the opposite state. */
internal class BoardSelectionGesture(
    private val initial: Set<CellPosition>,
    private val start: CellPosition,
    private val additive: Boolean,
) {
    private val removing = additive && start in initial
    private val base = if (additive) initial else emptySet()
    private val visited = mutableSetOf(start)

    fun tap(): Set<CellPosition> =
        if (!additive && initial == setOf(start)) emptySet() else visit()

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

/** Filled values take priority; empty cells use the active tool's matching order. */
internal fun toolSensitiveSelection(
    cells: List<CellState>,
    position: CellPosition,
    mode: InputMode,
): Set<CellPosition> {
    val source = cells[position.index(gridSize(cells))]
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
            { it.value != null && it.value == source.value },
            { it.colours.intersect(source.colours).isNotEmpty() },
            { source.centreNotes.isNotEmpty() && it.centreNotes.containsAll(source.centreNotes) },
            { it.cornerNotes.intersect(source.cornerNotes).isNotEmpty() },
        )
    }
    val selector = selectors.firstOrNull { it(source) } ?: return emptySet()
    return cells.indices.filterTo(linkedSetOf()) { selector(cells[it]) }.mapTo(linkedSetOf()) {
        CellPosition(it / gridSize(cells), it % gridSize(cells))
    }
}

/** Board coordinates are normalized to 0..1; outside clues never select a cell. */
internal fun boardCellAt(x: Float, y: Float, size: Int = 9): CellPosition? {
    if (!x.isFinite() || !y.isFinite() || x < 0f || y < 0f || x >= 1f || y >= 1f) return null
    return CellPosition((y * size).toInt(), (x * size).toInt())
}

/** Drag targets span 75% of each cell, or the full cell for additive selection. */
internal fun boardCellsAlongSegment(startX: Float, startY: Float, endX: Float, endY: Float, additive: Boolean = false, size: Int = 9): Set<CellPosition> {
    if (!listOf(startX, startY, endX, endY).all { it.isFinite() }) return emptySet()
    val gridSize = size
    val inset = if (additive) 0f else .125f
    val dx = endX - startX
    val dy = endY - startY
    return buildSet {
        for (row in 0 until gridSize) for (column in 0 until gridSize) {
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
            if (intersectsAxis(startX, dx, (column + inset) / gridSize, (column + 1f - inset) / gridSize) &&
                intersectsAxis(startY, dy, (row + inset) / gridSize, (row + 1f - inset) / gridSize)) {
                add(CellPosition(row, column))
            }
        }
    }
}
