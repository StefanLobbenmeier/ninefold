package de.ninefold

import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** Opt-in live audit. Never invoked by tests; retains only report output, never puzzle payloads. */
fun main(args: Array<String>) = runBlocking {
    require(args.size == 2) { "Usage: ImportAudit <links.txt> <report.md>" }
    val output = File(args[1])
    output.writeText("# SudokuPad live import audit\n\nGenerated ${java.time.Instant.now()}. Source: repository Sudoku index.\n\n")
    File(args[0]).readLines().filter { it.startsWith("https://sudokupad.app/") }.distinct().forEach { url ->
        val section = try {
            val puzzle = withTimeout(30_000) { importSudokuPadPuzzle(url) }
            val grouped = puzzle.importOmissions.groupBy { it.kind to it.detail }
            "## $url\n\n${puzzle.importOmissions.size} report entries.\n\n" +
                grouped.map { (finding, entries) -> "- ${finding.first}: ${finding.second} (${entries.size}); paths: ${entries.map { it.path.replace(Regex("\\[\\d+\\]"), "[]") }.distinct().joinToString()}" }.joinToString("\n") + "\n\n"
        } catch (e: Exception) { "## $url\n\nFAILED: ${e.message}\n\n" }
        output.appendText(section)
        println(url)
    }
}
