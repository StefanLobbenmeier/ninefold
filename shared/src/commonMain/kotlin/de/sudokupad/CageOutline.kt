package de.sudokupad

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import kotlin.math.roundToInt

private fun alignCageCoordinate(value: Float, strokeWidth: Float): Float = if (strokeWidth > 0f) {
    val phase = (strokeWidth.toInt() % 2) / 2f
    (value - phase).roundToInt() + phase
} else value

private data class CageCorner(val position: Offset, val horizontal: Int, val vertical: Int)

private fun cageCornerPositions(
    cells: Set<CellPosition>, cellWidth: Float, cellHeight: Float, inset: Float, strokeWidth: Float,
): List<CageCorner> = buildList {
    cells.forEach { cell ->
        for (rowDirection in listOf(-1, 1)) for (columnDirection in listOf(-1, 1)) {
            val horizontal = CellPosition(cell.row, cell.column + columnDirection) in cells
            val vertical = CellPosition(cell.row + rowDirection, cell.column) in cells
            val diagonal = CellPosition(cell.row + rowDirection, cell.column + columnDirection) in cells
            val convex = !horizontal && !vertical
            val concave = horizontal && vertical && !diagonal
            if (!convex && !concave) continue
            val x = alignCageCoordinate((cell.column + if (columnDirection > 0) 1 else 0) * cellWidth - columnDirection * inset, strokeWidth)
            val y = alignCageCoordinate((cell.row + if (rowDirection > 0) 1 else 0) * cellHeight - rowDirection * inset, strokeWidth)
            val direction = if (convex) -1 else 1
            add(CageCorner(Offset(x, y), direction * columnDirection, direction * rowDirection))
        }
    }
}

/** Whole dashes fitted between equal corner arms, with equal gaps at both ends. */
internal fun cageDashIntervals(length: Float, arm: Float, dash: Float, preferredGap: Float): List<Pair<Float, Float>> {
    val available = (length - 2 * arm).coerceAtLeast(0f)
    val count = ((available - preferredGap) / (dash + preferredGap)).roundToInt().coerceAtLeast(0)
    val gap = (available - count * dash) / (count + 1)
    return List(count) { index ->
        val start = arm + gap + index * (dash + gap)
        start to start + dash
    }
}

/** Each straight run restarts its pattern so no dash is clipped at a turn. */
internal fun cageDashes(
    cells: Set<CellPosition>, cellWidth: Float, cellHeight: Float, inset: Float,
    strokeWidth: Float, dash: Float, gap: Float,
): Path = Path().apply {
    val corners = cageCornerPositions(cells, cellWidth, cellHeight, inset, strokeWidth)
    // Include the half-stroke at the elbow in the arm's visible length. This
    // also puts arm tips on pixel boundaries on both sides of a snapped corner.
    val arm = (minOf(dash, (cellWidth - 2 * inset) / 2, (cellHeight - 2 * inset) / 2) - strokeWidth / 2).coerceAtLeast(0f)
    corners.forEach { (position, horizontal, vertical) ->
        moveTo(position.x + horizontal * arm, position.y)
        lineTo(position.x, position.y)
        lineTo(position.x, position.y + vertical * arm)
    }
    // Visit each run once, from its left or top corner. Matching directions also
    // keep disconnected cages and holes from joining across empty space.
    corners.forEach { corner ->
        for (horizontal in listOf(true, false)) {
            if ((if (horizontal) corner.horizontal else corner.vertical) != 1) continue
            val start = if (horizontal) corner.position.x else corner.position.y
            val end = corners.filter { other ->
                if (horizontal) other.position.y == corner.position.y && other.position.x > start && other.horizontal == -1
                else other.position.x == corner.position.x && other.position.y > start && other.vertical == -1
            }.minOfOrNull { if (horizontal) it.position.x else it.position.y } ?: continue
            cageDashIntervals(end - start, arm, dash, gap).forEach { (from, to) ->
                if (horizontal) {
                    moveTo(start + from, corner.position.y)
                    lineTo(start + to, corner.position.y)
                } else {
                    moveTo(corner.position.x, start + from)
                    lineTo(corner.position.x, start + to)
                }
            }
        }
    }
}

internal fun cageOutlinePaint(width: Float, colour: Color = Color(0xFF826B87)) = Paint().apply {
    color = colour
    style = PaintingStyle.Stroke
    strokeWidth = width
    strokeCap = StrokeCap.Butt
    // Pixel-aligned shafts and a single opaque draw keep corners the same colour.
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
    fun align(value: Float) = alignCageCoordinate(value, strokeWidth)
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
