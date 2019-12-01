package dev.bluefalcon

actual class DefaultConsoleLogger actual constructor() : Log.Logger {
    override fun log(level: Log.Level, message: String) {
        val tag = "BlueFalcon"
        when (level) {
            Log.Level.NONE -> return
            Log.Level.ERROR -> android.util.Log.e(tag, message)
            Log.Level.DEBUG -> android.util.Log.d(tag, message)
            Log.Level.VERBOSE -> android.util.Log.v(tag, message)
        }
    }
}
