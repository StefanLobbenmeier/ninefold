package de.sudokupad

import kotlin.test.*

class GamePlayTest {
    @Test fun clockExcludesBackgroundTimeAndPreservesPartialSeconds() {
        var now = 0L
        val clock = GameClock { now }
        clock.start()
        now = 1_500
        clock.setForeground(false)
        now = 61_500
        clock.start() // Recomposition while hidden must not restart the clock.
        assertEquals(1, clock.elapsedSeconds())
        clock.setForeground(true)
        clock.setForeground(true) // Repeated events must not reset the interval.
        now = 62_000
        assertEquals(2, clock.elapsedSeconds())
        clock.setForeground(false)
        clock.stop() // Completing a puzzle while hidden must prevent resumption.
        now = 100_000
        clock.setForeground(true)
        now = 110_000
        assertEquals(2, clock.elapsedSeconds())
    }

    @Test fun clockInitiallyHiddenWaitsUntilVisibleAndStarted() {
        var now = 0L
        val clock = GameClock { now }
        clock.restore(12)
        clock.setForeground(false)
        clock.start()
        now = 60_000
        assertEquals(12, clock.elapsedSeconds())
        clock.setForeground(true)
        now = 61_000
        assertEquals(13, clock.elapsedSeconds())
        clock.reset()
        clock.setForeground(false)
        clock.setForeground(true)
        now = 70_000
        assertEquals(0, clock.elapsedSeconds())
    }

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
            assertEquals(if (mode == InputMode.Corner) CellState(centreNotes = setOf(3)) else CellState(cornerNotes = setOf(3)), erased[2])
        }
    }
    @Test fun coloursToggleAcrossGivensDigitsAndNotesWithoutChangingThem() {
        val cells = List(81) { index -> when (index) {
            0 -> CellState(value = 5, given = true, colours = setOf(7))
            1 -> CellState(value = 3, colours = setOf(9))
            2 -> CellState(cornerNotes = setOf(1), centreNotes = setOf(2))
            else -> CellState()
        } }
        val selected = (0..2).map { CellPosition(0, it) }.toSet()
        val added = applyInput(cells, selected, InputMode.Colour, 7)
        selected.forEach { assertEquals(cells[it.index].copy(colours = cells[it.index].colours + 7), added[it.index]) }
        assertEquals(cells[3], added[3])
        val removed = applyInput(added, selected, InputMode.Colour, 7)
        assertEquals(emptySet(), removed[0].colours)
        assertEquals(setOf(9), removed[1].colours)
        val cleared = applyInput(added, selected, InputMode.Colour, null)
        selected.forEach { assertEquals(cells[it.index].copy(colours = emptySet()), cleared[it.index]) }
        assertEquals(cells, applyInput(cells, selected, InputMode.Colour, 10))
        assertEquals(cells, applyInput(cells, emptySet(), InputMode.Colour, 7))
    }

    @Test fun digitsAndNotesPreserveColourUntilNumbersAreErased() {
        val cells = List(81) { CellState(colours = setOf(4, 9)) }
        val selected = setOf(CellPosition(0, 0))
        for (mode in listOf(InputMode.Digit, InputMode.Corner, InputMode.Centre)) {
            val entered = applyInput(cells, selected, mode, 2)
            assertEquals(setOf(4, 9), entered[0].colours)
            val erased = applyInput(entered, selected, mode, null)
            assertEquals(cells[0], erased[0])
            assertEquals(CellState(), applyInput(erased, selected, mode, null)[0])
        }
    }

    @Test fun eraseClearsActiveCategoryThenOtherNumbersThenColours() {
        val selected = setOf(CellPosition(0, 0))
        val original = CellState(value = 5, cornerNotes = setOf(1, 2), centreNotes = setOf(3, 4), colours = setOf(7, 9))
        for (mode in listOf(InputMode.Digit, InputMode.Corner, InputMode.Centre)) {
            val cells = List(81) { if (it == 0) original else CellState() }
            val first = applyInput(cells, selected, mode, null)
            assertEquals(when (mode) {
                InputMode.Digit -> original.copy(value = null)
                InputMode.Corner -> original.copy(cornerNotes = emptySet())
                else -> original.copy(centreNotes = emptySet())
            }, first[0])
            val second = applyInput(first, selected, mode, null)
            assertEquals(CellState(colours = original.colours), second[0])
            val third = applyInput(second, selected, mode, null)
            assertEquals(CellState(), third[0])
            assertEquals(third, applyInput(third, selected, mode, null))
        }
    }

    @Test fun eraseFallsBackPerCellAndPreservesGivensAndUnselectedCells() {
        for (mode in listOf(InputMode.Digit, InputMode.Corner, InputMode.Centre)) {
            val active = when (mode) {
                InputMode.Digit -> CellState(value = 5)
                InputMode.Corner -> CellState(cornerNotes = setOf(1))
                else -> CellState(centreNotes = setOf(2))
            }.copy(colours = setOf(4))
            val other = when (mode) {
                InputMode.Digit -> CellState(cornerNotes = setOf(1), centreNotes = setOf(2))
                else -> CellState(value = 5)
            }.copy(colours = setOf(7))
            val cells = List(81) { index -> when (index) {
                0 -> active
                1 -> other
                2 -> CellState(colours = setOf(9))
                3 -> CellState(value = 3, given = true, colours = setOf(8))
                else -> active
            } }
            val erased = applyInput(cells, (0..3).map { CellPosition(0, it) }.toSet(), mode, null)
            assertEquals(CellState(colours = setOf(4)), erased[0])
            assertEquals(CellState(colours = setOf(7)), erased[1])
            assertEquals(CellState(), erased[2])
            assertEquals(cells[3], erased[3])
            assertEquals(cells.drop(4), erased.drop(4))
            assertEquals(cells, applyInput(cells, emptySet(), mode, null))
        }
    }

}
