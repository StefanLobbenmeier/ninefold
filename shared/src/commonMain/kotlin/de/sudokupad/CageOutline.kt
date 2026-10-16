package de.sudokupad

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle

// Eight repeats per cell. Cell borders sit in the middle of a gap; the
// inset (half a repeat) puts every convex and concave turn in a dash's middle.
internal const val CAGE_REPEATS_PER_CELL = 8
internal fun cageInset(cellSize: Float): Float = cellSize / (2 * CAGE_REPEATS_PER_CELL)

/** A virtual grid of erasing strips, applied only while drawing cage strokes. */
internal fun cageGapMask(cellWidth: Float, cellHeight: Float, columns: Int = 9, rows: Int = 9): Path = Path().apply {
    val periodX = cellWidth / CAGE_REPEATS_PER_CELL
    val periodY = cellHeight / CAGE_REPEATS_PER_CELL
    // A half-period gap leaves half-period dashes. Both are anchored to the
    // board, so runs of any length have exactly the same phase across cells.
    for (index in 0..columns * CAGE_REPEATS_PER_CELL) {
        val center = index * periodX
        addRect(Rect(center - periodX / 4, -cellHeight, center + periodX / 4, (rows + 1) * cellHeight))
    }
    for (index in 0..rows * CAGE_REPEATS_PER_CELL) {
        val center = index * periodY
        addRect(Rect(-cellWidth, center - periodY / 4, (columns + 1) * cellWidth, center + periodY / 4))
    }
}

internal fun cageOutlinePaint(width: Float, colour: Color = Color(0xFF826B87), antiAlias: Boolean = false) = Paint().apply {
    color = colour
    style = PaintingStyle.Stroke
    strokeWidth = width
    strokeCap = StrokeCap.Butt
    strokeJoin = StrokeJoin.Miter
    isAntiAlias = antiAlias
}

/** Join inset cell interiors into a solid outline without shared internal borders. */
internal fun cageOutline(
    cells: Set<CellPosition>, cellWidth: Float, cellHeight: Float, inset: Float,
    insetY: Float = inset,
): Path {
    var outline = Path()
    fun add(left: Float, top: Float, right: Float, bottom: Float) {
        val rectangle = Path().apply { addRect(Rect(left, top, right, bottom)) }
        outline = Path.combine(PathOperation.Union, outline, rectangle)
    }
    cells.forEach { cell ->
        val left = cell.column * cellWidth + inset
        val top = cell.row * cellHeight + insetY
        val right = (cell.column + 1) * cellWidth - inset
        val bottom = (cell.row + 1) * cellHeight - insetY
        add(left, top, right, bottom)
        // Bridges cross shared cell borders, including both inset margins.
        if (CellPosition(cell.row, cell.column + 1) in cells) add(right, top, right + 2 * inset, bottom)
        if (CellPosition(cell.row + 1, cell.column) in cells) add(left, bottom, right, bottom + 2 * insetY)
        if (CellPosition(cell.row, cell.column + 1) in cells &&
            CellPosition(cell.row + 1, cell.column) in cells &&
            CellPosition(cell.row + 1, cell.column + 1) in cells) {
            add(right, bottom, right + 2 * inset, bottom + 2 * insetY)
        }
    }
    return outline
}
