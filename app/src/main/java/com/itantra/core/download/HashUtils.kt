package com.itantra.core.download

import java.io.File
import java.security.MessageDigest

/**
 * Real SHA-256 computation and comparison for downloaded model files.
 *
 * Split out from [ModelDownloadManager] so the string-normalization/comparison logic
 * (the part that doesn't need file I/O) is independently unit-testable.
 */
object HashUtils {

    private val HEX_64_REGEX = Regex("^[0-9a-fA-F]{64}$")

    /** Streams the file through SHA-256 without loading it fully into memory (models are 15-200MB). */
    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Normalizes an HTTP `ETag` / `X-Linked-ETag` header value into a plain lowercase hex SHA-256
     * string, or null if it doesn't look like one. HuggingFace LFS files return their real SHA-256
     * this way (optionally quoted, optionally prefixed with "sha256:").
     */
    fun normalizeHashHeader(headerValue: String?): String? {
        if (headerValue == null) return null
        var v = headerValue.trim().trim('"')
        if (v.startsWith("sha256:", ignoreCase = true)) {
            v = v.substringAfter(':')
        }
        v = v.trim()
        return if (HEX_64_REGEX.matches(v)) v.lowercase() else null
    }

    /** Case-insensitive comparison of two hex SHA-256 strings. */
    fun hashesMatch(a: String, b: String): Boolean = a.equals(b, ignoreCase = true)
}
