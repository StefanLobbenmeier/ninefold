package de.ninefold

import kotlinx.serialization.Serializable

/** Symbols 0–9 are digits; 10–35 represent A–Z in saved values and notes. */
internal fun Int.symbolText(): String = if (this in 10..35) ('A' + (this - 10)).toString() else toString()
internal fun Int.isDigitSymbol(): Boolean = this in 0..9

@Serializable
data class CellPosition(val row: Int, val column: Int) {
    /** Classic-grid convenience for image recognition and existing callers. */
    val index: Int get() = index(9)
    fun index(size: Int): Int = row * size + column
}
@Serializable
data class CellState(val value: Int? = null, val given: Boolean = false, val cornerNotes: Set<Int> = emptySet(), val centreNotes: Set<Int> = emptySet(), val colours: Set<Int> = emptySet())
/** A null total preserves an unlabelled cage; its sum may be specified only in the rules. */
@Serializable
data class KillerCage(
    val total: Int?, val cells: Set<CellPosition>,
    val unique: Boolean = true,
    /** Sum stated in prose, without adding a printed clue to the artwork. */
    val ruleTotal: Int? = null,
    /** Hidden source cages still enforce constraints, but have no border or printed total. */
    val hidden: Boolean = false,
)
@Serializable
data class FogLink(val triggers: Set<CellPosition>, val cells: Set<CellPosition>)
@Serializable
data class GridPoint(val row: Double, val column: Double)

enum class LineRule(val label: String) {
    Unspecified("Display only"), GermanWhisper("German whisper (difference ≥ 5)"),
    DutchWhisper("Dutch whisper (difference ≥ 4)"), Renban("Renban"),
    Palindrome("Palindrome"), Thermometer("Thermometer"), RegionSum("Region sum"),
    SlowThermometer("Slow thermometer"),
}

internal val LineRule.isThermometer: Boolean
    get() = this == LineRule.Thermometer || this == LineRule.SlowThermometer

@Serializable
data class PuzzleLine(
    val points: List<GridPoint>, val color: String = "#A0A0A0", val thickness: Double = 6.0,
    val rule: LineRule = LineRule.Unspecified,
    val drawThermometerBulb: Boolean = true,
)
@Serializable
data class PuzzleArrow(
    val points: List<GridPoint>, val color: String = "#777777", val thickness: Double = 3.0,
    val headLength: Double = .3, val circle: CellPosition? = null, val pathCells: List<CellPosition> = emptyList(),
)
@Serializable
data class PuzzleOverlay(
    val center: GridPoint, val width: Double = .3, val height: Double = .3,
    val backgroundColor: String? = null, val borderColor: String? = null, val textColor: String? = null,
    val text: String? = null, val rounded: Boolean = false, val thickness: Double = 1.0,
    val fontSize: Double? = null, // Cell units, independent of the backing shape.
)
@Serializable
data class QuadrupleClue(val digits: List<Int>, val cells: List<CellPosition>)

enum class OutsideClueType { Unspecified, Skyscraper, DiagonalSum }
@Serializable
data class OutsideClue(val value: Int, val position: GridPoint, val cells: List<CellPosition>, val type: OutsideClueType)

data class PuzzleCheckResult(
    val cells: Set<Int> = emptySet(),
    val pencilMarks: Map<Int, Set<Int>> = emptyMap(),
) {
    val issueCount: Int get() = cells.size + pencilMarks.values.sumOf { it.size }

    operator fun plus(other: PuzzleCheckResult): PuzzleCheckResult {
        val mergedMarks = (pencilMarks.keys + other.pencilMarks.keys).associateWith { index ->
            pencilMarks[index].orEmpty() + other.pencilMarks[index].orEmpty()
        }
        return PuzzleCheckResult(cells + other.cells, mergedMarks)
    }
}

@Serializable
data class Puzzle(
    val title: String, val author: String, val givens: String, val solution: String? = null,
    val cages: List<KillerCage> = emptyList(), val rules: List<String> = listOf("Normal sudoku rules apply."),
    val lines: List<PuzzleLine> = emptyList(), val arrows: List<PuzzleArrow> = emptyList(),
    val overlays: List<PuzzleOverlay> = emptyList(), val underlays: List<PuzzleOverlay> = emptyList(),
    val outsideClues: List<OutsideClue> = emptyList(),
    val quadruples: List<QuadrupleClue> = emptyList(),
    /** Present (even when empty) when this is a fog puzzle. */
    val fogLights: Set<CellPosition>? = null,
    /** Null uses regular 3×3 fog; a present list uses only the specified reveals. */
    val fogLinks: List<FogLink>? = null, val sourceUrl: String? = null, val videoUrl: String? = null, val videoTitle: String? = null,
    val antiKnight: Boolean = false, val antiKing: Boolean = false, val successMessage: String? = null,
    /** Editable digits supplied by an importer, separate from fixed clues. */
    val initialValues: Map<Int, Int> = emptyMap(),
    val size: Int = 9,
    /** Null uses regular boxes; an empty list explicitly disables region constraints. */
    val regions: List<Set<CellPosition>>? = null,
    val importOmissions: List<ImportOmission> = emptyList(),
) {
    init { require(size in 1..9) { "Grid size must be between 1 and 9." } }
    val cellCount: Int get() = size * size
    val boxHeight: Int get() = (1..kotlin.math.sqrt(size.toDouble()).toInt()).last { size % it == 0 }
    val boxWidth: Int get() = size / boxHeight
    fun regionCells(): List<Set<CellPosition>> = regions ?: (0 until size step boxHeight).flatMap { row ->
        (0 until size step boxWidth).map { column ->
            (row until row + boxHeight).flatMap { r -> (column until column + boxWidth).map { c -> CellPosition(r, c) } }.toSet()
        }
    }
    fun regionIndex(position: CellPosition): Int = regionCells().indexOfFirst { position in it }
    fun startingCells(): List<CellState> = givens.mapIndexed { index, char ->
        val value = char.digitToIntOrNull()
        CellState(value = value ?: initialValues[index]?.takeIf { it in 0..9 }, given = value != null)
    }.let { it + List((cellCount - it.size).coerceAtLeast(0)) { CellState() } }.take(cellCount)
}

fun GridPoint.cellPositionOrNull(size: Int = 9): CellPosition? {
    val rowIndex = kotlin.math.floor(row).toInt()
    val columnIndex = kotlin.math.floor(column).toInt()
    return CellPosition(rowIndex, columnIndex).takeIf { rowIndex in 0 until size && columnIndex in 0 until size }
}

fun checkAgainstSolution(puzzle: Puzzle, cells: List<CellState>): PuzzleCheckResult {
    val solution = puzzle.solution ?: return PuzzleCheckResult()
    val size = puzzle.size
    val conflicts = mutableSetOf<Int>()
    val pencilMarks = mutableMapOf<Int, Set<Int>>()
    fun block(index: Int): Int = puzzle.regionIndex(CellPosition(index / size, index % size))
    // Corner marks describe possible positions for a digit within a block.
    // Accept the whole group when it includes the answer, without revealing its position.
    val validCornerDigits = cells.indices.groupBy(::block).mapValues { (_, indices) ->
        indices.mapNotNull { index ->
            solution.getOrNull(index)?.digitToIntOrNull()?.takeIf { cells[index].value == null && it in cells[index].cornerNotes }
        }.toSet()
    }
    cells.forEachIndexed { index, cell ->
        val answer = solution.getOrNull(index)?.digitToIntOrNull() ?: return@forEachIndexed
        if (!cell.given && cell.value?.isDigitSymbol() == true && answer != cell.value) conflicts += index
        if (cell.value == null && cell.cornerNotes.isNotEmpty()) {
            val invalid = cell.cornerNotes.filterTo(mutableSetOf(), Int::isDigitSymbol) - validCornerDigits[block(index)].orEmpty()
            if (invalid.isNotEmpty()) pencilMarks[index] = invalid
        }
        if (cell.value == null && cell.centreNotes.any(Int::isDigitSymbol) && answer !in cell.centreNotes) {
            pencilMarks[index] = pencilMarks[index].orEmpty() + cell.centreNotes.filter(Int::isDigitSymbol)
        }
    }
    return PuzzleCheckResult(conflicts, pencilMarks)
}

fun conflictsFor(puzzle: Puzzle, cells: List<CellState>): Set<Int> =
    (checkSudokuRules(puzzle, cells) + checkAgainstSolution(puzzle, cells)).cells

fun parseClassicGrid(input: String): String? {
    val grid = input.filter { it.isDigit() || it == '.' }.map { if (it == '0') '.' else it }.joinToString("")
    return grid.takeIf { it.length == 81 && it.all { char -> char == '.' || char in '1'..'9' } }
}

val DemoPuzzle = Puzzle(
    title = "After Hours", author = "Studio Grid",
    givens = "53..7....6..195....98....6.8...6...34..8.3..17...2...6.6....28....419..5....8..79",
    solution = "534678912672195348198342567859761423426853791713924856961537284287419635345286179",
    cages = listOf(
        KillerCage(8, setOf(CellPosition(0, 0), CellPosition(0, 1))),
        KillerCage(6, setOf(CellPosition(0, 2), CellPosition(1, 2))),
        KillerCage(7, setOf(CellPosition(1, 0), CellPosition(2, 0))),
        KillerCage(15, setOf(CellPosition(7, 7), CellPosition(7, 8), CellPosition(8, 8))),
    ),
    rules = listOf("Place 1–9 once in every row, column, and 3×3 box.", "Digits in a dotted cage sum to the small clue and cannot repeat."),
)

internal fun gridSize(cells: List<CellState>): Int = kotlin.math.sqrt(cells.size.toDouble()).toInt()
