package de.sudokupad

/** Board coordinates are normalized to 0..1; outside clues never select a cell. */
internal fun boardCellAt(x: Float, y: Float): CellPosition? {
    if (!x.isFinite() || !y.isFinite() || x < 0f || y < 0f || x >= 1f || y >= 1f) return null
    return CellPosition((y * 9).toInt(), (x * 9).toInt())
}

/** Include cells crossed between pointer events, even during a fast sweep. */
internal fun boardCellsAlongSegment(startX: Float, startY: Float, endX: Float, endY: Float): Set<CellPosition> {
    val dx = endX - startX
    val dy = endY - startY
    val crossings = mutableListOf(0f, 1f)
    for (boundary in 0..9) {
        val edge = boundary / 9f
        if (dx != 0f) ((edge - startX) / dx).takeIf { it > 0f && it < 1f }?.let(crossings::add)
        if (dy != 0f) ((edge - startY) / dy).takeIf { it > 0f && it < 1f }?.let(crossings::add)
    }
    val stops = crossings.sorted()
    return buildSet {
        boardCellAt(startX, startY)?.let(::add)
        stops.zipWithNext { a, b ->
            val midpoint = (a + b) / 2f
            boardCellAt(startX + dx * midpoint, startY + dy * midpoint)?.let(::add)
        }
        boardCellAt(endX, endY)?.let(::add)
    }
}
