package de.ninefold

import kotlin.math.floor
import kotlin.math.roundToInt

/** Symmetric margin in cell units, keeping the grid square and centered. */
internal fun Puzzle.boardPaddingCells(): Float {
    var outside = 0.0
    fun include(point: GridPoint, halfWidth: Double = 0.0, halfHeight: Double = halfWidth) {
        outside = maxOf(outside, halfWidth - point.column, point.column + halfWidth - 9,
            halfHeight - point.row, point.row + halfHeight - 9)
    }
    // Some exporters draw outside arrows entirely as decorative line segments.
    lines.forEach { line ->
        line.points.forEach { include(it, line.thickness / 100.0) }
        if (line.rule == LineRule.Thermometer) line.points.firstOrNull()?.let { include(it, .35) }
    }
    arrows.forEach { arrow ->
        arrow.points.forEach { include(it, arrow.thickness / 100.0) }
        if (arrow.points.size >= 2) {
            // The head can extend beyond the shaft in either axis.
            include(arrow.points.last(), arrow.headLength + arrow.thickness / 100.0)
        }
    }
    (overlays + underlays).forEach { include(it.center, it.width / 2, it.height / 2) }
    return if (outside > 0) maxOf(1.0, outside).toFloat() else 0f
}

/** Equal-sized cells, with extra space reserved for the two heavier box dividers. */
internal class BoardAxis(val extent: Float, boxGap: Float) {
    // Rotation can temporarily measure the board smaller than its dividers.
    val boxGap = boxGap.coerceIn(0f, extent.coerceAtLeast(0f) / 2f)
    val cellSize = (extent - 2 * this.boxGap).coerceAtLeast(0f) / 9f
    fun start(index: Int): Float = index * cellSize + (index / 3) * boxGap

    fun divider(index: Int): Float =
        ((start(index - 1) + cellSize).roundToInt() + start(index).roundToInt()) / 2f

    /** Grid points on a box boundary sit in the middle of its divider. */
    fun at(value: Double): Float {
        if (value <= 0.0) return (value * cellSize).toFloat()
        if (value >= 9.0) return extent + ((value - 9) * cellSize).toFloat()
        val index = floor(value).toInt()
        if (value == index.toDouble()) return divider(index)
        return start(index) + ((value - index) * cellSize).toFloat()
    }

    fun normalized(value: Float): Float {
        for (box in 0..1) {
            val edge = (box + 1) * 3
            val left = start(edge) - boxGap
            if (value in left..start(edge)) return edge / 9f
        }
        val gaps = (1..2).count { value > start(it * 3) }
        return (value - gaps * boxGap) / cellSize / 9f
    }
}

/** Outside text is actionable even when its rule is not understood by the importer. */
internal fun PuzzleOverlay.isOutsideTextClue(): Boolean = !text.isNullOrBlank() &&
    (center.row < 0 || center.row > 9 || center.column < 0 || center.column > 9)
