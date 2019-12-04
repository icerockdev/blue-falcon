package dev.bluefalcon

class EmptyLogger() : Log.Logger {
    override fun log(level: Log.Level, message: String) {}
}
