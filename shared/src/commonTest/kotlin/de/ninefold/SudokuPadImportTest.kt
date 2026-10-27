package de.ninefold

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
    @Test fun shadesUnlabelledFpuzzlesRegionsAndChecksTheirUniqueness() {
        val puzzle = parseSudokuPadPayload(SudokuFixtures.fpuzzlesPayload(properties = """"ruleset":
            "Each shaded region contains the digits 1-9 exactly once each.",
            "killercage":[{"cells":["R1C1","R4C4"]},{"cells":["R7C7"],"value":5}]"""))
        assertEquals(listOf(GridPoint(.5, .5), GridPoint(3.5, 3.5)), puzzle.underlays.map { it.center })
        assertTrue(puzzle.underlays.all { it.backgroundColor == "#E0E0E0" && it.width == 1.0 && it.height == 1.0 })
        assertTrue(puzzle.cages.first().unique)
        val cells = puzzle.startingCells().toMutableList().apply {
            this[0] = CellState(value = 2)
            this[30] = CellState(value = 2)
        }
        assertEquals(setOf(0, 30), checkSudokuRules(puzzle, cells).cells)
        assertTrue(checkSudokuRules(puzzle, cells, includeSpecialRules = false).cells.isEmpty())
        cells[30] = CellState(centreNotes = setOf(2, 3))
        assertEquals(setOf(2), checkSudokuRules(puzzle, cells).pencilMarks[30])
        assertEquals(puzzle, puzzleFromShareLink(puzzleShareLink(puzzle)))
    }

    @Test fun doesNotInventShadingForSclGraphicalCages() {
        val puzzle = parseSudokuPadPayload(SudokuFixtures.sudokuPadPayload(properties =
            """"cages":[{"cells":["R1C1","R4C4"]}]"""))
        assertTrue(puzzle.underlays.isEmpty())
        assertEquals(false, puzzle.cages.single().unique)
    }

    @Test fun importsOutsideLabelsWithZeroSizeBoxesAndExplicitFontSize() {
        val puzzle = parseSudokuPadPayload(SudokuFixtures.sudokuPadPayload(properties = """"cellSize":60,
            "overlays":[{"center":[-0.4,1.5],"width":0,"height":0,"text":12,
                "fontSize":24,"dominant-baseline":"alphabetic"}]"""))
        val label = puzzle.overlays.single()
        assertEquals("12", label.text)
        assertEquals(0.4, label.fontSize)
        assertEquals(-0.52, label.center.row, 0.0001)
        assertEquals(0.0, label.width)
        assertEquals(0.0, label.height)
        assertEquals(12, puzzle.outsideClues.single().value)
        assertTrue(label.isOutsideTextClue(puzzle.size))
        assertEquals(puzzle, puzzleFromShareLink(puzzleShareLink(puzzle)))
    }

    @Test fun importsLittleKillerDiagonalsWithoutRelyingOnRuleText() {
        val puzzle = parseSudokuPadPayload(SudokuFixtures.fpuzzlesPayload(properties = """"littlekillersum":[
            {"cell":"R0C3","cells":["R1C4","R2C5","R3C6","R4C7","R5C8","R6C9"],"direction":"DR","value":"27"},
            {"cell":"R10C7","direction":"UL","value":"30"},
            {"cell":"R4C0","direction":"UR","value":"12"},
            {"cell":"R6C10","direction":"DL","value":"15"}
        ]"""))
        assertEquals(listOf(27, 30, 12, 15), puzzle.outsideClues.map { it.value })
        assertEquals(List(6) { CellPosition(it, it + 3) }, puzzle.outsideClues[0].cells)
        assertEquals(List(6) { CellPosition(8 - it, 5 - it) }, puzzle.outsideClues[1].cells)
        assertEquals(List(3) { CellPosition(2 - it, it) }, puzzle.outsideClues[2].cells)
        assertEquals(List(3) { CellPosition(6 + it, 8 - it) }, puzzle.outsideClues[3].cells)
        assertTrue(puzzle.outsideClues.all { it.type == OutsideClueType.DiagonalSum })
        assertEquals(listOf("27", "30", "12", "15"), puzzle.overlays.map { it.text })
        assertEquals(GridPoint(-.5, 2.5), puzzle.overlays[0].center)
        assertEquals(4, puzzle.arrows.size)
        assertEquals(-.15, puzzle.arrows[0].points.first().row, .0001)
        assertEquals(3.35, puzzle.arrows[0].points.last().column, .0001)
        assertTrue(puzzle.importOmissions.none { it.path.contains("littlekillersum") })
        assertEquals(puzzle, puzzleFromShareLink(puzzleShareLink(puzzle)))
    }

    @Test fun reportsUnusableLittleKillerClues() {
        val puzzle = parseSudokuPadPayload(SudokuFixtures.fpuzzlesPayload(properties = """"littlekillersum":[
            {"cell":"R0C3","direction":"sideways","value":"12"},
            {"cell":"R5C5","direction":"DR","value":"12"},
            {"cell":"R0C3","direction":"DR","value":"?"}
        ]"""))
        assertTrue(puzzle.outsideClues.isEmpty())
        assertTrue(puzzle.arrows.isEmpty())
        assertTrue(puzzle.overlays.isEmpty())
        assertEquals(3, puzzle.importOmissions.count { it.path.startsWith("$.littlekillersum[") })
    }

    @Test fun textStrokeDoesNotBecomeAnOverlayBorder() {
        val puzzle = parseSudokuPadPayload(SudokuFixtures.sudokuPadPayload(properties =
            """"overlays":[
                {"center":[1,0.5],"width":0.35,"height":0.35,"rounded":true,"text":"X","stroke":"#000000","backgroundColor":"#FFFFFF"},
                {"center":[2,0.5],"width":0.35,"height":0.35,"rounded":true,"text":"V","stroke":"#000000","borderColor":"#FF0000"}]"""))
        assertEquals(null, puzzle.overlays[0].borderColor)
        assertEquals("#FFFFFF", puzzle.overlays[0].backgroundColor)
        assertEquals("X", puzzle.overlays[0].text)
        assertEquals("#FF0000", puzzle.overlays[1].borderColor)
    }

    @Test fun ignoresUnmarkedFpuzzlesSolutionValues() {
        val payload = SudokuFixtures.fpuzzlesPayload(givens = "12" + ".".repeat(79))
            .replace("{\"value\":2,\"given\":true}", "{\"value\":2}")
        val puzzle = parseSudokuPadPayload(payload)
        assertEquals("1" + ".".repeat(80), puzzle.givens)
        assertEquals(CellState(), puzzle.startingCells()[1])
        assertTrue(puzzle.initialValues.isEmpty())
    }

    @Test fun importsFixedAndEditableCellValuesWithoutChangingSclSemantics() {
        val payload = SudokuFixtures.fpuzzlesPayload(givens = "12" + ".".repeat(79))
            .replace("{\"value\":2,\"given\":true}", "{\"value\":2,\"given\":false}")
        val puzzle = parseSudokuPadPayload(payload)
        assertEquals("1." + ".".repeat(79), puzzle.givens)
        assertEquals(CellState(1, given = true), puzzle.startingCells()[0])
        assertEquals(CellState(2), puzzle.startingCells()[1])
        assertEquals(puzzle, puzzleFromShareLink(puzzleShareLink(puzzle)))
        assertEquals(puzzle.startingCells(), assertNotNull(puzzleFromShareLink(puzzleShareLink(puzzle))).startingCells())
        val scl = parseSudokuPadPayload(payload.replace("fpuz", "scl").replace("\"grid\"", "\"cells\""))
        assertEquals(CellState(2, given = true), scl.startingCells()[1])
        assertTrue(puzzle.importOmissions.none { it.path.endsWith(".given") })
        assertEquals(SudokuFixtures.classic.givens, parseSudokuPadPayload(
            SudokuFixtures.fpuzzlesPayload(SudokuFixtures.classic.givens)).givens)
    }

    @Test fun importsAuthoredCellShadingAndReportsUnrepresentedMarksAndColors() {
        val payload = SudokuFixtures.fpuzzlesPayload().replaceFirst("{}",
            """{"color":"#A8A8A8","givenPencilMarks":[1,3],"given":true}""")
        val puzzle = parseSudokuPadPayload(payload)
        assertEquals(listOf(PuzzleOverlay(GridPoint(.5, .5), 1.0, 1.0, "#A8A8A8", rounded = false)), puzzle.underlays)
        assertTrue(puzzle.importOmissions.any { it.path == "$.grid[0][0].givenPencilMarks" })
        assertTrue(puzzle.importOmissions.none { it.path.endsWith(".color") || it.path.endsWith(".given") })
        val malformed = parseSudokuPadPayload(payload.replace("#A8A8A8", "palette-2").replace("\"given\":true", "\"given\":2"))
        assertTrue(malformed.underlays.isEmpty())
        assertTrue(malformed.importOmissions.any { it.path.endsWith(".color") })
        assertTrue(malformed.importOmissions.any { it.path.endsWith(".given") })
    }

    @Test fun importsFpuzzlesCircleArtworkBetweenCellsAndAtIntersections() {
        val puzzle = parseSudokuPadPayload(SudokuFixtures.fpuzzlesPayload(properties = """"circle":[
            {"cells":["R1C1","R1C2"],"baseC":"#FFFFFF","outlineC":"#000000","width":0.35,"height":0.35},
            {"cells":["R3C3","R2C3"],"baseC":"#000000","outlineC":"#000000","width":0.35,"height":0.35},
            {"cells":["R4C4","R4C5","R5C4","R5C5"],"baseC":"#FFE100","outlineC":"#000000","width":0.5,"height":0.5},
            {"cells":["R7C7","R6C6","R7C6","R6C7"],"baseC":"#FF0000","outlineC":"#336699","fontC":"#123456","value":"A","width":0.45,"height":0.6},
            {"cells":[]}
        ]"""))
        assertEquals(listOf(
            PuzzleOverlay(GridPoint(.5, 1.0), .35, .35, "#FFFFFF", "#000000", "#000000", rounded = true),
            PuzzleOverlay(GridPoint(2.0, 2.5), .35, .35, "#000000", "#000000", "#000000", rounded = true),
            PuzzleOverlay(GridPoint(4.0, 4.0), .5, .5, "#FFE100", "#000000", "#000000", rounded = true),
            PuzzleOverlay(GridPoint(6.0, 6.0), .45, .6, "#FF0000", "#336699", "#123456", "A", rounded = true),
        ), puzzle.overlays)
        assertEquals(puzzle, puzzleFromShareLink(puzzleShareLink(puzzle)))
    }

    @Test fun importsFpuzzlesLineOutlineColorsForEveryPath() {
        val puzzle = parseSudokuPadPayload(SudokuFixtures.fpuzzlesPayload(properties = """"line":[
            {"lines":[["R1C1","R1C2"],["R2C1","R2C2"]],"outlineC":"#3366FF"},
            {"lines":[["R3C1","R3C2"]],"outlineC":"#33CC66"},
            {"lines":[["R4C1","R4C2"]]}
        ]"""))
        assertEquals(listOf("#3366FF", "#3366FF", "#33CC66", "#A0A0A0"), puzzle.lines.map { it.color })
        assertEquals(listOf(GridPoint(0.5, 0.5), GridPoint(0.5, 1.5)), puzzle.lines.first().points)
    }

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
