package com.itantra.core.download

import android.content.Context

/**
 * Persists the last-verified SHA-256 hash per downloaded file (keyed by filename, not pack —
 * a pack can have a main file and a separate aux file, each needing its own remembered hash).
 *
 * For sources that don't hand us an authoritative hash up front (anything not on
 * HuggingFace — GitHub Releases, fbaipublicfiles), there's nothing to compare a fresh
 * download against except "what we saw last time" (trust-on-first-download). This store
 * is that memory: it doesn't prove the *first* download was untampered, but it does catch
 * silent corruption or tampering on every download after the first.
 */
class ModelHashStore(context: Context) {
    private val prefs = context.getSharedPreferences("model_hashes", Context.MODE_PRIVATE)

    fun get(fileName: String): String? = prefs.getString(fileName, null)

    fun set(fileName: String, sha256: String) {
        prefs.edit().putString(fileName, sha256).apply()
    }

    fun clear(fileName: String) {
        prefs.edit().remove(fileName).apply()
    }
}
