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
    @Test fun edgeMarksRevealFromEitherSideOnly() {
        for (text in listOf("V", "X", null)) {
            for (horizontal in listOf(true, false)) {
                val mark = PuzzleOverlay(
                    if (horizontal) GridPoint(4.0, 4.5) else GridPoint(4.5, 4.0),
                    text = text, rounded = text == null, backgroundColor = "#FFFFFF",
                )
                val neighbors = if (horizontal) listOf(CellPosition(3, 4), CellPosition(4, 4))
                    else listOf(CellPosition(4, 3), CellPosition(4, 4))
                assertFalse(mark.isRevealedEdgeMark(emptySet()))
                assertFalse(mark.isRevealedEdgeMark(setOf(CellPosition(0, 0))))
                neighbors.forEach { assertTrue(mark.isRevealedEdgeMark(setOf(it))) }
                assertTrue(mark.isRevealedEdgeMark(neighbors.toSet()))
            }
        }
    }

    @Test fun sudokuPadTextBoxDimensionsRevealXVFromEitherSide() {
        for (text in listOf("v", "x")) {
            val mark = PuzzleOverlay(GridPoint(5.5, 1.0), width = 0.0, height = 1.05, text = text)
            assertTrue(mark.isRevealedEdgeMark(setOf(CellPosition(5, 0))))
            assertTrue(mark.isRevealedEdgeMark(setOf(CellPosition(5, 1))))
            assertFalse(mark.isRevealedEdgeMark(emptySet()))
            assertFalse(mark.isRevealedEdgeMark(setOf(CellPosition(4, 0))))
        }
    }

    @Test fun otherArtworkDoesNotRevealAcrossFog() {
        val lit = (0..8).flatMap { row -> (0..8).map { CellPosition(row, it) } }.toSet()
        val dot = PuzzleOverlay(GridPoint(4.0, 4.5), rounded = true, backgroundColor = "#FFFFFF")
        assertFalse(dot.copy(center = GridPoint(4.5, 4.5)).isRevealedEdgeMark(lit))
        assertFalse(dot.copy(center = GridPoint(4.0, 4.0)).isRevealedEdgeMark(lit))
        assertFalse(dot.copy(center = GridPoint(0.0, 4.5)).isRevealedEdgeMark(lit))
        assertFalse(dot.copy(width = .8, height = .8).isRevealedEdgeMark(lit))
        assertFalse(dot.copy(text = "42").isRevealedEdgeMark(lit))
        assertFalse(dot.copy(rounded = false).isRevealedEdgeMark(lit))
    }

}
