package de.ninefold

import android.content.Context

private var appContext: Context? = null
fun initializeAppStorage(context: Context) { appContext = context.applicationContext }
private object AndroidAppStorage : AppStorage {
    private fun preferences() = checkNotNull(appContext) { "App storage is not initialized." }
        .getSharedPreferences("ninefold", Context.MODE_PRIVATE)
    override fun entries(): Map<String, String> = preferences().all.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()
    override fun read(key: String) = preferences().getString(key, null)
    override fun write(key: String, payload: String) { check(preferences().edit().putString(key, payload).commit()) { "Could not save preferences." } }
}
actual fun platformAppStorage(): AppStorage = AndroidAppStorage
