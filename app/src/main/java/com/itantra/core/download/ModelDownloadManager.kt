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

    /** Check if a model file (and its companion aux file, if any) exists on disk and is non-empty */
    fun isModelPresent(pack: ModelPack): Boolean {
        val info = ModelRegistry.getInfo(pack) ?: return false
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

        scope.launch {
            updateState(pack, DownloadState.Queued)

            val mainOk = downloadFile(pack, info.downloadUrl, info.fileName, info.sizeBytes)
            if (!mainOk) return@launch

            if (info.auxUrl != null && info.auxFileName != null) {
                val auxOk = downloadFile(pack, info.auxUrl, info.auxFileName, sizeBytes = 0L, isAux = true)
                if (!auxOk) return@launch
            }

            updateState(pack, DownloadState.Downloaded)
            Log.i(TAG, "$pack downloaded successfully to ${File(modelsDir, info.fileName).path}")
        }
    }

    /**
     * Downloads a single file for [pack] via real HTTP. Returns true on success.
     * On any failure, marks the pack [DownloadState.Failed] with the real reason and returns false —
     * it never writes a placeholder file in place of the real download.
     */
    private suspend fun downloadFile(
        pack: ModelPack,
        url: String,
        fileName: String,
        sizeBytes: Long,
        isAux: Boolean = false
    ): Boolean {
        val destFile = File(modelsDir, fileName)
        val partFile = File(modelsDir, "$fileName.part")

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "iTantra-Android/2.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw java.io.IOException("HTTP ${response.code}")
                }
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

    /** Delete a downloaded model (and its aux file, if any) to free storage */
    fun delete(pack: ModelPack) {
        val info = ModelRegistry.getInfo(pack) ?: return
        File(modelsDir, info.fileName).delete()
        File(modelsDir, "${info.fileName}.part").delete()
        info.auxFileName?.let { auxName ->
            File(modelsDir, auxName).delete()
            File(modelsDir, "$auxName.part").delete()
        }
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

    /** Returns path to a downloaded model file, or null if not present */
    fun modelPath(pack: ModelPack): String? {
        val info = ModelRegistry.getInfo(pack) ?: return null
        val file = File(modelsDir, info.fileName)
        return if (file.exists()) file.absolutePath else null
    }

    private fun updateState(pack: ModelPack, state: DownloadState) {
        _downloadStates.value = _downloadStates.value.toMutableMap().also { it[pack] = state }
    }
}
