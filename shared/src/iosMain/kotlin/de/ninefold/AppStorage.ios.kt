package de.ninefold

import platform.Foundation.NSUserDefaults

private object IOSAppStorage : AppStorage {
    override fun read(key: String) = NSUserDefaults.standardUserDefaults.stringForKey(key)
    override fun write(key: String, payload: String) { NSUserDefaults.standardUserDefaults.setObject(payload, forKey = key) }
}
actual fun platformAppStorage(): AppStorage = IOSAppStorage
