package dev.bluefalcon

import platform.Foundation.NSLog

actual class DefaultConsoleLogger actual constructor() : Log.Logger {
    override fun log(level: Log.Level, message: String) {
        NSLog("BlueFalcon: $message")
    }
}
