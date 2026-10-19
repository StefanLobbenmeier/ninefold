package de.ninefold

import kotlin.math.abs
import kotlin.math.round

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


/** Reveal a whole XV/Kropki mark when either of the two cells sharing its edge is lit. */
internal fun PuzzleOverlay.isRevealedEdgeMark(litCells: Set<CellPosition>): Boolean {
    // SudokuPad exports XV labels with zero-width, 1.05-cell-high text boxes.
    // Their box dimensions describe text sizing rather than the painted glyph bounds.
    val isXV = text?.trim()?.uppercase() in setOf("V", "X") &&
        width in 0.0..1.1 && height > 0 && height <= 1.1
    if (!isXV && !isKropkiDot()) return false
    return edgeMarkCells().any { it in litCells }
}

internal fun PuzzleOverlay.isKropkiDot(): Boolean =
    text.isNullOrBlank() && rounded && width > 0 && height > 0 &&
        width <= .5 && height <= .5 && (backgroundColor != null || borderColor != null) &&
        edgeMarkCells().isNotEmpty()

/** Keep partially hidden quadruples clipped by fog; lift the whole clue once all cells are lit. */
internal fun PuzzleOverlay.isRevealedQuadruple(clues: List<QuadrupleClue>, litCells: Set<CellPosition>): Boolean =
    rounded && clues.any { clue ->
        clue.cells.size == 4 && clue.cells.all { it in litCells } &&
            abs(center.row - (clue.cells.map { it.row }.average() + .5)) < .001 &&
            abs(center.column - (clue.cells.map { it.column }.average() + .5)) < .001
    }

private fun PuzzleOverlay.edgeMarkCells(): List<CellPosition> {
    fun near(a: Double, b: Double) = abs(a - b) < .001
    val row = round(center.row).toInt()
    val column = round(center.column).toInt()
    val horizontal = row in 1..8 && near(center.row, row.toDouble()) &&
        near(center.column, kotlin.math.floor(center.column) + .5) && center.column in 0.0..9.0
    val vertical = column in 1..8 && near(center.column, column.toDouble()) &&
        near(center.row, kotlin.math.floor(center.row) + .5) && center.row in 0.0..9.0
    return when {
        horizontal -> listOf(CellPosition(row - 1, center.column.toInt()), CellPosition(row, center.column.toInt()))
        vertical -> listOf(CellPosition(center.row.toInt(), column - 1), CellPosition(center.row.toInt(), column))
        else -> emptyList()
    }
}
