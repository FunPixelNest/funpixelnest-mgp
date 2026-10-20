package com.funpixelnest.mgp.platform

import platform.Foundation.NSUserDefaults

actual object LocalPrefs {
    actual fun get(key: String): String? = NSUserDefaults.standardUserDefaults.stringForKey(key)

    actual fun set(key: String, value: String) {
        NSUserDefaults.standardUserDefaults.setObject(value, key)
    }
}
