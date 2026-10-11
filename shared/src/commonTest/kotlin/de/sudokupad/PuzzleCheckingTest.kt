package de.sudokupad

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PuzzleCheckingTest {
    private val solution = "534678912672195348198342567859761423426853791713924856961537284287419635345286179"

    @Test
    fun centreNotesPassSolutionCheckWhenTheyContainTheAnswer() {
        val puzzle = Puzzle("", "", ".".repeat(81), solution)
        val cells = puzzle.startingCells().toMutableList().also {
            it[0] = CellState(centreNotes = setOf(2, 5, 8))
            it[1] = CellState(centreNotes = setOf(1, 2, 9))
        }

        val result = checkAgainstSolution(puzzle, cells)

        assertFalse(0 in result.pencilMarks)
        assertEquals(setOf(1, 2, 9), result.pencilMarks[1])
    }

    @Test
    fun cornerNotesAreCheckedIndividuallyAgainstSolutionPosition() {
        val puzzle = Puzzle("", "", ".".repeat(81), solution)
        val cells = puzzle.startingCells().toMutableList().also {
            it[0] = CellState(cornerNotes = setOf(3, 5, 7))
        }

        assertEquals(setOf(3, 7), checkAgainstSolution(puzzle, cells).pencilMarks[0])
    }

    @Test
    fun ruleCheckMarksOnlyCandidatesBlockedByFixedPeers() {
        val puzzle = Puzzle("", "", ".".repeat(81))
        val cells = puzzle.startingCells().toMutableList().also {
            it[0] = CellState(value = 5)
            it[1] = CellState(cornerNotes = setOf(4, 5, 6))
            it[9] = CellState(centreNotes = setOf(2, 5, 8))
        }

        val result = checkSudokuRules(puzzle, cells, includeSpecialRules = false)

        assertEquals(setOf(5), result.pencilMarks[1])
        assertEquals(setOf(5), result.pencilMarks[9])
    }

    @Test
    fun specialRuleChecksCanBeIncludedOrExcluded() {
        val cage = KillerCage(6, setOf(CellPosition(0, 0), CellPosition(0, 1)))
        val puzzle = Puzzle("", "", ".".repeat(81), cages = listOf(cage))
        val cells = puzzle.startingCells().toMutableList().also {
            it[0] = CellState(value = 4)
            it[1] = CellState(centreNotes = setOf(2, 3, 4))
        }

        assertFalse(3 in checkSudokuRules(puzzle, cells, includeSpecialRules = false).pencilMarks[1].orEmpty())
        val withSpecialRules = checkSudokuRules(puzzle, cells, includeSpecialRules = true)
        assertTrue(3 in withSpecialRules.pencilMarks[1].orEmpty())
        assertTrue(4 in withSpecialRules.pencilMarks[1].orEmpty())
        assertFalse(2 in withSpecialRules.pencilMarks[1].orEmpty())
    }

    @Test fun checksAssignedLinesAndSumArrows() {
        val line = PuzzleLine(listOf(GridPoint(.5, .5), GridPoint(.5, 1.5)), rule = LineRule.GermanWhisper)
        val arrow = PuzzleArrow(emptyList(), circle = CellPosition(1, 0), pathCells = listOf(CellPosition(1, 1), CellPosition(1, 2)))
        val puzzle = Puzzle("", "", ".".repeat(81), lines = listOf(line), arrows = listOf(arrow))
        val cells = puzzle.startingCells().toMutableList().also {
            it[0] = CellState(value = 1); it[1] = CellState(value = 4)
            it[9] = CellState(value = 4); it[10] = CellState(value = 1); it[11] = CellState(value = 2)
        }
        val conflicts = checkSudokuRules(puzzle, cells).cells
        assertTrue(0 in conflicts && 1 in conflicts)
        assertTrue(9 in conflicts && 10 in conflicts && 11 in conflicts)
    }

    @Test fun checksTrivialOutsideClues() {
        val skyscraper = OutsideClue(8, GridPoint(-.5, .5), List(9) { CellPosition(it, 0) }, OutsideClueType.Skyscraper)
        val puzzle = Puzzle("", "", ".".repeat(81), outsideClues = listOf(skyscraper))
        val cells = puzzle.startingCells().toMutableList().also { states -> (1..9).forEachIndexed { row, value -> states[row * 9] = CellState(value = value) } }
        assertTrue((0..8).all { it * 9 in checkSudokuRules(puzzle, cells).cells })
    }
}
