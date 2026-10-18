package de.ninefold

import kotlin.test.*

class CageSemanticsTest {
    private fun imported(cage: String, rules: String = "Normal sudoku rules apply.") =
        parseSudokuPadPayload(SudokuFixtures.sudokuPadPayload(properties =
            """"metadata":{"rules":"$rules"},"cages":[{"cells":[[0,0],[4,4]],$cage}]"""))

    @Test fun explicitUniquenessOverridesRulesAndSurvivesSharing() {
        for (unique in listOf(false, true)) {
            val puzzle = imported(""""value":10,"unique":$unique""", "Killer cages.")
            assertEquals(unique, puzzle.cages.single().unique)
            assertEquals(puzzle, puzzleFromShareLink(puzzleShareLink(puzzle)))
            assertFalse(puzzle.importOmissions.any { it.path.endsWith(".unique") })
            val cells = puzzle.startingCells().toMutableList()
            cells[0] = CellState(value = 5)
            cells[40] = CellState(value = 5)
            assertEquals(if (unique) setOf(0, 40) else emptySet(), checkSudokuRules(puzzle, cells).cells)
            assertTrue(checkSudokuRules(puzzle, cells, includeSpecialRules = false).cells.isEmpty())
            cells[40] = CellState(centreNotes = setOf(5, 6))
            assertEquals(if (unique) setOf(5, 6) else setOf(6), checkSudokuRules(puzzle, cells).pencilMarks[40])
            assertTrue(checkSudokuRules(puzzle, cells, includeSpecialRules = false).pencilMarks.isEmpty())
        }
    }

    @Test fun absentUniquenessDoesNotTurnDecorativeCagesIntoKillers() {
        assertFalse(imported(""""value":null""").cages.single().unique)
        assertFalse(imported(""""value":10""").cages.single().unique)
        assertTrue(imported(""""value":10""", "Killer cages.").cages.single().unique)
        assertTrue(imported(""""value":null""", "Digits cannot repeat in cages.").cages.single().unique)
        val fpuzzles = parseSudokuPadPayload(SudokuFixtures.fpuzzlesPayload(properties =
            """"killercage":[{"cells":["R1C1","R5C5"],"value":10}]"""))
        assertTrue(fpuzzles.cages.single().unique)
    }

    @Test fun proseTotalChecksSpecialRulesWithoutInventingPrintedClues() {
        for (total in listOf("fifteen", "15")) {
            val puzzle = imported(""""unique":true""", "Cages sum to $total")
            assertNull(puzzle.cages.single().total)
            assertEquals(15, puzzle.cages.single().ruleTotal)
            assertEquals(listOf("Cages sum to $total"), puzzle.rules)
            assertEquals(puzzle, puzzleFromShareLink(puzzleShareLink(puzzle)))
            val cells = puzzle.startingCells().toMutableList()
            cells[0] = CellState(value = 8)
            cells[40] = CellState(value = 6)
            assertEquals(setOf(0, 40), checkSudokuRules(puzzle, cells).cells)
            assertTrue(checkSudokuRules(puzzle, cells, includeSpecialRules = false).cells.isEmpty())
            cells[40] = CellState(cornerNotes = setOf(6, 7))
            assertEquals(setOf(6), checkSudokuRules(puzzle, cells).pencilMarks[40])
        }
    }

    @Test fun malformedUniquenessStillHasAnOmission() {
        val puzzle = imported(""""unique":"sometimes" """)
        assertTrue(puzzle.importOmissions.any { it.path == "$.cages[0].unique" })
    }

    @Test fun exoticCageRulesRemainInSpecialRulesWithReviewNotice() {
        val rules = "Digits in shaded cells count as double their value for sums."
        val puzzle = imported(""""unique":true""", rules)
        assertEquals(listOf(rules), puzzle.rules)
        assertNull(puzzle.cages.single().ruleTotal)
        assertTrue(puzzle.importOmissions.any { it.kind == "validation" && it.detail.contains("additional cage rules") })
        assertNull(imported(""""unique":false""", "Cages sum to 15 or 20.").cages.single().ruleTotal)
        assertEquals(10, imported(""""value":10""", "Cages sum to fifteen").cages.single().total)
    }
}
