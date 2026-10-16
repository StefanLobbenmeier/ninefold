package de.sudokupad

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield

enum class ImportStatus { Conflicts, Unsolvable, Unique, Multiple }

data class ImportValidation(val status: ImportStatus, val conflicts: Set<Int> = emptySet(), val solution: String? = null)

/** Counts up to two solutions, without filling or changing the imported clues. */
suspend fun validateClassicImport(grid: String): ImportValidation {
    require(grid.length == 81 && grid.all { it == '.' || it in '1'..'9' })
    val puzzle = Puzzle("", "", grid)
    val conflicts = conflictsFor(puzzle, puzzle.startingCells())
    if (conflicts.isNotEmpty()) return ImportValidation(ImportStatus.Conflicts, conflicts)
    val values = grid.map { it.digitToIntOrNull() ?: 0 }.toIntArray()
    val rows = IntArray(9)
    val columns = IntArray(9)
    val boxes = IntArray(9)
    fun box(index: Int) = index / 27 * 3 + index % 9 / 3
    values.forEachIndexed { i, n -> if (n != 0) {
        val bit = 1 shl n
        rows[i / 9] = rows[i / 9] or bit
        columns[i % 9] = columns[i % 9] or bit
        boxes[box(i)] = boxes[box(i)] or bit
    } }
    var solutions = 0
    var firstSolution: String? = null
    var visits = 0
    suspend fun search() {
        if (++visits % 256 == 0) { currentCoroutineContext().ensureActive(); yield() }
        var chosen = -1
        var candidates = 0
        var smallest = 10
        for (i in values.indices) if (values[i] == 0) {
            val mask = 0x3fe and (rows[i / 9] or columns[i % 9] or boxes[box(i)]).inv()
            val count = mask.countOneBits()
            if (count == 0) return
            if (count < smallest) { chosen = i; candidates = mask; smallest = count }
            if (count == 1) break
        }
        if (chosen == -1) {
            solutions++
            if (solutions == 1) firstSolution = values.joinToString("")
            return
        }
        val r = chosen / 9; val c = chosen % 9; val b = box(chosen)
        for (n in 1..9) {
            val bit = 1 shl n
            if (candidates and bit == 0) continue
            values[chosen] = n
            rows[r] = rows[r] or bit; columns[c] = columns[c] or bit; boxes[b] = boxes[b] or bit
            search()
            values[chosen] = 0
            rows[r] = rows[r] xor bit; columns[c] = columns[c] xor bit; boxes[b] = boxes[b] xor bit
            if (solutions == 2) return
        }
    }
    search()
    return ImportValidation(when (solutions) {
        0 -> ImportStatus.Unsolvable
        1 -> ImportStatus.Unique
        else -> ImportStatus.Multiple
    }, solution = firstSolution.takeIf { solutions == 1 })
}
