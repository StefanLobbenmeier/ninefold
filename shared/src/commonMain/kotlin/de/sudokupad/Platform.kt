package de.sudokupad

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform