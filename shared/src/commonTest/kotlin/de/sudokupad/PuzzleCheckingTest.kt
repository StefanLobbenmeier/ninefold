package de.sudokupad

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PuzzleCheckingTest {

    @Test
    fun centreNotesPassSolutionCheckWhenTheyContainTheAnswer() {
        val puzzle = SudokuFixtures.empty.copy(solution = SudokuFixtures.classic.solution)
        val cells = puzzle.startingCells().toMutableList().also {
            it[0] = CellState(centreNotes = setOf(2, 5, 8))
            it[1] = CellState(centreNotes = setOf(1, 2, 9))
        }

        val result = checkAgainstSolution(puzzle, cells)

        assertFalse(0 in result.pencilMarks)
        assertEquals(setOf(1, 2, 9), result.pencilMarks[1])
    }

    @Test
    fun cornerNotesPassForEveryPlacementOfADigitWhenOneMatchesInTheBlock() {
        val puzzle = SudokuFixtures.empty.copy(solution = SudokuFixtures.classic.solution)
        val cells = puzzle.startingCells().toMutableList().also {
            it[0] = CellState(cornerNotes = setOf(3, 5, 7))
            it[1] = CellState(cornerNotes = setOf(3, 5, 7))
            it[10] = CellState(cornerNotes = setOf(3, 5, 7))
        }

        assertTrue(checkAgainstSolution(puzzle, cells).pencilMarks.isEmpty())
    }

    @Test
    fun cornerNotesFailForEveryPlacementOfADigitWhenNoneMatchesInTheBlock() {
        val puzzle = SudokuFixtures.empty.copy(solution = SudokuFixtures.classic.solution)
        val cells = puzzle.startingCells().toMutableList().also {
            it[0] = CellState(cornerNotes = setOf(3, 5, 7))
            it[9] = CellState(cornerNotes = setOf(3, 5, 7))
            it[19] = CellState(cornerNotes = setOf(3, 5, 7))
        }

        assertEquals(mapOf(0 to setOf(3, 7), 9 to setOf(3, 7), 19 to setOf(3, 7)),
            checkAgainstSolution(puzzle, cells).pencilMarks)
    }

    @Test
    fun matchingCornerNotesDoNotValidateAnotherBlockOrCentreNotes() {
        val puzzle = SudokuFixtures.empty.copy(solution = SudokuFixtures.classic.solution)
        val cells = puzzle.startingCells().toMutableList().also {
            it[0] = CellState(cornerNotes = setOf(5))
            it[1] = CellState(cornerNotes = setOf(5), centreNotes = setOf(2, 8))
            it[3] = CellState(cornerNotes = setOf(5))
            it[27] = CellState(cornerNotes = setOf(5))
            it[28] = CellState(centreNotes = setOf(5))
        }

        assertEquals(mapOf(1 to setOf(2, 8), 3 to setOf(5), 27 to setOf(5)),
            checkAgainstSolution(puzzle, cells).pencilMarks)
    }

    @Test
    fun ruleCheckMarksOnlyCandidatesBlockedByFixedPeers() {
        val puzzle = SudokuFixtures.empty
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
        val puzzle = SudokuFixtures.empty.copy(cages = listOf(cage))
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
        val puzzle = SudokuFixtures.empty.copy(lines = listOf(line), arrows = listOf(arrow))
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
        val puzzle = SudokuFixtures.empty.copy(outsideClues = listOf(skyscraper))
        val cells = puzzle.startingCells().toMutableList().also { states -> (1..9).forEachIndexed { row, value -> states[row * 9] = CellState(value = value) } }
        assertTrue((0..8).all { it * 9 in checkSudokuRules(puzzle, cells).cells })
    }
}
