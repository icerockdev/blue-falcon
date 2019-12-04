package dev.bluefalcon

import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
object Log {
    var logger: Logger = DefaultConsoleLogger()
    var level: Level = Level.NONE

    private fun log(level: Level = Level.VERBOSE, message: () -> String) {
        if (this.level.ordinal < level.ordinal) return

        logger.log(level, message())
    }

    fun d(message: () -> String) = log(Level.DEBUG, message)
    fun e(message: () -> String) = log(Level.ERROR, message)
    fun v(message: () -> String) = log(Level.VERBOSE, message)

    enum class Level {
        NONE,
        ERROR,
        DEBUG,
        VERBOSE
    }

    interface Logger {
        fun log(level: Level, message: String)
    }
}
