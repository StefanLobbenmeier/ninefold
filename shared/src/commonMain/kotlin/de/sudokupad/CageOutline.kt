package de.sudokupad

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation

/** Join inset cell interiors before stroking, so the dash pattern follows the entire cage. */
internal fun cageOutline(cells: Set<CellPosition>, cellWidth: Float, cellHeight: Float, inset: Float): Path {
    var outline = Path()
    fun add(left: Float, top: Float, right: Float, bottom: Float) {
        val rectangle = Path().apply { addRect(Rect(left, top, right, bottom)) }
        outline = Path.combine(PathOperation.Union, outline, rectangle)
    }
    cells.forEach { cell ->
        val left = cell.column * cellWidth + inset
        val top = cell.row * cellHeight + inset
        val right = (cell.column + 1) * cellWidth - inset
        val bottom = (cell.row + 1) * cellHeight - inset
        add(left, top, right, bottom)
        // Bridges cross shared cell borders, including both inset margins.
        if (CellPosition(cell.row, cell.column + 1) in cells) add(right, top, right + 2 * inset, bottom)
        if (CellPosition(cell.row + 1, cell.column) in cells) add(left, bottom, right, bottom + 2 * inset)
        if (CellPosition(cell.row, cell.column + 1) in cells &&
            CellPosition(cell.row + 1, cell.column) in cells &&
            CellPosition(cell.row + 1, cell.column + 1) in cells) {
            add(right, bottom, right + 2 * inset, bottom + 2 * inset)
        }
    }
    return outline
}
