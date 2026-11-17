package com.example.ui.components

import android.net.Uri
import androidx.compose.ui.platform.UriHandler

/**
 * Validates that an external URL uses HTTPS (or HTTP) with a non-blank host
 * so untrusted schemes (javascript:, file:, content:, intent:) cannot be launched.
 */
fun isSafeWebUrl(rawUri: String?): Boolean {
    val trimmed = rawUri?.trim().orEmpty()
    if (trimmed.isEmpty() || trimmed.length > 2048) return false
    val parsed = try {
        Uri.parse(trimmed)
    } catch (_: Exception) {
        return false
    }
    val scheme = parsed.scheme?.lowercase() ?: return false
    if (scheme != "https" && scheme != "http") return false
    return !parsed.host.isNullOrBlank()
}

fun openSafeExternalUri(uriHandler: UriHandler, rawUri: String?): Boolean {
    if (!isSafeWebUrl(rawUri)) return false
    return runCatching {
        uriHandler.openUri(rawUri!!.trim())
    }.isSuccess
}
