package de.ninefold

import kotlin.math.floor
import kotlin.math.roundToInt

/** Symmetric margin in cell units, keeping the grid square and centered. */
internal fun Puzzle.boardPaddingCells(): Float {
    var outside = 0.0
    fun include(point: GridPoint, halfWidth: Double = 0.0, halfHeight: Double = halfWidth) {
        outside = maxOf(outside, halfWidth - point.column, point.column + halfWidth - size,
            halfHeight - point.row, point.row + halfHeight - size)
    }
    // Some exporters draw outside arrows entirely as decorative line segments.
    lines.forEach { line ->
        line.points.forEach { include(it, line.thickness / 100.0) }
        if (line.rule.isThermometer && line.drawThermometerBulb) line.points.firstOrNull()?.let { include(it, .35) }
    }
    arrows.forEach { arrow ->
        arrow.points.forEach { include(it, arrow.thickness / 100.0) }
        if (arrow.points.size >= 2) {
            // The head can extend beyond the shaft in either axis.
            include(arrow.points.last(), arrow.headLength + arrow.thickness / 100.0)
        }
    }
    (overlays + underlays).forEach {
        val textHeight = if (it.text.isNullOrBlank()) 0.0 else it.fontSize ?: 0.0
        include(it.center, maxOf(it.width / 2, textHeight * it.text.orEmpty().length / 2),
            maxOf(it.height / 2, textHeight / 2))
    }
    return if (outside > 0) maxOf(1.0, outside).toFloat() else 0f
}

/** Equal-sized cells, with extra space reserved for the two heavier box dividers. */
internal class BoardAxis(val extent: Float, boxGap: Float, val size: Int = 9) {
    // Rotation can temporarily measure the board smaller than its dividers.
    val boxGap = (if (size == 9) boxGap else 0f).coerceIn(0f, extent.coerceAtLeast(0f) / 2f)
    val cellSize = (extent - 2 * this.boxGap).coerceAtLeast(0f) / size
    fun start(index: Int): Float = index * cellSize + (index / 3) * boxGap

    fun divider(index: Int): Float =
        ((start(index - 1) + cellSize).roundToInt() + start(index).roundToInt()) / 2f

    /** Grid points on a box boundary sit in the middle of its divider. */
    fun at(value: Double): Float {
        if (value <= 0.0) return (value * cellSize).toFloat()
        if (value >= size.toDouble()) return extent + ((value - size) * cellSize).toFloat()
        val index = floor(value).toInt()
        if (value == index.toDouble()) return divider(index)
        return start(index) + ((value - index) * cellSize).toFloat()
    }

    fun normalized(value: Float): Float {
        for (box in 0..1) {
            val edge = (box + 1) * 3
            val left = start(edge) - boxGap
            if (value in left..start(edge)) return edge.toFloat() / size
        }
        val gaps = (1..2).count { value > start(it * 3) }
        return (value - gaps * boxGap) / cellSize / size
    }
}

/** Outside text is actionable even when its rule is not understood by the importer. */
internal fun PuzzleOverlay.isOutsideTextClue(size: Int = 9): Boolean = !text.isNullOrBlank() &&
    (center.row < 0 || center.row > size || center.column < 0 || center.column > size)

/** All shaft points must stay outside the grid, including diagonal arrows ending on its edge. */
internal fun PuzzleArrow.isOutsideClue(size: Int): Boolean = points.size >= 2 &&
    circle == null && pathCells.isEmpty() && points.all {
        it.row <= 0 || it.row >= size || it.column <= 0 || it.column >= size
    } && points.any {
        it.row < 0 || it.row > size || it.column < 0 || it.column > size
    }

/** Exported waypoints describe bends and endpoints, and may skip cells along a straight shaft. */
internal fun gridPathCells(points: List<GridPoint>, size: Int): List<CellPosition> =
    points.zipWithNext().flatMap { (start, end) ->
        val rowDelta = end.row - start.row
        val columnDelta = end.column - start.column
        val crossings = buildList {
            add(0.0)
            add(1.0)
            for (boundary in 0..size) {
                if (rowDelta != 0.0) add((boundary - start.row) / rowDelta)
                if (columnDelta != 0.0) add((boundary - start.column) / columnDelta)
            }
        }.filter { it in 0.0..1.0 }.distinct().sorted()
        // Sample inside each crossed cell; a diagonal touching a corner does not enter its neighbours.
        crossings.zipWithNext().mapNotNull { (from, to) ->
            val midpoint = (from + to) / 2
            GridPoint(start.row + rowDelta * midpoint, start.column + columnDelta * midpoint)
                .cellPositionOrNull(size)
        }
    }.fold(mutableListOf()) { path, position ->
        if (path.lastOrNull() != position) path.add(position)
        path
    }
