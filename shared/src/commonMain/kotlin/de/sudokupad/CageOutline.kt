package de.sudokupad

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import kotlin.math.roundToInt

// A corner dash reaches the outside edge of the perpendicular stroke.
internal fun cageOutlinePaint(width: Float, dash: Float, gap: Float, colour: Color = Color(0xFF826B87)) = Paint().apply {
    color = colour
    style = PaintingStyle.Stroke
    strokeWidth = width
    strokeCap = StrokeCap.Square
    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap))
    // Axis-aligned cage edges are snapped to pixels; smoothing their dash corners
    // would reintroduce partially covered pixels with a different apparent colour.
    isAntiAlias = false
}

/** Join inset cell interiors before stroking, so the dash pattern follows the entire cage. */
internal fun cageOutline(
    cells: Set<CellPosition>, cellWidth: Float, cellHeight: Float, inset: Float,
    strokeWidth: Float = 0f,
): Path {
    var outline = Path()
    // Place the edges of the stroke on physical pixel boundaries. Otherwise a
    // one-pixel line straddles two pixels and its corners appear darker than its sides.
    fun align(value: Float): Float = if (strokeWidth > 0f) {
        val phase = (strokeWidth.toInt() % 2) / 2f
        (value - phase).roundToInt() + phase
    } else value
    fun add(left: Float, top: Float, right: Float, bottom: Float) {
        val rectangle = Path().apply { addRect(Rect(align(left), align(top), align(right), align(bottom))) }
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
