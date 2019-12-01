package dev.bluefalcon

import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create

fun NSData.string(): String? {
    return NSString.create(this, NSUTF8StringEncoding) as String?
}
