package de.ninefold

import kotlin.test.*

class SudokuPadOmissionsTest {
    @Test fun reportsNestedLossWithoutReportingEmptyExtensions() {
        val puzzle = parseSudokuPadPayload(SudokuFixtures.sudokuPadPayload(properties = """
            "extension":{"meaning":1},"empty":[],
            "overlays":[{"center":[1,1],"text":"A","rotation":45},{"text":"lost"}],
            "lines":[{"wayPoints":[[0,0],[1,1],"bad"]}]
        """))
        val paths = puzzle.importOmissions.map { it.path }
        assertTrue("$.extension" in paths)
        assertTrue("$.overlays[0].rotation" in paths)
        assertTrue("$.overlays[1]" in paths)
        assertTrue("$.lines[0].wayPoints[2]" in paths)
        assertFalse("$.empty" in paths)
        assertEquals(1, puzzle.overlays.size)
    }

    @Test fun preservesRulesAndDistinguishesGraphicsFromValidation() {
        val rules = "Digits on purple lines obey a custom rule."
        val puzzle = parseSudokuPadPayload(SudokuFixtures.sudokuPadPayload(properties = """
            "metadata":{"rules":"$rules"},"triggereffect":[{"trigger":{"cell":"r1c1"},"effect":{"type":"sound","name":"bell"}}],
            "lines":[{"wayPoints":[[0,0],[1,1]]}]
        """))
        assertEquals(listOf(rules), puzzle.rules)
        assertTrue(puzzle.importOmissions.any { it.path == "$.triggereffect[0]" })
        assertTrue(puzzle.importOmissions.any { it.kind == "validation" })
    }

    @Test fun explicitFalseAndDiscardedSolutionsRemainVisible() {
        val puzzle = parseSudokuPadPayload(SudokuFixtures.sudokuPadPayload(properties = """
            "metadata":{"solution":"123"},"cages":[{"cells":[[0,0]],"unique":false}]
        """))
        assertTrue(puzzle.importOmissions.any { it.path == "$.cages[0].unique" })
        assertTrue(puzzle.importOmissions.any { it.path == "$.solution" })
    }

    @Test fun minimalSyntheticGridHasNoOmissions() {
        assertTrue(parseSudokuPadPayload(SudokuFixtures.sudokuPadPayload()).importOmissions.isEmpty())
    }
}
