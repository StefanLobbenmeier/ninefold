package de.ninefold

internal fun CheckingSettings.rulesResult(puzzle: Puzzle, cells: List<CellState>, manual: Boolean = false): PuzzleCheckResult =
    checkingResult(rules, automaticRules || manual, automaticRulePencilmarks || manual) {
        checkSudokuRules(puzzle, cells, specialRules)
    }

internal fun CheckingSettings.solutionResult(puzzle: Puzzle, cells: List<CellState>, manual: Boolean = false): PuzzleCheckResult =
    checkingResult(solution, automaticSolution || manual, automaticSolutionPencilmarks || manual) {
        checkAgainstSolution(puzzle, cells)
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
