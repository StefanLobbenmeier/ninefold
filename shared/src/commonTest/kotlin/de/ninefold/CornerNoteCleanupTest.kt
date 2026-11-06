package de.ninefold

import kotlin.test.Test
import kotlin.test.assertEquals

class CornerNoteCleanupTest {
    private val puzzle = SudokuFixtures.empty
    private val pair = setOf(CellPosition(0, 0), CellPosition(0, 1))

    @Test fun centreInputWaitsForLeavingBeforeRemovingExcludedCorners() {
        val position = setOf(CellPosition(0, 0))
        val cells = puzzle.startingCells().toMutableList().also { it[0] = CellState(cornerNotes = setOf(3, 8)) }
        val firstDigit = applyInput(cells, position, InputMode.Centre, 2)
        assertEquals(setOf(3, 8), firstDigit[0].cornerNotes)
        val bothDigits = applyInput(firstDigit, position, InputMode.Centre, 8)
        assertEquals(setOf(3, 8), cleanCornerNotes(puzzle, bothDigits, emptySet(), position)[0].cornerNotes)
        assertEquals(setOf(8), cleanCornerNotes(puzzle, bothDigits, position, emptySet())[0].cornerNotes)
    }

    @Test fun pairRemovesSharedCornerReminderButLeavesOtherCellsAlone() {
        val cells = puzzle.startingCells().toMutableList().also {
            it[0] = CellState(centreNotes = setOf(2, 8), cornerNotes = setOf(3, 8))
            it[1] = CellState(centreNotes = setOf(2, 8), cornerNotes = setOf(8))
            it[2] = CellState(cornerNotes = setOf(8))
        }
        val cleaned = cleanCornerNotes(puzzle, cells, pair, emptySet())
        assertEquals(emptySet(), cleaned[0].cornerNotes)
        assertEquals(emptySet(), cleaned[1].cornerNotes)
        assertEquals(cells[2], cleaned[2])
    }

    @Test fun partiallyLeavingPairWaitsUntilLastCellLeaves() {
        val cells = puzzle.startingCells().toMutableList().also {
            pair.forEach { position -> it[position.index] = CellState(centreNotes = setOf(2, 8), cornerNotes = setOf(8)) }
        }
        val first = setOf(CellPosition(0, 0))
        val remaining = pair - first
        val partial = cleanCornerNotes(puzzle, cells, first, remaining)
        assertEquals(cells, partial)
        val cleaned = cleanCornerNotes(puzzle, partial, remaining, emptySet())
        pair.forEach { assertEquals(emptySet(), cleaned[it.index].cornerNotes) }
    }

    @Test fun tripletKeepsCornerReminderRestrictedToTwoCells() {
        val triplet = pair + CellPosition(0, 2)
        val cells = puzzle.startingCells().toMutableList().also {
            triplet.forEach { position ->
                it[position.index] = CellState(centreNotes = setOf(2, 6, 8), cornerNotes = if (position in pair) setOf(6, 8) else setOf(8))
            }
        }
        val cleaned = cleanCornerNotes(puzzle, cells, triplet, emptySet())
        pair.forEach { assertEquals(setOf(6), cleaned[it.index].cornerNotes) }
        assertEquals(emptySet(), cleaned[2].cornerNotes)
    }

    @Test fun matchingNotesInDifferentRegionsDoNotFormAPair() {
        val positions = setOf(CellPosition(0, 0), CellPosition(0, 3))
        val cells = puzzle.startingCells().toMutableList().also {
            positions.forEach { position -> it[position.index] = CellState(centreNotes = setOf(2, 8), cornerNotes = setOf(8)) }
        }
        assertEquals(cells, cleanCornerNotes(puzzle, cells, positions, emptySet()))
        val irregular = puzzle.copy(regions = listOf(positions))
        val cleaned = cleanCornerNotes(irregular, cells, positions, emptySet())
        positions.forEach { assertEquals(emptySet(), cleaned[it.index].cornerNotes) }
    }

    @Test fun emptyCentreNotesPreserveCornerNotes() {
        val cells = puzzle.startingCells().toMutableList().also { it[0] = CellState(cornerNotes = setOf(3)) }
        assertEquals(cells, cleanCornerNotes(puzzle, cells, setOf(CellPosition(0, 0)), emptySet()))
    }
}
