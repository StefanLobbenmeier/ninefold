package de.sudokupad

data class CellPosition(val row: Int, val column: Int) { val index: Int get() = row * 9 + column }
data class CellState(val value: Int? = null, val given: Boolean = false, val cornerNotes: Set<Int> = emptySet(), val centreNotes: Set<Int> = emptySet())
data class KillerCage(val total: Int, val cells: Set<CellPosition>)

data class Puzzle(
    val title: String, val author: String, val givens: String, val solution: String? = null,
    val cages: List<KillerCage> = emptyList(), val rules: List<String> = listOf("Normal sudoku rules apply."),
) {
    fun startingCells(): List<CellState> = givens.map { char ->
        val value = char.digitToIntOrNull()?.takeIf { it in 1..9 }
        CellState(value = value, given = value != null)
    }.let { it + List((81 - it.size).coerceAtLeast(0)) { CellState() } }.take(81)
}

fun conflictsFor(puzzle: Puzzle, cells: List<CellState>): Set<Int> {
    val conflicts = mutableSetOf<Int>()
    fun markDuplicates(indices: List<Int>) {
        indices.mapNotNull { index -> cells[index].value?.let { it to index } }
            .groupBy({ it.first }, { it.second }).values.filter { it.size > 1 }.forEach(conflicts::addAll)
    }
    repeat(9) { unit -> markDuplicates(List(9) { unit * 9 + it }); markDuplicates(List(9) { it * 9 + unit }) }
    repeat(3) { br -> repeat(3) { bc -> markDuplicates(List(9) { (br * 3 + it / 3) * 9 + bc * 3 + it % 3 }) } }
    puzzle.cages.forEach { cage ->
        val indices = cage.cells.map(CellPosition::index); markDuplicates(indices)
        val values = indices.mapNotNull { cells[it].value }
        if (values.sum() > cage.total || (values.size == indices.size && values.sum() != cage.total)) conflicts.addAll(indices.filter { cells[it].value != null })
    }
    puzzle.solution?.let { solution -> cells.forEachIndexed { index, cell ->
        if (!cell.given && cell.value != null && solution.getOrNull(index)?.digitToIntOrNull() != cell.value) conflicts += index
    } }
    return conflicts
}

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
