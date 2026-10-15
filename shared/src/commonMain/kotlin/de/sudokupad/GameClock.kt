package de.sudokupad

import kotlin.time.TimeSource

/** Monotonic elapsed time, with completed games frozen until an edit reopens them. */
internal class GameClock(private val nowMillis: (() -> Long)? = null) {
    private val origin = TimeSource.Monotonic.markNow()
    private fun now() = nowMillis?.invoke() ?: origin.elapsedNow().inWholeMilliseconds
    private var runningSince: Long? = null
    private var accumulated = 0L
    private var foreground = true
    private var started = false

    fun reset() { restore(0) }
    fun restore(seconds: Long) { started = false; runningSince = null; accumulated = seconds.coerceAtLeast(0) * 1000 }
    fun start() { started = true; if (foreground && runningSince == null) runningSince = now() }
    fun stop() {
        started = false
        pause()
    }
    fun setForeground(visible: Boolean) {
        foreground = visible
        if (!visible) pause() else if (started) start()
    }
    private fun pause() {
        runningSince?.let { accumulated += now() - it }
        runningSince = null
    }
    fun elapsedSeconds(): Long = (accumulated + (runningSince?.let { now() - it } ?: 0L)) / 1000
}

internal fun formatElapsed(seconds: Long): String {
    val minutes = seconds / 60
    val remainder = (seconds % 60).toString().padStart(2, '0')
    return if (minutes < 60) "${minutes.toString().padStart(2, '0')}:$remainder"
    else "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}:$remainder"
}

internal fun Puzzle.isComplete(cells: List<CellState>): Boolean {
    if (cells.size != 81 || cells.any { it.value !in 1..9 }) return false
    return if (solution != null) cells.map { it.value }.joinToString("") == solution
    else checkSudokuRules(this, cells).cells.isEmpty()
}
