package de.ninefold

internal expect fun readBrowserIndex(): String?
internal expect fun writeBrowserIndex(payload: String)

internal object BrowserIndexCache : SudokuIndexCache {
    override fun read() = readBrowserIndex()
    override fun write(payload: String) = writeBrowserIndex(payload)
}
