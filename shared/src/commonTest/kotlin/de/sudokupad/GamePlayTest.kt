package de.sudokupad

import kotlin.test.*

class GamePlayTest {
    @Test fun clockWaitsForStartAndFreezesAtCompletion() {
        var now = 0L
        val clock = GameClock { now }
        now = 5_000
        assertEquals(0, clock.elapsedSeconds())
        clock.start()
        now = 8_000
        clock.start() // Reopening rules must not restart the timer.
        assertEquals(3, clock.elapsedSeconds())
        clock.stop()
        now = 20_000
        assertEquals(3, clock.elapsedSeconds())
        clock.start() // Undoing the final digit resumes elapsed time.
        now = 22_000
        assertEquals(5, clock.elapsedSeconds())
        clock.reset()
        assertEquals(0, clock.elapsedSeconds())
    }

    @Test fun completionRequiresCorrectFinalDigitRegardlessOfCheckingSettings() {
        val puzzle = DemoPuzzle.copy(cages = emptyList())
        val solved = puzzle.solution!!.map { CellState(value = it.digitToInt()) }
        assertTrue(puzzle.isComplete(solved))
        assertFalse(puzzle.isComplete(solved.toMutableList().also { it[80] = CellState() }))
        assertFalse(puzzle.isComplete(solved.toMutableList().also { it[80] = CellState(value = 1) }))
        assertTrue(puzzle.copy(solution = null).isComplete(solved))
        assertFalse(puzzle.copy(solution = null).isComplete(List(81) { CellState(value = 1) }))
    }

    @Test fun multiCellNotesStayConsistentAndPreserveDigits() {
        val selected = (0..3).map { CellPosition(0, it) }.toSet()
        for (mode in listOf(InputMode.Corner, InputMode.Centre)) {
            val cells = List(81) { index -> when (index) {
                0 -> CellState(value = 5, given = true)
                1 -> CellState(value = 7)
                2 -> CellState(cornerNotes = setOf(3), centreNotes = setOf(3))
                else -> CellState()
            } }
            val added = applyInput(cells, selected, mode, 3)
            assertEquals(cells[0], added[0])
            assertEquals(cells[1], added[1])
            for (index in 2..3) assertTrue(3 in if (mode == InputMode.Corner) added[index].cornerNotes else added[index].centreNotes)
            val removed = applyInput(added, selected, mode, 3)
            for (index in 2..3) assertFalse(3 in if (mode == InputMode.Corner) removed[index].cornerNotes else removed[index].centreNotes)
            val erased = applyInput(added, selected, mode, null)
            assertEquals(cells[0], erased[0])
            assertEquals(CellState(), erased[2])
        }
    }
}
