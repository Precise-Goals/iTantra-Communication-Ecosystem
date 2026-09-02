package com.itantra.core.download

import android.util.Log
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Extracts the `.tar.bz2` bundles sherpa-onnx publishes (each containing model.onnx + tokens.txt
 * for a TTS voice, or the shared espeak-ng-data directory). Pure JVM via Apache Commons Compress —
 * Android has no built-in tar/bzip2 support.
 */
object ArchiveExtractor {

    /**
     * Extracts [archiveFile] (a .tar.bz2) into [destDir], flattening the archive's own top-level
     * directory (sherpa-onnx bundles wrap everything in one folder named after the release asset;
     * we don't want that extra nesting under our own per-pack directory).
     *
     * @param excludePrefixes Skip any entry whose stripped relative path starts with one of these
     *   (after stripping the archive's own top-level folder). Used to drop each Piper voice
     *   bundle's own embedded `espeak-ng-data/` copy — confirmed present by inspecting a real
     *   bundle's contents — since [ModelPack.ESPEAK_NG_DATA] downloads one shared copy already;
     *   keeping both would waste ~7MB per language for no benefit.
     */
    fun extractTarBz2(archiveFile: File, destDir: File, excludePrefixes: List<String> = emptyList()) {
        destDir.mkdirs()
        var writtenCount = 0
        BZip2CompressorInputStream(archiveFile.inputStream().buffered()).use { bz2 ->
            TarArchiveInputStream(bz2).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    if (!tar.canReadEntryData(entry)) {
                        Log.w("ArchiveExtractor", "Cannot read entry data, skipping: ${entry.name}")
                        entry = tar.nextEntry
                        continue
                    }
                    val relativePath = stripTopLevelDir(entry.name)
                    if (relativePath.isNotBlank() && excludePrefixes.none { relativePath.startsWith(it) }) {
                        val outFile = File(destDir, relativePath)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out -> tar.copyTo(out) }
                            writtenCount++
                        }
                    }
                    entry = tar.nextEntry
                }
            }
        }
        Log.i("ArchiveExtractor", "Extracted $writtenCount files from ${archiveFile.name} to ${destDir.path}")
    }

    /** sherpa-onnx tar bundles are named e.g. "vits-piper-hi_IN-pratham-medium/model.onnx" — drop that first segment. */
    private fun stripTopLevelDir(entryName: String): String {
        val normalized = entryName.replace('\\', '/')
        val firstSlash = normalized.indexOf('/')
        return if (firstSlash in 0 until normalized.lastIndex) normalized.substring(firstSlash + 1) else ""
    }
}
