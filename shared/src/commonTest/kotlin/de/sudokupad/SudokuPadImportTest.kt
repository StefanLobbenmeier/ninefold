package de.sudokupad

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SudokuPadImportTest {
    @Test fun importsLegacyCageMetadataWithoutCreatingCages() {
        val puzzle = parseSudokuPadPayload(SudokuFixtures.sudokuPadPayload(properties =
            """"ca":[{"v":"title: Synthetic dots"},{"v":"author: Test setter"},{"v":"rules: Normal sudoku rules apply.\nGray dots: consecutive or double."},{"v":"solution: ${SudokuFixtures.classic.solution}"}]"""))
        assertEquals("Synthetic dots", puzzle.title)
        assertEquals("Test setter", puzzle.author)
        assertEquals(listOf("Normal sudoku rules apply.", "Gray dots: consecutive or double."), puzzle.rules)
        assertEquals(SudokuFixtures.classic.solution, puzzle.solution)
        assertTrue(puzzle.cages.isEmpty())
    }

    @Test fun explicitMetadataOverridesLegacyCageMetadata() {
        val puzzle = parseSudokuPadPayload(SudokuFixtures.sudokuPadPayload(properties =
            """"metadata":{"title":"Explicit title","rules":"Explicit rules"},"cages":[{"value":"title: Legacy title"},{"value":"rules: Legacy rules"},{"value":"author: Legacy setter"}]"""))
        assertEquals("Explicit title", puzzle.title)
        assertEquals(listOf("Explicit rules"), puzzle.rules)
        assertEquals("Legacy setter", puzzle.author)
    }

    @Test fun numericRoundedGrayDotsUseTheRevealedEdgeMarkLayer() {
        for (rounded in listOf("1", "true")) {
            val puzzle = parseSudokuPadPayload(SudokuFixtures.sudokuPadPayload(properties =
                """"o":[{"ct":[0.5,1],"w":0.286,"h":0.286,"c2":"#CFCFCF","c1":"#000000","r":$rounded}]"""))
            val dot = puzzle.overlays.single()
            assertTrue(dot.rounded)
            assertEquals("#CFCFCF", dot.backgroundColor)
            assertTrue(dot.isKropkiDot())
            assertTrue(dot.isRevealedEdgeMark(setOf(CellPosition(0, 0))))
        }
        val square = parseSudokuPadPayload(SudokuFixtures.sudokuPadPayload(properties =
            """"o":[{"ct":[0.5,1],"c2":"#CFCFCF","r":0}]""")).overlays.single()
        assertEquals(false, square.rounded)
        assertEquals(false, square.isKropkiDot())
    }

    @Test fun importsExplicitAntiKnightFlags() {
        val puzzle = parseSudokuPadPayload(SudokuFixtures.sudokuPadPayload(properties =
            """"metadata":{"antiknight":true}"""))
        assertTrue(puzzle.antiKnight)
        assertTrue(parseSudokuPadPayload(SudokuFixtures.fpuzzlesPayload(properties = """"antiknight":true""")).antiKnight)
        assertEquals(false, parseSudokuPadPayload(SudokuFixtures.sudokuPadPayload()).antiKnight)
        assertEquals(false, parseSudokuPadPayload(SudokuFixtures.sudokuPadPayload(properties =
            """"metadata":{"antiknight":false}""")).antiKnight)
        assertEquals(false, parseSudokuPadPayload(SudokuFixtures.sudokuPadPayload(properties =
            """"metadata":{"rules":"A knight appears in the artwork."}""")).antiKnight)
        assertEquals(puzzle, puzzleFromShareLink(puzzleShareLink(puzzle)))
    }

    @Test fun fetchesFromCorsEndpointAndPreservesNamespacedPathSeparators() = runTest {
        val engine = MockEngine { request ->
            assertEquals("https://sudokupad.svencodes.com/ctclegacy/test-setter/test-puzzle", request.url.toString())
            respond(SudokuFixtures.sudokuPadPayload(properties = """"metadata":{"title":"Fetched puzzle"}"""))
        }
        val client = HttpClient(engine)
        try {
            val puzzle = importSudokuPadPuzzle("https://sudokupad.app/test-setter/test-puzzle", client)
            assertEquals("Fetched puzzle", puzzle.title)
            assertEquals("https://sudokupad.app/test-setter/test-puzzle", puzzle.sourceUrl)
            assertEquals(".".repeat(81), puzzle.givens)
        } finally { client.close() }
    }

    @Test fun reimportBypassesCachesAndRetainsTheOriginalSourceUrl() = runTest {
        val urls = mutableSetOf<String>()
        val client = HttpClient(MockEngine { request ->
            assertNotNull(request.url.parameters["reimport"])
            urls += request.url.toString()
            respond(SudokuFixtures.sudokuPadPayload())
        })
        try {
            repeat(2) {
                val puzzle = importSudokuPadPuzzle("https://sudokupad.app/test", client, forceRefresh = true)
                assertEquals("https://sudokupad.app/test", puzzle.sourceUrl)
            }
            assertEquals(2, urls.size)
        } finally { client.close() }
    }

    @Test fun retainsEmbeddedVideoLinks() {
        val puzzle = parseSudokuPadPayload(SudokuFixtures.sudokuPadPayload(properties = """"metadata":{"videos":["aaaaaaaaaaa"]}"""))
        assertEquals("https://www.youtube.com/watch?v=aaaaaaaaaaa", puzzle.videoUrl)
    }

    @Test fun retainsExplicitAndLegacySuccessMessages() {
        val explicit = parseSudokuPadPayload(SudokuFixtures.sudokuPadPayload(properties =
            """"metadata":{"successMessage":"A custom finish!"}"""))
        assertEquals("A custom finish!", explicit.successMessage)

        val legacy = parseSudokuPadPayload(SudokuFixtures.fpuzzlesPayload(properties =
            """"cage":[{"cells":["R1C1"],"value":"msgcorrect: Legacy finish"}]"""))
        assertEquals("Legacy finish", legacy.successMessage)
        assertEquals(legacy, puzzleFromShareLink(puzzleShareLink(legacy)))

        val multiline = parseSudokuPadPayload(SudokuFixtures.fpuzzlesPayload(properties =
            """"cage":[{"cells":["R1C1"],"value":"msgcorrect: First line\nSecond line"}]"""))
        assertEquals("First line\nSecond line", multiline.successMessage)
    }

    @Test fun reportsUnavailablePuzzlesWithoutParsingTheErrorBody() = runTest {
        val client = HttpClient(MockEngine { respond("Not found", HttpStatusCode.NotFound) })
        try {
            val error = assertFailsWith<IllegalArgumentException> { importSudokuPadPuzzle("missing", client) }
            assertEquals("SudokuPad returned 404 for this puzzle.", error.message)
        } finally { client.close() }
    }

    @Test fun decompressesSudokuPadLzString() {
        assertEquals("Hello world!", lzStringDecompressFromBase64("BIUwNmD2AEDukCcwBMCEQ==="))
    }

    @Test fun parsesPackedSudokuPadGraphicsAndConstraints() {
        val payload = """{id:'fixture',metadata:{title:'Imported fixture',author:'Setter',rules:'Normal sudoku rules apply.\\nGreen lines are German whispers.\\nNumbers outside are skyscraper clues.',solution:'${SudokuFixtures.classic.solution}'},ce:[[{v:5},,,,,,,,],[,,,,,,,,],[,,,,,,,,],[,,,,,,,,],[,,,,,,,,],[,,,,,,,,],[,,,,,,,,],[,,,,,,,,],[,,,,,,,,]],ca:[{ce:[[0,0],[0,1]],v:8}],l:[{c:67f067,th:9.6,wp:[[0.5,0.5],[0.5,1.5]]}],a:[{c:aaaaaa,wp:[[1.5,1.2],[1.5,2.5]]}],o:[{ct:[1.5,1.5],w:.8,h:.8,c1:aaaaaa,c2:#F,r:t},{ct:[-.5,2.5],w:1,h:1,te:4}],foglight:[[0,0]]}"""
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

    @Test fun retainsCagesWithoutPrintedTotalsAndKeepsFogSeparate() {
        val payload = SudokuFixtures.sudokuPadPayload(properties = """"cages":[
            {"cells":[[0,0],[0,1]],"unique":true},
            {"cells":[[1,0],[1,1]],"value":""},
            {"cells":[[2,0],[2,1]],"value":null},
            {"cells":[[3,0],[3,1]],"value":"7"},
            {"cells":[[4,0]],"value":"foglight"},
            {"cells":[],"value":"title: Test"}
        ]""")
        val puzzle = parseSudokuPadPayload(payload)
        assertEquals(listOf(null, null, null, 7), puzzle.cages.map { it.total })
        assertEquals((0..3).map { row -> setOf(CellPosition(row, 0), CellPosition(row, 1)) },
            puzzle.cages.map { it.cells })
        assertEquals(setOf(CellPosition(4, 0)), puzzle.fogLights)
    }

    @Test fun retainsFpuzzlesCagesWithoutPrintedTotals() {
        val puzzle = parseSudokuPadPayload(SudokuFixtures.fpuzzlesPayload(properties =
            """"killercage":[{"cells":["R1C1","R1C2"]}]"""))
        assertEquals(KillerCage(null, setOf(CellPosition(0, 0), CellPosition(0, 1))), puzzle.cages.single())
    }

    @Test fun importsDynamicFogTriggersAndRectangularRevealsWithoutInitialFog() {
        val puzzle = parseSudokuPadPayload(SudokuFixtures.sudokuPadPayload(properties = """"triggereffect":[
            {"trigger":{"type":"cellvalue","cell":"r2c2r2c3"},"effect":{"type":"foglight","cells":"r7c7-r8c8, r9c9"}}
        ]"""))
        assertEquals(emptySet(), puzzle.fogLights)
        assertEquals(listOf(FogLink(setOf(CellPosition(1, 1), CellPosition(1, 2)),
            setOf(CellPosition(6, 6), CellPosition(6, 7), CellPosition(7, 6), CellPosition(7, 7), CellPosition(8, 8)))), puzzle.fogLinks)
        assertEquals(emptySet(), puzzle.litCells(puzzle.startingCells()))
        assertEquals(null, parseSudokuPadPayload(SudokuFixtures.sudokuPadPayload()).fogLinks)
        val empty = parseSudokuPadPayload(SudokuFixtures.sudokuPadPayload(properties = """"triggereffect":[]"""))
        assertEquals(emptyList(), empty.fogLinks)
        assertEquals(emptySet(), empty.fogLights)
    }

    @Test fun recognizesSupportedLinksAndRejectsOtherHosts() {
        assertEquals("test-puzzle", sudokuPadPuzzleId("https://sudokupad.app/test-puzzle"))
        assertEquals("test-setter/test-puzzle", sudokuPadPuzzleId("https://sudokupad.app/test-setter/test-puzzle"))
        assertEquals(null, sudokuPadPuzzleId("https://example.com/test-puzzle"))
    }

    @Test fun parsesFpuzzlesSemanticFeatures() {
        val payload = SudokuFixtures.fpuzzlesPayload(givens = "5" + ".".repeat(80), properties = """"title":"fpuz fixture","author":"Setter","ruleset":"Thermometers increase.","killercage":[{"cells":["R1C1","R1C2"],"value":"8"}],"thermometer":[{"lines":[["R2C1","R2C2"]]}],"arrow":[{"cells":["R3C1"],"lines":[["R3C1","R3C2","R3C3"]]}],"difference":[{"cells":["R4C1","R4C2"]}],"even":[{"cell":"R5C1"}],"foglight":["R1C1"]""")
        val puzzle = parseSudokuPadPayload(payload)
        assertEquals(8, puzzle.cages.single().total)
        assertEquals(LineRule.Thermometer, puzzle.lines.single().rule)
        assertEquals(CellPosition(2, 0), puzzle.arrows.single().circle)
        assertTrue(puzzle.overlays.size >= 2)
        assertTrue(puzzle.underlays.isNotEmpty())
        assertNotNull(puzzle.fogLights)
    }
}
