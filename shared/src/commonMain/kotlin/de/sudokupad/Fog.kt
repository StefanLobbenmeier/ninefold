package de.sudokupad

internal fun Puzzle.litCells(cells: List<CellState>): Set<CellPosition> {
    val initial = fogLights ?: return buildSet { repeat(9) { row -> repeat(9) { column -> add(CellPosition(row, column)) } } }
    return buildSet {
        addAll(initial)
        if (fogLinks != null) {
            fogLinks.forEach { link ->
                if (link.triggers.all { position ->
                    val value = cells.getOrNull(position.index)?.value
                    value != null && (solution == null || solution.getOrNull(position.index)?.digitToIntOrNull() == value)
                }) addAll(link.cells)
            }
            return@buildSet
        }
        cells.forEachIndexed { index, cell ->
            val position = CellPosition(index / 9, index % 9)
            if (cell.given) add(position)
            else if (cell.value != null && (solution?.getOrNull(index)?.digitToIntOrNull() == cell.value || solution == null)) {
                for (row in position.row - 1..position.row + 1) for (column in position.column - 1..position.column + 1) {
                    if (row in 0..8 && column in 0..8) add(CellPosition(row, column))
                }
            }
        }
    }
}

