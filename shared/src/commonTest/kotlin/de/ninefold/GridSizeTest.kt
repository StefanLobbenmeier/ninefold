package de.ninefold

import kotlin.test.*

class GridSizeTest {
    private val six = SudokuFixtures.empty.copy(size = 6, givens = ".".repeat(36))

    @Test fun importsSquareGridsAndSolutions() {
        for (size in listOf(4, 6, 9)) {
            val givens = "1" + ".".repeat(size * size - 2) + size.toString()
            val solution = (1..size).joinToString("").repeat(size)
            val puzzle = parseSudokuPadPayload(SudokuFixtures.sudokuPadPayload(givens, "\"solution\":\"$solution\""))
            assertEquals(size, puzzle.size)
            assertEquals(size * size, puzzle.startingCells().size)
            assertEquals(size, puzzle.startingCells().last().value)
            assertEquals(solution, puzzle.solution)
            assertEquals(puzzle, puzzleFromShareLink(puzzleShareLink(puzzle)))
        }
    }

    @Test fun preservesRegionsAndChecksRectangularBoxes() {
        val puzzle = parseSudokuPadPayload(SudokuFixtures.sudokuPadPayload(six.givens,
            "\"regions\":[[[0,0],[1,2]],[[0,1],[2,0]]]"))
        assertEquals(setOf(CellPosition(0, 0), CellPosition(1, 2)), puzzle.regions!!.first())
        val cells = six.startingCells().toMutableList()
        cells[0] = CellState(1)
        cells[8] = CellState(1)
        assertEquals(setOf(0, 8), checkSudokuRules(six, cells).cells)
        assertEquals(setOf(0, 8), checkSudokuRules(puzzle, cells).cells)
        cells[8] = CellState()
        cells[13] = CellState(1)
        assertTrue(checkSudokuRules(six, cells).cells.isEmpty())
    }

    @Test fun sixBySixCanBeCompleted() {
        val solution = "123456456123234561561234345612612345"
        val puzzle = six.copy(solution = solution)
        val cells = solution.map { CellState(it.digitToInt()) }
        assertTrue(puzzle.isComplete(cells))
        assertFalse(puzzle.isComplete(cells.dropLast(1)))
        assertFalse(puzzle.isComplete(cells.toMutableList().also { it[35] = CellState(7) }))
    }

    @Test fun selectionInputAndFogUseActualStride() {
        val position = CellPosition(5, 5)
        val cells = applyInput(six.startingCells(), setOf(position), InputMode.Digit, 6)
        assertEquals(6, cells[35].value)
        assertEquals(setOf(position), toolSensitiveSelection(cells, position, InputMode.Digit))
        assertEquals(position, boardCellAt(.99f, .99f, 6))
        assertNull(boardCellAt(1f, .5f, 6))
        assertEquals((0..5).map { CellPosition(5, it) }.toSet(), boardCellsAlongSegment(.01f, .92f, .99f, .92f, true, 6))
        assertEquals(setOf(CellPosition(4, 4), CellPosition(4, 5), CellPosition(5, 4), position),
            six.copy(fogLights = emptySet()).litCells(cells))
        assertEquals(cells, applyInput(cells, setOf(position), InputMode.Digit, 9))
    }

    @Test fun smallBoardGeometryAndOutsideCluesUseGridEdges() {
        val axis = BoardAxis(300f, 0f, 6)
        assertEquals(50f, axis.cellSize)
        assertEquals(300f, axis.at(6.0))
        assertEquals(.5f, axis.normalized(150f))
        val clue = PuzzleOverlay(GridPoint(6.5, 2.5), text = "2")
        assertTrue(clue.isOutsideTextClue(6))
        assertEquals(1f, six.copy(overlays = listOf(clue)).boardPaddingCells())
        val puzzle = parseSudokuPadPayload(SudokuFixtures.sudokuPadPayload(six.givens,
            "\"rules\":\"Skyscraper clues\",\"overlays\":[{\"center\":[6.5,2.5],\"text\":\"2\"}]"))
        assertEquals((5 downTo 0).map { CellPosition(it, 2) }, puzzle.outsideClues.single().cells)
    }

    @Test fun smallGamesSurviveReopeningWithHistory() {
        val storage = MemoryAppStorage()
        val cells = applyInput(six.startingCells(), setOf(CellPosition(5, 5)), InputMode.Centre, 6)
        val game = SavedGame(six, cells, true, history = listOf(six.startingCells()))
        GameRepository(storage).saveGame(game)
        assertEquals(game, GameRepository(storage).game(six))
    }

    @Test fun fpuzzlesKeepsEditableValuesAndExplicitRegions() {
        val payload = SudokuFixtures.fpuzzlesPayload(six.givens)
            .replaceFirst("{}", """{"value":3,"given":false,"region":0}""")
        val puzzle = parseSudokuPadPayload(payload)
        assertEquals(6, puzzle.size)
        assertEquals(CellState(3), puzzle.startingCells().first())
        assertEquals(listOf(setOf(CellPosition(0, 0))), puzzle.regions)
    }

    @Test fun rejectsNonSquareAndOversizedGrids() {
        assertFailsWith<IllegalArgumentException> { parseSudokuPadPayload("""scl{"cells":[[{},{}],[{}]]}""") }
        val row = List(10) { "{}" }.joinToString(",", "[", "]")
        assertFailsWith<IllegalArgumentException> { parseSudokuPadPayload("scl{\"cells\":[${List(10) { row }.joinToString(",")}]}" ) }
    }
}
