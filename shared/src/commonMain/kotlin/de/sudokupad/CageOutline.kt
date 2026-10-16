package de.sudokupad

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import kotlin.math.roundToInt

// Eight repeats per cell. Cell borders sit in the middle of a gap; the
// inset (half a repeat) puts every convex and concave turn in a dash's middle.
internal const val CAGE_REPEATS_PER_CELL = 8
internal fun cageInset(cellSize: Float): Float = cellSize / (2 * CAGE_REPEATS_PER_CELL)

private class CageAxis(val cellSize: Float, strokeWidth: Float) {
    private val period = cellSize / CAGE_REPEATS_PER_CELL
    private val width = strokeWidth.roundToInt().coerceAtLeast(1)
    private val phase = (width % 2) / 2f
    // Matching parity gives each dash an exact centre for the stroke. Leave at
    // least one whole pixel on either side so parallel gap strips cannot graze it.
    private val dash = (2 * ((period / 2 - width) / 2).roundToInt().coerceAtLeast(1) + width).toFloat()
    fun center(index: Int): Float {
        val cell = index / CAGE_REPEATS_PER_CELL
        val slot = index % CAGE_REPEATS_PER_CELL
        val left = (cell * cellSize).roundToInt()
        val right = ((cell + 1) * cellSize).roundToInt()
        // Round one half of a cell and reflect it. Rounding both halves
        // independently would bias odd-width strokes by one pixel at ties.
        val firstHalfSlot = minOf(slot, CAGE_REPEATS_PER_CELL - 1 - slot)
        val ideal = left + (firstHalfSlot + .5f) * (right - left) / CAGE_REPEATS_PER_CELL
        val aligned = (ideal - phase).roundToInt() + phase
        return if (slot < CAGE_REPEATS_PER_CELL / 2) aligned else left + right - aligned
    }
    fun align(value: Float): Float = center((value / period - .5f).roundToInt())
    fun start(index: Int): Float = center(index) - dash / 2
    fun end(index: Int): Float = center(index) + dash / 2
}

/** Integer-edged erasing strips, applied only while drawing cage strokes. */
internal fun cageGapMask(
    cellWidth: Float, cellHeight: Float, columns: Int = 9, rows: Int = 9, strokeWidth: Float = 1f,
): Path = Path().apply {
    val x = CageAxis(cellWidth, strokeWidth)
    val y = CageAxis(cellHeight, strokeWidth)
    for (index in 0..columns * CAGE_REPEATS_PER_CELL) {
        val left = if (index == 0) -cellWidth else x.end(index - 1)
        val right = if (index == columns * CAGE_REPEATS_PER_CELL) (columns + 1) * cellWidth else x.start(index)
        if (right > left) addRect(Rect(left, -cellHeight, right, (rows + 1) * cellHeight))
    }
    for (index in 0..rows * CAGE_REPEATS_PER_CELL) {
        val top = if (index == 0) -cellHeight else y.end(index - 1)
        val bottom = if (index == rows * CAGE_REPEATS_PER_CELL) (rows + 1) * cellHeight else y.start(index)
        if (bottom > top) addRect(Rect(-cellWidth, top, (columns + 1) * cellWidth, bottom))
    }
}

internal fun cageOutlinePaint(width: Float, colour: Color = Color(0xFF826B87)) = Paint().apply {
    color = colour
    style = PaintingStyle.Stroke
    strokeWidth = width
    strokeCap = StrokeCap.Butt
    strokeJoin = StrokeJoin.Miter
    isAntiAlias = false
}

/** Join inset cell interiors into a solid outline without shared internal borders. */
internal fun cageOutline(
    cells: Set<CellPosition>, cellWidth: Float, cellHeight: Float, inset: Float,
    insetY: Float = inset, strokeWidth: Float = 0f,
): Path {
    var outline = Path()
    val x = if (strokeWidth > 0f) CageAxis(cellWidth, strokeWidth) else null
    val y = if (strokeWidth > 0f) CageAxis(cellHeight, strokeWidth) else null
    fun add(left: Float, top: Float, right: Float, bottom: Float) {
        val rectangle = Path().apply {
            addRect(Rect(x?.align(left) ?: left, y?.align(top) ?: top,
                x?.align(right) ?: right, y?.align(bottom) ?: bottom))
        }
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
