package de.ninefold

/** Synthetic baselines; use copy(...) to make the constellation relevant to each test. */
internal object SudokuFixtures {
    val empty = Puzzle("Test Sudoku", "Test setter", ".".repeat(81))
    val classic = empty.copy(
        givens = "53..7....6..195....98....6.8...6...34..8.3..17...2...6.6....28....419..5....8..79",
        solution = "534678912672195348198342567859761423426853791713924856961537284287419635345286179",
    )

    /** JSON fields remain visible at the call site, including deliberately unusual encodings. */
    fun sudokuPadPayload(givens: String = empty.givens, properties: String = ""): String =
        """scl{"cells":${cellGrid(givens)}${if (properties.isBlank()) "" else ",$properties"}}"""

    fun fpuzzlesPayload(givens: String = empty.givens, properties: String = ""): String =
        """fpuz{"size":9,"grid":${cellGrid(givens)}${if (properties.isBlank()) "" else ",$properties"}}"""

    private fun cellGrid(givens: String): String {
        require(givens.length == 81)
        return givens.chunked(9).joinToString(prefix = "[", postfix = "]") { row ->
            row.map { if (it in '1'..'9') """{"value":$it}""" else "{}" }
                .joinToString(prefix = "[", postfix = "]")
        }
    }
}
