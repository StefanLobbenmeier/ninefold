package de.sudokupad

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

private object DesktopAppStorage : AppStorage {
    private val directory = Path.of(System.getProperty("user.home"), ".ninefold")
    private fun path(key: String): Path = directory.resolve(MessageDigest.getInstance("SHA-256")
        .digest(key.toByteArray()).joinToString("") { "%02x".format(it) } + ".json")
    override fun read(key: String): String? = path(key).takeIf(Files::exists)?.let(Files::readString)
    override fun write(key: String, payload: String) {
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, "save-", ".tmp")
        try {
            Files.writeString(temporary, payload)
            Files.move(temporary, path(key), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally { Files.deleteIfExists(temporary) }
    }
}
actual fun platformAppStorage(): AppStorage = DesktopAppStorage
