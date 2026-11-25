package de.ninefold

import kotlin.math.abs

/** Minimum rise from the first position to each later position, in either traversal direction. */
private fun thermometerRises(
    path: List<CellPosition>,
    rule: LineRule,
    regions: List<Set<CellPosition>>,
): List<Int> {
    if (rule == LineRule.Thermometer) return path.indices.toList()
    val rises = MutableList(path.size) { 0 }
    for (end in 1 until path.size) {
        val target = path[end]
        rises[end] = rises[end - 1]
        for (start in 0 until end) {
            val source = path[start]
            if (source != target && (source.row == target.row || source.column == target.column ||
                    regions.any { source in it && target in it })) {
                rises[end] = maxOf(rises[end], rises[start] + 1)
            }
        }
    }
    return rises
}

internal class DragRule(val rejects: (Int) -> Boolean, val canFill: Boolean)

/** A local rule evaluated against placed symbols or a hypothetical placement. Null means unknown. */
internal class SudokuRule(
    val cells: List<CellPosition>,
    private val allDifferent: Boolean = false,
    private val fillFromPlacedDigit: Boolean = false,
    private val useCentreNotes: Boolean = false,
    private val dragPath: List<CellPosition>? = null,
    private val numeric: Boolean = false,
    private val requiresVisibleGroup: Boolean = false,
    private val violations: (List<Int?>) -> List<Int>,
) {
    private val members = cells.toSet()

    fun conflicts(valueAt: (CellPosition) -> Int?): List<CellPosition> =
        violations(cells.map { position ->
            valueAt(position)?.takeIf { !numeric || it.isDigitSymbol() }
        }).map(cells::get)

    fun rulesOut(target: CellPosition, candidate: Int, valueAt: (CellPosition) -> Int?): Boolean =
        (!numeric || candidate.isDigitSymbol()) &&
            target in conflicts { if (it == target) candidate else valueAt(it) }

    /** Only the uninterrupted part of the gesture within this group can supply evidence. */
    private fun dragSources(target: CellPosition, previous: List<CellPosition>): List<CellPosition> {
        val path = dragPath ?: return previous.asReversed().takeWhile { it in members }.distinct()
        val traversal = if (target == path.last()) path else path.asReversed()
        val sources = traversal.dropLast(1)
        return if (previous.size >= sources.size && previous.takeLast(sources.size) == sources)
            listOf(traversal.first()) else emptyList()
    }

    fun duringDrag(
        target: CellPosition,
        previous: List<CellPosition>,
        lit: Set<CellPosition>,
        digits: IntRange,
        cellAt: (CellPosition) -> CellState,
    ): DragRule? {
        if (requiresVisibleGroup && !lit.containsAll(members)) return null
        val sources = dragSources(target, previous).associateWith(cellAt)
        if (sources.isEmpty()) return null

        if (useCentreNotes) {
            val (source, state) = sources.entries.single()
            val placed = state.value
            val candidates = placed?.let(::setOf) ?: state.centreNotes
            if (candidates.isEmpty() || candidates.any { it !in digits }) return null
            return DragRule(
                rejects = { digit ->
                    candidates.all { value -> rulesOut(target, digit) { if (it == source) value else null } }
                },
                canFill = fillFromPlacedDigit && placed != null,
            )
        }

        val pairDigits = if (allDifferent) {
            sources.values.filter { it.value == null && it.centreNotes.size == 2 }
                .groupingBy { it.centreNotes }.eachCount().filterValues { it == 2 }.keys.flatten().toSet()
        } else emptySet()
        return DragRule(
            rejects = { digit -> digit in pairDigits || rulesOut(target, digit) { sources[it]?.value } },
            canFill = false,
        )
    }
}

private fun distinctSymbols(cells: List<CellPosition>, requiresVisibleGroup: Boolean = false) =
    SudokuRule(cells, allDifferent = true, requiresVisibleGroup = requiresVisibleGroup) { values ->
        values.indices.filter { index -> values[index] != null && values.count { it == values[index] } > 1 }
    }

private fun numericPair(
    first: CellPosition,
    second: CellPosition,
    path: List<CellPosition>? = null,
    fillFromPlacedDigit: Boolean = false,
    compatible: (Int, Int) -> Boolean,
) = SudokuRule(
    listOf(first, second), numeric = true, dragPath = path,
    useCentreNotes = true, fillFromPlacedDigit = fillFromPlacedDigit,
) { (firstValue, secondValue) ->
    if (firstValue != null && secondValue != null && !compatible(firstValue, secondValue))
        listOf(0, 1) else emptyList()
}

private fun numericGroup(
    cells: List<CellPosition>,
    invalid: (List<Int?>) -> Boolean,
) = SudokuRule(cells, numeric = true, requiresVisibleGroup = true) { values ->
    if (invalid(values)) values.indices.filter { values[it] != null } else emptyList()
}

internal fun Puzzle.sudokuRules(includeSpecialRules: Boolean = true): List<SudokuRule> = buildList {
    val gridSize = this@sudokuRules.size
    val regions = regionCells()
    for (index in 0 until gridSize) {
        add(distinctSymbols(List(gridSize) { CellPosition(index, it) }))
        add(distinctSymbols(List(gridSize) { CellPosition(it, index) }))
    }
    regions.forEach { add(distinctSymbols(it.toList())) }
    if (!includeSpecialRules) return@buildList

    if (antiKnight || antiKing) {
        for (index in 0 until cellCount) {
            val source = CellPosition(index / gridSize, index % gridSize)
            for (rowOffset in 0..2) for (columnOffset in -2..2) {
                if (rowOffset == 0 && columnOffset <= 0) continue
                val knightMove = rowOffset * abs(columnOffset) == 2
                val kingMove = rowOffset <= 1 && abs(columnOffset) <= 1
                if (!(antiKnight && knightMove || antiKing && kingMove)) continue
                val target = CellPosition(source.row + rowOffset, source.column + columnOffset)
                if (target.row in 0 until gridSize && target.column in 0 until gridSize) {
                    add(distinctSymbols(listOf(source, target)))
                }
            }
        }
    }

    for (cage in cages) {
        val positions = cage.cells.toList()
        if (cage.unique) add(distinctSymbols(positions, requiresVisibleGroup = true))
        val total = cage.total ?: cage.ruleTotal ?: continue
        add(numericGroup(positions) { values ->
            val placed = values.filterNotNull()
            val sum = placed.sum()
            sum > total || placed.size == values.size && sum != total
        })
    }

    for (clue in quadruples) {
        val required = clue.digits.groupingBy { it }.eachCount()
        add(numericGroup(clue.cells) { values ->
            val missing = required.entries.sumOf { (digit, count) ->
                (count - values.count { it == digit }).coerceAtLeast(0)
            }
            missing > values.count { it == null }
        })
    }

    for (arrow in arrows) {
        val circle = arrow.circle ?: continue
        val shaft = arrow.pathCells.filter { it != circle }.distinct()
        if (shaft.isEmpty()) continue
        add(numericGroup(listOf(circle) + shaft) { values ->
            values.all { it != null } && values.drop(1).filterNotNull().sum() != values.first()
        })
    }

    for (line in lines) {
        val path = when (line.rule) {
            LineRule.GermanWhisper, LineRule.DutchWhisper, LineRule.RegionSum -> gridPathCells(line.points, gridSize)
            else -> line.points.mapNotNull { it.cellPositionOrNull(gridSize) }.distinct()
        }
        if (path.isEmpty()) continue
        when (line.rule) {
            LineRule.GermanWhisper, LineRule.DutchWhisper -> {
                val difference = if (line.rule == LineRule.GermanWhisper) 5 else 4
                for ((first, second) in path.zipWithNext()) {
                    add(numericPair(first, second) { a, b -> abs(a - b) >= difference })
                }
            }
            LineRule.Thermometer, LineRule.SlowThermometer -> {
                val rises = path.indices.map { thermometerRises(path.drop(it), line.rule, regions) }
                for ((start, position) in path.withIndex()) {
                    val bounds = (1 + rises[0][start])..(gridSize - rises[start].last())
                    add(numericGroup(listOf(position)) { values ->
                        values.single()?.let { it !in bounds } == true
                    })
                    for (end in start + 1 until path.size) {
                        val rise = rises[start][end - start]
                        add(numericPair(position, path[end], path.subList(start, end + 1)) { a, b -> b - a >= rise })
                    }
                }
            }
            LineRule.Palindrome -> for (start in 0 until path.size / 2) {
                val end = path.lastIndex - start
                add(numericPair(path[start], path[end], path.subList(start, end + 1)) { a, b -> a == b })
            }
            LineRule.Renban -> add(numericGroup(path) { values ->
                val digits = values.filterNotNull()
                digits.toSet().size != digits.size ||
                    (digits.isNotEmpty() && digits.max() - digits.min() >= path.size)
            })
            LineRule.RegionSum -> {
                val regionIndices = path.map { position -> regions.indexOfFirst { position in it } }
                if (regionIndices.any { it < 0 }) continue
                val starts = listOf(0) + (1 until path.size).filter { regionIndices[it] != regionIndices[it - 1] }
                val segments = (starts + path.size).zipWithNext { start, end -> start until end }
                add(SudokuRule(path, numeric = true, requiresVisibleGroup = true) { values ->
                    val completed = segments.mapNotNull { segment ->
                        val digits = segment.mapNotNull(values::get)
                        if (digits.size == segment.count()) segment to digits.sum() else null
                    }
                    if (completed.map { it.second }.distinct().size > 1) completed.flatMap { it.first }
                    else emptyList()
                })
            }
            LineRule.Unspecified -> Unit
        }
    }

    for (clue in outsideClues) {
        if (clue.type == OutsideClueType.Unspecified) continue
        add(numericGroup(clue.cells) { values ->
            if (values.any { it == null }) false else {
                val digits = values.filterNotNull()
                when (clue.type) {
                    OutsideClueType.DiagonalSum -> digits.sum() != clue.value
                    OutsideClueType.Skyscraper -> {
                        var tallest = 0
                        val visible = digits.count { digit ->
                            (digit > tallest).also { if (it) tallest = digit }
                        }
                        visible != clue.value
                    }
                    OutsideClueType.Unspecified -> false
                }
            }
        })
    }

    for (overlay in overlays) {
        val pair = overlay.edgeMarkCells(gridSize)
        if (pair.size != 2) continue
        val (first, second) = pair
        val sum = overlay.edgeSum()
        if (sum != null) {
            add(numericPair(first, second, fillFromPlacedDigit = true) { a, b -> a + b == sum })
        } else if (overlay.isKropkiDot()) {
            when (overlay.backgroundColor?.removePrefix("#")?.uppercase()) {
                "FFF", "FFFFFF", "FFFF", "FFFFFFFF" ->
                    add(numericPair(first, second) { a, b -> abs(a - b) == 1 })
                "000", "000000", "000F", "000000FF" ->
                    add(numericPair(first, second) { a, b -> a == 2 * b || b == 2 * a })
            }
        }
    }
}

fun checkSudokuRules(puzzle: Puzzle, cells: List<CellState>, includeSpecialRules: Boolean = true): PuzzleCheckResult {
    val size = puzzle.size
    val conflicts = mutableSetOf<Int>()
    val pencilMarks = mutableMapOf<Int, MutableSet<Int>>()
    fun valueAt(position: CellPosition) = cells.getOrNull(position.index(size))?.value
    for (rule in puzzle.sudokuRules(includeSpecialRules)) {
        rule.conflicts(::valueAt).mapTo(conflicts) { it.index(size) }
        for (position in rule.cells) {
            val index = position.index(size)
            val cell = cells.getOrNull(index) ?: continue
            if (cell.value != null) continue
            for (candidate in cell.cornerNotes + cell.centreNotes) {
                if (rule.rulesOut(position, candidate, ::valueAt)) {
                    pencilMarks.getOrPut(index, ::mutableSetOf).add(candidate)
                }
            }
        }
    }
    return PuzzleCheckResult(conflicts, pencilMarks)
}
