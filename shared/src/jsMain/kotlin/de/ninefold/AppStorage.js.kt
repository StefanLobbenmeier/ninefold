package de.ninefold

private fun readLocal(key: String): String? = js("localStorage.getItem(key)") as String?
private fun writeLocal(key: String, payload: String) { js("localStorage.setItem(key, payload)") }
private object BrowserAppStorage : AppStorage {
    override fun read(key: String) = readLocal(key)
    override fun write(key: String, payload: String) = writeLocal(key, payload)
}
actual fun platformAppStorage(): AppStorage = BrowserAppStorage
