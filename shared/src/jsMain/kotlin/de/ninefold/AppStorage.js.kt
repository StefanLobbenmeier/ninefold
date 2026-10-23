package de.ninefold

private fun readLocal(key: String): String? = js("localStorage.getItem(key)") as String?
private fun writeLocal(key: String, payload: String) { js("localStorage.setItem(key, payload)") }
private fun localEntries(): String = js("JSON.stringify(Object.fromEntries(Object.keys(localStorage).map(k => [k, localStorage.getItem(k)])))") as String
private object BrowserAppStorage : AppStorage {
    override val browserStorage = true
    override fun entries(): Map<String, String> = kotlinx.serialization.json.Json.decodeFromString(localEntries())
    override fun read(key: String) = readLocal(key)
    override fun write(key: String, payload: String) = writeLocal(key, payload)
}
actual fun platformAppStorage(): AppStorage = BrowserAppStorage
