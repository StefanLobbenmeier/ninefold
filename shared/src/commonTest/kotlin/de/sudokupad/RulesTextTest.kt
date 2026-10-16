package de.sudokupad

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import kotlin.test.Test
import kotlin.test.assertEquals

class RulesTextTest {
    @Test fun linksKeepTheirTextAndExcludeSurroundingPunctuation() {
        val text = "See https://example.com/rules. Also (https://example.com/a_(b)), or www.example.org/help!"
        val linked = linkedRuleText(text, Color.Blue)
        assertEquals(text, linked.text)
        val links = linked.getLinkAnnotations(0, linked.length)
        assertEquals(listOf("https://example.com/rules", "https://example.com/a_(b)", "https://www.example.org/help"),
            links.map { (it.item as LinkAnnotation.Url).url })
        assertEquals(listOf("https://example.com/rules", "https://example.com/a_(b)", "www.example.org/help"),
            links.map { text.substring(it.start, it.end) })
    }

    @Test fun importedParagraphsAndExplicitBulletsRetainTheirStructure() {
        val puzzle = parseSudokuPadPayload(SudokuFixtures.sudokuPadPayload(properties =
            """"metadata":{"rules":"Normal sudoku rules apply.\n\nEXTRA RULES\n- Adjacent digits differ.\n• Marked digits increase."}"""))
        assertEquals(listOf(
            RuleLine("Normal sudoku rules apply."), RuleLine(""), RuleLine("EXTRA RULES"),
            RuleLine("Adjacent digits differ.", true), RuleLine("Marked digits increase.", true),
        ), ruleLines(puzzle.rules))
    }

    @Test fun multilineRulesRecognizeMarkersWithoutChangingOrdinaryText() {
        val puzzle = SudokuFixtures.empty.copy(rules = listOf("Heading\n* First\n+ Second\n-1 is negative\nA - B\n1. Numbered rule"))
        assertEquals(listOf(
            RuleLine("Heading"), RuleLine("First", true), RuleLine("Second", true),
            RuleLine("-1 is negative"), RuleLine("A - B"), RuleLine("1. Numbered rule"),
        ), ruleLines(puzzle.rules))
    }
}
