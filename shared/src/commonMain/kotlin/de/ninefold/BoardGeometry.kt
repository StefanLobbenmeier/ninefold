package de.ninefold

import kotlin.math.floor
import kotlin.math.roundToInt

/** Equal-sized cells, with extra space reserved for the two heavier box dividers. */
internal class BoardAxis(val extent: Float, val boxGap: Float) {
    val cellSize = (extent - 2 * boxGap) / 9f
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
