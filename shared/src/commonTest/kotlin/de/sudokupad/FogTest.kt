package de.sudokupad

import kotlin.test.*

class FogTest {
    private val initial = CellPosition(0, 0)
    private val first = CellPosition(1, 1)
    private val second = CellPosition(1, 2)
    private val target = CellPosition(8, 8)
    private val puzzle = Puzzle("Fog", "Test", ".".repeat(81), solution = "123456789".repeat(9),
        fogLights = setOf(initial), fogLinks = listOf(FogLink(setOf(first, second), setOf(target))))

    @Test fun dynamicFogRequiresAllCorrectTriggersAndRefogsAfterUndo() {
        val cells = puzzle.startingCells().toMutableList()
        assertEquals(setOf(initial), puzzle.litCells(cells))
        cells[first.index] = CellState(value = 2)
        assertEquals(setOf(initial), puzzle.litCells(cells))
        cells[second.index] = CellState(value = 4)
        assertEquals(setOf(initial), puzzle.litCells(cells))
        val before = cells.toList()
        cells[second.index] = CellState(value = 3)
        assertEquals(setOf(initial, target), puzzle.litCells(cells))
        assertEquals(setOf(initial), puzzle.litCells(before))
        assertEquals(setOf(initial), puzzle.litCells(puzzle.startingCells()))
    }

    @Test fun dynamicFogDoesNotAutomaticallyRevealGivensOrNeighbors() {
        val cells = puzzle.startingCells().toMutableList()
        cells[40] = CellState(value = 5, given = true)
        cells[first.index] = CellState(value = 2)
        cells[second.index] = CellState(value = 3)
        assertEquals(setOf(initial, target), puzzle.litCells(cells))
        assertEquals(setOf(initial), puzzle.copy(fogLinks = emptyList()).litCells(cells))
    }

    @Test fun missingSolutionUsesFilledTriggersAndRegularFogKeepsItsBehavior() {
        val cells = puzzle.startingCells().toMutableList()
        cells[first.index] = CellState(value = 9)
        cells[second.index] = CellState(value = 8)
        assertEquals(setOf(initial, target), puzzle.copy(solution = null).litCells(cells))
        val regular = puzzle.copy(fogLinks = null)
        assertEquals(setOf(initial), regular.litCells(cells))
        cells[first.index] = CellState(value = 2)
        assertEquals((0..2).flatMap { row -> (0..2).map { CellPosition(row, it) } }.toSet(), regular.litCells(cells))
        assertEquals(81, puzzle.copy(fogLights = null, fogLinks = null).litCells(cells).size)
    }

    @Test fun dynamicFogSurvivesSharingAndSavedGames() {
        assertEquals(puzzle, puzzleFromShareLink(puzzleShareLink(puzzle)))
        val storage = MemoryAppStorage()
        val cells = puzzle.startingCells().toMutableList()
        cells[first.index] = CellState(value = 2)
        cells[second.index] = CellState(value = 3)
        GameRepository(storage).saveGame(SavedGame(puzzle, cells, true))
        val restored = GameRepository(storage).game(puzzle)!!
        assertEquals(setOf(initial, target), restored.puzzle.litCells(restored.cells))
        assertEquals(puzzle, restored.puzzle)
    }
}
