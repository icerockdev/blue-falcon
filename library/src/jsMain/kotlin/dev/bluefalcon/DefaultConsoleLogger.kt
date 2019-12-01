package dev.bluefalcon

actual class DefaultConsoleLogger actual constructor() : Log.Logger {
    override fun log(level: Log.Level, message: String) {
        println("BlueFalcon: $message")
    }
}
