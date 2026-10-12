package de.sudokupad

/** Board coordinates are normalized to 0..1; outside clues never select a cell. */
internal fun boardCellAt(x: Float, y: Float): CellPosition? {
    if (!x.isFinite() || !y.isFinite() || x < 0f || y < 0f || x >= 1f || y >= 1f) return null
    return CellPosition((y * 9).toInt(), (x * 9).toInt())
}

/** Drag targets are centered half-size squares, leaving lanes between cells to dodge neighbors. */
internal fun boardCellsAlongSegment(startX: Float, startY: Float, endX: Float, endY: Float): Set<CellPosition> {
    if (!listOf(startX, startY, endX, endY).all { it.isFinite() }) return emptySet()
    val dx = endX - startX
    val dy = endY - startY
    return buildSet {
        for (row in 0..8) for (column in 0..8) {
            var enter = 0f
            var exit = 1f
            fun intersectsAxis(start: Float, delta: Float, min: Float, max: Float): Boolean {
                if (delta == 0f) return start in min..max
                val a = (min - start) / delta
                val b = (max - start) / delta
                enter = maxOf(enter, minOf(a, b))
                exit = minOf(exit, maxOf(a, b))
                return enter <= exit
            }
            // Intersect the whole segment so fast drags still include every crossed target.
            if (intersectsAxis(startX, dx, (column + .25f) / 9f, (column + .75f) / 9f) &&
                intersectsAxis(startY, dy, (row + .25f) / 9f, (row + .75f) / 9f)) {
                add(CellPosition(row, column))
            }
        }
    }
}
