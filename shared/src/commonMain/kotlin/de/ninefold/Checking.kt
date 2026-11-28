package de.ninefold

internal fun CheckingSettings.rulesResult(puzzle: Puzzle, cells: List<CellState>, manual: Boolean = false): PuzzleCheckResult =
    checkingResult(rules, automaticRules || manual, automaticRulePencilmarks || manual) {
        checkSudokuRules(puzzle, cells, specialRules)
    }

internal fun CheckingSettings.solutionResult(
    puzzle: Puzzle,
    cells: List<CellState>,
    manual: Boolean = false,
    editing: Set<CellPosition> = emptySet(),
): PuzzleCheckResult =
    checkingResult(solution, automaticSolution || manual, automaticSolutionPencilmarks || manual) {
        // Selected cells still contribute to the region's corner candidates.
        val result = checkAgainstSolution(puzzle, cells)
        val editingIndices = editing.map { it.index(puzzle.size) }.toSet()
        result.copy(
            cells = result.cells - editingIndices,
            pencilMarks = result.pencilMarks.filterKeys { it !in editingIndices },
        )
    }

private inline fun checkingResult(
    enabled: Boolean, digits: Boolean, pencilmarks: Boolean, check: () -> PuzzleCheckResult,
): PuzzleCheckResult {
    if (!enabled || (!digits && !pencilmarks)) return PuzzleCheckResult()
    val result = check()
    return PuzzleCheckResult(
        cells = if (digits) result.cells else emptySet(),
        pencilMarks = if (pencilmarks) result.pencilMarks else emptyMap(),
    )
}
