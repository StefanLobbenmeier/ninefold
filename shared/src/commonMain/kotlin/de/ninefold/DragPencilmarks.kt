package de.ninefold

import kotlin.math.abs

/** Walk the drag in pointer order. Each newly selected cell gets one chance to change. */
internal fun clearDraggedPencilmarks(
    puzzle: Puzzle,
    cells: List<CellState>,
    path: List<CellPosition>,
    added: Set<CellPosition>,
    targets: Set<CellPosition> = added,
    lit: Set<CellPosition> = puzzle.litCells(cells),
): List<CellState> {
    if (added.size < 2) return cells

    val next = cells.toMutableList()
    fun cell(position: CellPosition) = next[position.index(puzzle.size)]

    val regions = puzzle.regionCells()
    val units = regions + (0 until puzzle.size).flatMap { index ->
        listOf(
            (0 until puzzle.size).mapTo(mutableSetOf()) { CellPosition(index, it) },
            (0 until puzzle.size).mapTo(mutableSetOf()) { CellPosition(it, index) },
        )
    }
    val slowThermoBulbs = puzzle.importedSlowThermometerBulbs()
    val lines = puzzle.lines.mapNotNull { line ->
        when {
            line.rule.isThermometer ->
                line.rule to line.points.mapNotNull { it.cellPositionOrNull(puzzle.size) }.distinct()
            line.rule == LineRule.GermanWhisper ->
                line.rule to gridPathCells(line.points, puzzle.size)
            line.rule == LineRule.Unspecified && slowThermoBulbs.isNotEmpty() ->
                line.slowThermometerPath(slowThermoBulbs, puzzle.size)?.let { LineRule.SlowThermometer to it }
            else -> null
        }
    }
    val sums = puzzle.overlays.mapNotNull { overlay ->
        overlay.edgeSum()?.let { overlay.edgeMarkCells(puzzle.size).toSet() to it }
    }.toMap()
    val previous = mutableListOf<CellPosition>()
    val visited = mutableSetOf<CellPosition>()
    for (target in path) {
        if (target !in added || target !in lit) {
            previous.clear()
            continue
        }
        val old = cell(target)
        if (visited.add(target) && target in targets && !old.given && old.value == null) {
            val excluded = buildSet {
                for (unit in units) {
                    if (target !in unit) continue
                    val sources = previous.asReversed().takeWhile { it in unit }.distinct()
                    addAll(sources.mapNotNull { cell(it).value })
                    val pairs = sources.map(::cell).filter { it.value == null && it.centreNotes.size == 2 }
                        .groupingBy { it.centreNotes }.eachCount()
                    for ((digits, count) in pairs) if (count == 2) addAll(digits)
                }
            }
            val predecessor = previous.lastOrNull()
            val complement = predecessor?.let { source ->
                val sum = sums[setOf(source, target)]
                if (sum == null) null else cell(source).value?.let { sum - it }
            }
            val lineFilters = lines.mapNotNull { (rule, positions) ->
                if (predecessor == null) return@mapNotNull null
                if (rule == LineRule.GermanWhisper) {
                    if (positions.zipWithNext().none { (a, b) ->
                            a == predecessor && b == target || b == predecessor && a == target
                        }) return@mapNotNull null
                    val source = cell(predecessor)
                    val candidates = source.value?.let(::setOf) ?: source.centreNotes
                    if (candidates.isEmpty() || candidates.any { it !in 1..puzzle.size }) return@mapNotNull null
                    return@mapNotNull { digit: Int -> candidates.any { abs(digit - it) >= 5 } }
                }

                // Use only the part of the thermometer traversed in this direction.
                val sourceIndex = positions.indexOf(predecessor)
                val targetIndex = positions.indexOf(target)
                val step = targetIndex - sourceIndex
                if (sourceIndex < 0 || targetIndex < 0 || abs(step) != 1) return@mapNotNull null
                val run = buildList {
                    add(target)
                    for ((offset, position) in previous.asReversed().withIndex()) {
                        if (position != positions.getOrNull(sourceIndex - offset * step)) break
                        add(position)
                    }
                }
                val rises = thermometerRises(run, rule, regions)
                val bounds = run.drop(1).mapIndexedNotNull { offset, position ->
                    val state = cell(position)
                    val candidates = state.value?.let(::setOf) ?: state.centreNotes
                    if (candidates.isEmpty() || candidates.any { it !in 1..puzzle.size }) null
                    else if (step > 0) candidates.min() + rises[offset + 1]
                    else candidates.max() - rises[offset + 1]
                }
                if (bounds.isEmpty()) null
                else if (step > 0) ({ digit: Int -> digit >= bounds.max() })
                else ({ digit: Int -> digit <= bounds.min() })
            }
            fun allowed(digit: Int): Boolean = digit !in excluded && lineFilters.all { it(digit) }
            val centre = old.centreNotes.filterTo(mutableSetOf(), ::allowed)
            val corner = old.cornerNotes.filterTo(mutableSetOf(), ::allowed)
            val replacement = when {
                complement != null && complement in 1..puzzle.size && allowed(complement) &&
                    (old.centreNotes.isEmpty() || complement in old.centreNotes) ->
                    old.copy(value = complement)
                complement != null -> old
                old.centreNotes.isNotEmpty() && centre.isEmpty() -> old
                centre.size == 1 && centre != old.centreNotes ->
                    old.copy(value = centre.single(), centreNotes = centre, cornerNotes = corner)
                else -> old.copy(centreNotes = centre, cornerNotes = corner)
            }
            next[target.index(puzzle.size)] = replacement
        }
        previous.add(target)
    }
    return next
}

private val slowThermometerInstruction = Regex(
    """(?:Normal sudoku rules apply\.\s*)?Slow thermo(?:meters?)?:\s*digits along each thermometer shape must either increase or stay the same at each step, from the round bulb to the tip\.""",
    RegexOption.IGNORE_CASE,
)

// SudokuPad can export thermometers as line and circle artwork, including in already saved puzzles.
private fun Puzzle.importedSlowThermometerBulbs(): List<PuzzleOverlay> {
    if (!slowThermometerInstruction.matches(rules.joinToString(" ").trim())) return emptyList()
    return (overlays + underlays).filter {
        it.rounded && it.text.isNullOrBlank() && it.width == it.height && it.width in .5..1.0
    }
}

private fun PuzzleLine.slowThermometerPath(bulbs: List<PuzzleOverlay>, size: Int): List<CellPosition>? {
    if (points.any {
        val cell = it.cellPositionOrNull(size)
        cell == null || it != GridPoint(cell.row + .5, cell.column + .5)
    }) return null
    if (points.zipWithNext().any { (a, b) ->
        val rows = abs(a.row - b.row)
        val columns = abs(a.column - b.column)
        rows != 0.0 && columns != 0.0 && rows != columns
    }) return null
    val path = gridPathCells(points, size)
    if (path.size < 2 || path.distinct().size != path.size) return null
    val bulb = bulbs.singleOrNull {
        it.backgroundColor.equals(color, ignoreCase = true) && it.width > thickness / 50.0 &&
            it.center.cellPositionOrNull(size) in path
    }?.center
    return when (bulb) {
        points.first() -> path
        points.last() -> path.reversed()
        else -> null
    }
}
