package de.sudokupad

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SudokuPadImportTest {
    @Test fun decompressesSudokuPadLzString() {
        assertEquals("Hello world!", lzStringDecompressFromBase64("BIUwNmD2AEDukCcwBMCEQ==="))
    }

    @Test fun parsesPackedSudokuPadGraphicsAndConstraints() {
        val payload = """{id:'fixture',metadata:{title:'Imported fixture',author:'Setter',rules:'Normal sudoku rules apply.\\nGreen lines are German whispers.\\nNumbers outside are skyscraper clues.',solution:'123456789'.repeat(9)},ce:[[{v:5},,,,,,,,],[,,,,,,,,],[,,,,,,,,],[,,,,,,,,],[,,,,,,,,],[,,,,,,,,],[,,,,,,,,],[,,,,,,,,],[,,,,,,,,]],ca:[{ce:[[0,0],[0,1]],v:8}],l:[{c:67f067,th:9.6,wp:[[0.5,0.5],[0.5,1.5]]}],a:[{c:aaaaaa,wp:[[1.5,1.2],[1.5,2.5]]}],o:[{ct:[1.5,1.5],w:.8,h:.8,c1:aaaaaa,c2:#F,r:t},{ct:[-.5,2.5],w:1,h:1,te:4}],foglight:[[0,0]]}"""
            .replace("'123456789'.repeat(9)", "'${"123456789".repeat(9)}'")
        val puzzle = parseSudokuPadPayload(payload, "https://sudokupad.app/fixture")
        assertEquals("Imported fixture", puzzle.title)
        assertEquals('5', puzzle.givens.first())
        assertEquals(8, puzzle.cages.single().total)
        assertEquals("#67f067", puzzle.lines.single().color)
        assertEquals(CellPosition(1, 1), puzzle.arrows.single().circle)
        assertEquals(setOf(CellPosition(0, 0)), puzzle.fogLights)
        assertTrue(puzzle.overlays.any { it.center.row < 0 })
        assertEquals(OutsideClueType.Skyscraper, puzzle.outsideClues.single().type)
        assertNotNull(puzzle.solution)
    }

    @Test fun recognizesSupportedLinksAndRejectsOtherHosts() {
        assertEquals("hDNMJqPmTh", sudokuPadPuzzleId("https://sudokupad.app/hDNMJqPmTh"))
        assertEquals("philip-newman/a-puzzle", sudokuPadPuzzleId("https://sudokupad.app/philip-newman/a-puzzle"))
        assertEquals(null, sudokuPadPuzzleId("https://example.com/hDNMJqPmTh"))
    }

    @Test fun parsesFpuzzlesSemanticFeatures() {
        val rows = List(9) { row -> List(9) { column -> if (row == 0 && column == 0) "{\"value\":5}" else "{}" }.joinToString(prefix = "[", postfix = "]") }.joinToString()
        val payload = """fpuz{"size":9,"title":"fpuz fixture","author":"Setter","ruleset":"Thermometers increase.","grid":[$rows],"killercage":[{"cells":["R1C1","R1C2"],"value":"8"}],"thermometer":[{"lines":[["R2C1","R2C2"]]}],"arrow":[{"cells":["R3C1"],"lines":[["R3C1","R3C2","R3C3"]]}],"difference":[{"cells":["R4C1","R4C2"]}],"even":[{"cell":"R5C1"}],"foglight":["R1C1"]}"""
        val puzzle = parseSudokuPadPayload(payload)
        assertEquals(8, puzzle.cages.single().total)
        assertEquals(LineRule.Thermometer, puzzle.lines.single().rule)
        assertEquals(CellPosition(2, 0), puzzle.arrows.single().circle)
        assertTrue(puzzle.overlays.size >= 2)
        assertTrue(puzzle.underlays.isNotEmpty())
        assertNotNull(puzzle.fogLights)
    }
}
