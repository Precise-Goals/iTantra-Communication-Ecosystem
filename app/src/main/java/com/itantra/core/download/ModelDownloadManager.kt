package com.itantra.core.download

import android.content.Context
import android.util.Log
import com.itantra.domain.model.DownloadState
import com.itantra.domain.model.ModelPack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Manages background model downloads with real-time progress tracking.
 *
 * - Supports HTTP downloads only — a failed download is reported as [DownloadState.Failed],
 *   never masked with a synthetic placeholder file.
 * - Verifies real SHA-256 after every download: against [ModelRegistry.ModelInfo.sha256] when
 *   known (HuggingFace LFS hashes are also captured live from the `X-Linked-ETag`/`ETag` response
 *   header), or against [ModelHashStore]'s trust-on-first-download record otherwise.
 * - `.tar.bz2` bundle packs (the sherpa-onnx TTS voices + shared espeak-ng-data) are extracted
 *   via [ArchiveExtractor] and the archive is deleted, leaving only the extracted directory.
 * - Persists downloaded status on disk in context.filesDir/models
 * - Emits StateFlow<Map<ModelPack, DownloadState>> for reactive UI observation
 */
class ModelDownloadManager(private val context: Context) {

    private val TAG = "ModelDownloadManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val hashStore = ModelHashStore(context)

    private val _downloadStates = MutableStateFlow<Map<ModelPack, DownloadState>>(
        ModelPack.entries.associateWith { pack ->
            if (isModelPresent(pack)) DownloadState.Downloaded else DownloadState.NotDownloaded
        }
    )
    val downloadStates: StateFlow<Map<ModelPack, DownloadState>> =
        _downloadStates.asStateFlow()

    /** Directory where model files are stored on device */
    val modelsDir: File
        get() = File(context.filesDir, "models").also { it.mkdirs() }

    /** Check if a model (or, for bundle packs, its extracted directory) is present on disk. */
    fun isModelPresent(pack: ModelPack): Boolean {
        val info = ModelRegistry.getInfo(pack) ?: return false
        if (info.downloadUrl.isBlank()) return false // unsupported pack, see ModelRegistry doc

        if (info.extractDirName != null) {
            val dir = File(modelsDir, info.extractDirName)
            return dir.exists() && dir.listFiles()?.isNotEmpty() == true
        }

        val file = File(modelsDir, info.fileName)
        if (!file.exists() || file.length() <= 0) return false
        val auxName = info.auxFileName ?: return true
        val auxFile = File(modelsDir, auxName)
        return auxFile.exists() && auxFile.length() > 0
    }

    /** Check if all packs in the given list are present */
    fun areAllPresent(packs: List<ModelPack>): Boolean = packs.all { isModelPresent(it) }

    /** Start downloading a pack. Reports [DownloadState.Failed] honestly on any error — never fabricates a file. */
    fun download(pack: ModelPack) {
        val info = ModelRegistry.getInfo(pack) ?: run {
            Log.e(TAG, "No registry entry for $pack")
            return
        }
        if (info.downloadUrl.isBlank()) {
            Log.w(TAG, "$pack has no known download source — not attempting")
            updateState(pack, DownloadState.Failed("No offline TTS source available for this language"))
            return
        }

        scope.launch {
            updateState(pack, DownloadState.Queued)

            val mainOk = downloadFile(pack, info.downloadUrl, info.fileName, info.sizeBytes, info.sha256)
            if (!mainOk) return@launch

            if (info.auxUrl != null && info.auxFileName != null) {
                val auxOk = downloadFile(pack, info.auxUrl, info.auxFileName, sizeBytes = 0L, expectedSha256 = null, isAux = true)
                if (!auxOk) return@launch
            }

            if (info.extractDirName != null) {
                val archiveFile = File(modelsDir, info.fileName)
                val destDir = File(modelsDir, info.extractDirName)
                try {
                    ArchiveExtractor.extractTarBz2(archiveFile, destDir)
                    archiveFile.delete()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to extract $pack: ${e.message}", e)
                    destDir.deleteRecursively()
                    archiveFile.delete()
                    updateState(pack, DownloadState.Failed("Archive extraction failed: ${e.message}"))
                    return@launch
                }
            }

            updateState(pack, DownloadState.Downloaded)
            Log.i(TAG, "$pack downloaded and verified successfully")
        }
    }

    /**
     * Downloads a single file for [pack] via real HTTP, verifies its SHA-256, and returns true on
     * success. On any failure (network, HTTP status, integrity mismatch), marks the pack
     * [DownloadState.Failed] with the real reason and returns false — never writes a placeholder
     * file in place of the real download.
     *
     * Integrity check order:
     *  1. [expectedSha256] from the registry, when the source is known upfront (e.g. GitHub's
     *     published release-asset digest).
     *  2. The `X-Linked-ETag`/`ETag` response header, when the host is HuggingFace (LFS files
     *     serve their real SHA-256 this way).
     *  3. [ModelHashStore] trust-on-first-download: no authoritative hash exists for this source,
     *     so the first successful download's hash becomes the baseline; a later re-download that
     *     doesn't match it is treated as corruption/tampering and rejected.
     */
    private suspend fun downloadFile(
        pack: ModelPack,
        url: String,
        fileName: String,
        sizeBytes: Long,
        expectedSha256: String?,
        isAux: Boolean = false
    ): Boolean {
        val destFile = File(modelsDir, fileName)
        val partFile = File(modelsDir, "$fileName.part")
        var remoteHash: String? = null

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "iTantra-Android/2.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw java.io.IOException("HTTP ${response.code}")
                }
                remoteHash = HashUtils.normalizeHashHeader(
                    response.header("x-linked-etag") ?: response.header("etag")
                )
                val body = response.body ?: throw java.io.IOException("Empty response body")
                val contentLength = body.contentLength().takeIf { it > 0 } ?: sizeBytes

                partFile.outputStream().use { outputStream ->
                    body.byteStream().use { inputStream ->
                        val buffer = ByteArray(32 * 1024)
                        var downloadedBytes = 0L
                        var bytesRead: Int
                        var lastUpdate = 0L

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            if (!isAux) {
                                val now = System.currentTimeMillis()
                                if (now - lastUpdate > 100 || downloadedBytes >= contentLength) {
                                    lastUpdate = now
                                    val totalTarget = if (contentLength > 0) maxOf(contentLength, downloadedBytes) else sizeBytes
                                    val progress = ((downloadedBytes.toDouble() / totalTarget.toDouble()) * 100.0).toFloat().coerceIn(0f, 99f)
                                    updateState(
                                        pack,
                                        DownloadState.Downloading(
                                            progressPercent = progress,
                                            downloadedBytes = downloadedBytes,
                                            totalBytes = totalTarget
                                        )
                                    )
                                }
                            }
                        }
                        outputStream.flush()
                    }
                }
            }

            // Integrity check, in priority order: registry-known hash, then HF header, then TOFU.
            val computedHash = HashUtils.computeSha256(partFile)
            val authoritative = expectedSha256 ?: remoteHash
            if (authoritative != null) {
                if (!HashUtils.hashesMatch(computedHash, authoritative)) {
                    partFile.delete()
                    Log.e(TAG, "Integrity check FAILED for $pack ($fileName): expected $authoritative, got $computedHash")
                    updateState(pack, DownloadState.Failed("Integrity check failed"))
                    return false
                }
            } else {
                val previouslyStored = hashStore.get(fileName)
                if (previouslyStored != null && !HashUtils.hashesMatch(computedHash, previouslyStored)) {
                    partFile.delete()
                    Log.e(TAG, "Integrity check FAILED for $pack ($fileName): differs from previously-downloaded copy")
                    updateState(pack, DownloadState.Failed("Integrity check failed (differs from last verified download)"))
                    return false
                }
            }
            hashStore.set(fileName, computedHash)

            if (destFile.exists()) destFile.delete()
            val renamed = partFile.renameTo(destFile)
            if (!renamed) {
                partFile.copyTo(destFile, overwrite = true)
                partFile.delete()
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for $pack ($fileName): ${e.message}")
            if (partFile.exists()) partFile.delete()
            updateState(pack, DownloadState.Failed(e.message ?: "Download failed"))
            return false
        }
    }

    /** Cancel an in-progress download */
    fun cancel(pack: ModelPack) {
        val info = ModelRegistry.getInfo(pack) ?: return
        File(modelsDir, "${info.fileName}.part").delete()
        updateState(pack, DownloadState.NotDownloaded)
    }

    /** Delete a downloaded model (files or extracted bundle directory) to free storage */
    fun delete(pack: ModelPack) {
        val info = ModelRegistry.getInfo(pack) ?: return
        if (info.extractDirName != null) {
            File(modelsDir, info.extractDirName).deleteRecursively()
        }
        File(modelsDir, info.fileName).delete()
        File(modelsDir, "${info.fileName}.part").delete()
        info.auxFileName?.let { auxName ->
            File(modelsDir, auxName).delete()
            File(modelsDir, "$auxName.part").delete()
            hashStore.clear(auxName)
        }
        hashStore.clear(info.fileName)
        updateState(pack, DownloadState.NotDownloaded)
    }

    /** Download a list of packs sequentially */
    fun downloadAll(packs: List<ModelPack>) {
        packs.forEach { download(it) }
    }

    /** Download all core mandatory transceiver packs */
    fun downloadCorePack() {
        downloadAll(ModelPack.coreTransceiverPacks())
    }

    /** Refresh state by re-checking disk */
    fun refreshStates() {
        val current = _downloadStates.value.toMutableMap()
        ModelPack.entries.forEach { pack ->
            if (current[pack] !is DownloadState.Downloading && isModelPresent(pack)) {
                current[pack] = DownloadState.Downloaded
            } else if (current[pack] is DownloadState.Downloaded && !isModelPresent(pack)) {
                current[pack] = DownloadState.NotDownloaded
            }
        }
        _downloadStates.value = current
    }

    /**
     * Returns the path to a downloaded model, or null if not present. For bundle packs
     * (`extractDirName != null`) this is the extracted directory, not the (deleted) archive file.
     */
    fun modelPath(pack: ModelPack): String? {
        val info = ModelRegistry.getInfo(pack) ?: return null
        if (info.extractDirName != null) {
            val dir = File(modelsDir, info.extractDirName)
            return if (dir.exists()) dir.absolutePath else null
        }
        val file = File(modelsDir, info.fileName)
        return if (file.exists()) file.absolutePath else null
    }

    private fun updateState(pack: ModelPack, state: DownloadState) {
        _downloadStates.value = _downloadStates.value.toMutableMap().also { it[pack] = state }
    }
}
