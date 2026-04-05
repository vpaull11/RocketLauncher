package com.rocketlauncher.presentation.navigation

import android.util.Log

private const val TAG = "NavArgDecode"

/**
 * [URLDecoder] через Java (см. [UrlFormEncode]) + обход битой percent-encoding.
 */
fun safeUrlDecode(encoded: String?): String {
    if (encoded.isNullOrEmpty()) return ""
    return try {
        UrlFormEncode.decodeUtf8(encoded)
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "decode failed, use raw: ${e.message}")
        encoded
    }
}
