@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package de.ninefold

@JsFun("(key) => localStorage.getItem(key)")
private external fun readLocal(key: String): String?
@JsFun("(key, payload) => localStorage.setItem(key, payload)")
private external fun writeLocal(key: String, payload: String)
@JsFun("() => JSON.stringify(Object.fromEntries(Object.keys(localStorage).map(k => [k, localStorage.getItem(k)])))")
private external fun localEntries(): String
private object BrowserAppStorage : AppStorage {
    override val browserStorage = true
    override fun entries(): Map<String, String> = kotlinx.serialization.json.Json.decodeFromString(localEntries())
    override fun read(key: String) = readLocal(key)
    override fun write(key: String, payload: String) = writeLocal(key, payload)
}
actual fun platformAppStorage(): AppStorage = BrowserAppStorage
