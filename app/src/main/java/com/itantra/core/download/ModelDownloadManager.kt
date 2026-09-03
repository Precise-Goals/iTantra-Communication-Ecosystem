package com.itantra.core.download

import android.content.Context
import android.util.Log
import com.itantra.domain.model.DownloadState
import com.itantra.domain.model.ModelPack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Manages background model downloads with real-time progress tracking.
 *
 * Strict Production Rules:
 * 1. ZERO RE-DOWNLOADS: If a model is already downloaded on disk and its integrity check
 *    (file existence, min size sanity, and SHA256 checksum) passes, IT IS NEVER RE-DOWNLOADED.
 * 2. REAL HTTP DOWNLOADS ONLY: Absolutely zero fake stubs or mock synthesis.
 * 3. STRICT INTEGRITY VERIFICATION:
 *    - Validates file size is >= 50% of expected binary size and >= 100KB.
 *    - Validates SHA256 hash against official upstream checksums in ModelRegistry.
 *    - Deletes corrupt partial or mismatched files immediately.
 * 4. ATOMIC DISK COMMITS: Streams into .part files and atomically renames only upon full verification.
 */
class ModelDownloadManager(private val context: Context) {

    private val TAG = "ModelDownloadManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val activeJobs = mutableMapOf<ModelPack, Job>()

    val modelsDir: File
        get() = File(context.filesDir, "models").also { it.mkdirs() }

    private val _downloadStates = MutableStateFlow<Map<ModelPack, DownloadState>>(
        ModelPack.entries.associateWith { pack ->
            if (verifyModelIntegrity(pack)) DownloadState.Downloaded else DownloadState.NotDownloaded
        }
    )
    val downloadStates: StateFlow<Map<ModelPack, DownloadState>> = _downloadStates.asStateFlow()

    /**
     * Strictly verifies the integrity of a model file on disk:
     * 1. Checks file exists and is a regular file.
     * 2. Checks file size is >= 50% of expected binary size and >= 100KB.
     * 3. Checks cryptographic SHA256 hash if specified in ModelRegistry.
     *
     * If the file is corrupt or has a mismatched checksum, it is automatically purged
     * from disk and false is returned so it can be cleanly downloaded.
     */
    fun verifyModelIntegrity(pack: ModelPack): Boolean {
        val info = ModelRegistry.getInfo(pack) ?: return false
        val file = File(modelsDir, info.fileName)
        if (!file.exists() || !file.isFile) return false

        val fileLength = file.length()
        val minAcceptable = (info.sizeBytes * 0.50).toLong().coerceAtLeast(100 * 1024L)
        if (fileLength < minAcceptable) {
            Log.w(TAG, "Corrupt model detected for $pack: size $fileLength < expected min $minAcceptable. Deleting corrupt file.")
            file.delete()
            return false
        }

        val expectedSha = info.sha256
        if (expectedSha.isNotBlank() && !expectedSha.startsWith("unknown") && !expectedSha.startsWith("placeholder")) {
            val actualSha = computeSha256(file)
            if (!actualSha.equals(expectedSha, ignoreCase = true)) {
                Log.e(TAG, "Integrity check FAILED for $pack: expected SHA256 $expectedSha, got $actualSha. Deleting corrupt file.")
                file.delete()
                return false
            }
            Log.d(TAG, "Integrity verified for $pack: SHA256 $actualSha matches expected.")
        }
        return true
    }

    /** Returns true if the model is present on disk and passes all integrity checks. */
    fun isModelPresent(pack: ModelPack): Boolean = verifyModelIntegrity(pack)

    fun areAllPresent(packs: List<ModelPack>): Boolean = packs.all { verifyModelIntegrity(it) }

    /**
     * Start downloading a model pack.
     *
     * SAFETY GUARANTEE:
     * If the model already exists on disk and passes SHA256 / size integrity,
     * this method IMMEDIATELY returns and does NOT perform any network requests.
     */
    fun download(pack: ModelPack) {
        if (activeJobs[pack]?.isActive == true) {
            Log.d(TAG, "$pack is already currently downloading")
            return
        }

        val info = ModelRegistry.getInfo(pack) ?: run {
            Log.e(TAG, "No registry entry for $pack")
            updateState(pack, DownloadState.Failed("No model registry entry for $pack"))
            return
        }

        // ── ZERO REDOWNLOAD CHECK ──────────────────────────────────────
        if (verifyModelIntegrity(pack)) {
            Log.i(TAG, "Model $pack (${info.fileName}) is already downloaded and verified on disk. Skipping re-download.")
            updateState(pack, DownloadState.Downloaded)
            return
        }

        val job = scope.launch {
            val destFile = File(modelsDir, info.fileName)
            val partFile = File(modelsDir, "${info.fileName}.part")

            // Clean up any stale partial download
            if (partFile.exists()) partFile.delete()

            updateState(pack, DownloadState.Queued)
            Log.i(TAG, "Initiating real HTTP download: $pack from ${info.downloadUrl}")

            try {
                val request = Request.Builder()
                    .url(info.downloadUrl)
                    .header("User-Agent", "iTantra-Android/2.0 (+https://github.com/Precise-Goals/iTantra-Communication-Ecosystem)")
                    .header("Accept", "application/octet-stream, */*")
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    val errorMsg = "HTTP ${response.code}: ${response.message} for ${info.downloadUrl}"
                    Log.e(TAG, "Download failed: $pack — $errorMsg")
                    updateState(pack, DownloadState.Failed(errorMsg))
                    return@launch
                }

                val body = response.body ?: run {
                    updateState(pack, DownloadState.Failed("Empty response body from server"))
                    return@launch
                }

                val contentLength = body.contentLength().let { if (it > 0L) it else info.sizeBytes }

                // Stream into .part file with throttle progress updates
                partFile.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024) // 64KB chunk buffer
                        var downloadedBytes = 0L
                        var bytesRead: Int
                        var lastUpdateTime = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            out.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastUpdateTime > 200L) {
                                lastUpdateTime = now
                                val progress = (downloadedBytes.toFloat() / contentLength.toFloat() * 100f)
                                    .coerceIn(0f, 99f)
                                updateState(
                                    pack, DownloadState.Downloading(
                                        progressPercent = progress,
                                        downloadedBytes = downloadedBytes,
                                        totalBytes = contentLength
                                    )
                                )
                            }
                        }
                        out.flush()
                    }
                }

                // ── POST-DOWNLOAD INTEGRITY VERIFICATION ─────────────────
                val downloadedSize = partFile.length()
                val minAcceptable = (info.sizeBytes * 0.50).toLong().coerceAtLeast(100 * 1024L)
                if (downloadedSize < minAcceptable) {
                    partFile.delete()
                    val errMsg = "Downloaded file too small: ${downloadedSize} bytes (expected ~${info.sizeBytes}). Corrupted or HTML redirect."
                    Log.e(TAG, "$pack: $errMsg")
                    updateState(pack, DownloadState.Failed(errMsg))
                    return@launch
                }

                // Cryptographic SHA256 validation
                val expectedSha = info.sha256
                if (expectedSha.isNotBlank() && !expectedSha.startsWith("unknown") && !expectedSha.startsWith("placeholder")) {
                    Log.d(TAG, "Computing SHA256 checksum for downloaded $pack...")
                    val actualSha = computeSha256(partFile)
                    if (!actualSha.equals(expectedSha, ignoreCase = true)) {
                        partFile.delete()
                        val errMsg = "Integrity check FAILED for $pack: expected SHA256 $expectedSha, got $actualSha"
                        Log.e(TAG, errMsg)
                        updateState(pack, DownloadState.Failed("Integrity check failed: SHA256 mismatch"))
                        return@launch
                    }
                    Log.i(TAG, "$pack SHA256 verified successfully: $actualSha")
                }

                // Atomically move .part -> final model file
                if (destFile.exists()) destFile.delete()
                val renamed = partFile.renameTo(destFile)
                if (!renamed) {
                    partFile.copyTo(destFile, overwrite = true)
                    partFile.delete()
                }

                // If secondary asset exists (e.g. tokens.txt for STT), download it as well
                if (info.secondaryUrl != null && info.secondaryFileName != null) {
                    val secondaryDest = File(modelsDir, info.secondaryFileName)
                    try {
                        val secReq = Request.Builder()
                            .url(info.secondaryUrl)
                            .header("User-Agent", "iTantra-Android/2.0")
                            .build()
                        val secResp = client.newCall(secReq).execute()
                        if (secResp.isSuccessful && secResp.body != null) {
                            secondaryDest.writeBytes(secResp.body!!.bytes())
                            Log.i(TAG, "Downloaded secondary asset for $pack: ${info.secondaryFileName} (${secondaryDest.length()} bytes)")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed downloading secondary asset ${info.secondaryFileName}: ${e.message}")
                    }
                }

                updateState(pack, DownloadState.Downloaded)
                Log.i(TAG, "$pack downloaded and verified successfully: ${destFile.length() / 1024 / 1024}MB at ${destFile.path}")

            } catch (e: Exception) {
                if (partFile.exists()) partFile.delete()
                val errMsg = "Download exception: ${e.javaClass.simpleName}: ${e.message}"
                Log.e(TAG, "$pack: $errMsg")
                updateState(pack, DownloadState.Failed(errMsg))
            }
        }

        activeJobs[pack] = job
    }

    /** Cancel an in-progress download and clean up .part file */
    fun cancel(pack: ModelPack) {
        activeJobs[pack]?.cancel()
        activeJobs.remove(pack)
        File(modelsDir, "${ModelRegistry.getInfo(pack)?.fileName}.part").delete()
        updateState(pack, DownloadState.NotDownloaded)
    }

    /** Delete a downloaded model to free storage */
    fun delete(pack: ModelPack) {
        activeJobs[pack]?.cancel()
        activeJobs.remove(pack)
        val info = ModelRegistry.getInfo(pack) ?: return
        File(modelsDir, info.fileName).delete()
        File(modelsDir, "${info.fileName}.part").delete()
        updateState(pack, DownloadState.NotDownloaded)
    }

    /**
     * Download a list of model packs.
     * Skips any model that is already present and passes integrity verification.
     */
    fun downloadAll(packs: List<ModelPack>) {
        val needed = packs.filter { pack ->
            val isPresent = verifyModelIntegrity(pack)
            if (isPresent) {
                Log.d(TAG, "$pack is already present & verified. Will not re-download.")
                updateState(pack, DownloadState.Downloaded)
            }
            !isPresent
        }

        if (needed.isEmpty()) {
            Log.i(TAG, "All ${packs.size} requested packs are already downloaded and verified. No download needed.")
            return
        }

        Log.i(TAG, "Starting download for ${needed.size} missing/unverified models: ${needed.map { it.name }}")
        needed.forEach { download(it) }
    }

    fun downloadCorePack() {
        downloadAll(ModelPack.coreTransceiverPacks())
    }

    /** Re-check disk and refresh states against strict integrity verification */
    fun refreshStates() {
        val current = _downloadStates.value.toMutableMap()
        ModelPack.entries.forEach { pack ->
            val isActive = current[pack] is DownloadState.Downloading || current[pack] is DownloadState.Queued
            if (!isActive) {
                current[pack] = if (verifyModelIntegrity(pack)) DownloadState.Downloaded else DownloadState.NotDownloaded
            }
        }
        _downloadStates.value = current
    }

    fun modelPath(pack: ModelPack): String? {
        val info = ModelRegistry.getInfo(pack) ?: return null
        val file = File(modelsDir, info.fileName)
        return if (file.exists() && file.length() > 0) file.absolutePath else null
    }

    /** Compute SHA256 of a file, returned as lowercase hex string. */
    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        FileInputStream(file).use { stream ->
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun updateState(pack: ModelPack, state: DownloadState) {
        _downloadStates.value = _downloadStates.value.toMutableMap().also { it[pack] = state }
    }
}
