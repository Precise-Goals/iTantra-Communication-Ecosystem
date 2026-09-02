package com.itantra.core.download

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
     */
    fun extractTarBz2(archiveFile: File, destDir: File) {
        destDir.mkdirs()
        BZip2CompressorInputStream(archiveFile.inputStream().buffered()).use { bz2 ->
            TarArchiveInputStream(bz2).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    if (!tar.canReadEntryData(entry)) {
                        entry = tar.nextEntry
                        continue
                    }
                    val relativePath = stripTopLevelDir(entry.name)
                    if (relativePath.isNotBlank()) {
                        val outFile = File(destDir, relativePath)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out ->
                                tar.copyTo(out)
                            }
                        }
                    }
                    entry = tar.nextEntry
                }
            }
        }
    }

    /** sherpa-onnx tar bundles are named e.g. "vits-piper-hi_IN-pratham-medium/model.onnx" — drop that first segment. */
    private fun stripTopLevelDir(entryName: String): String {
        val normalized = entryName.replace('\\', '/')
        val firstSlash = normalized.indexOf('/')
        return if (firstSlash in 0 until normalized.lastIndex) normalized.substring(firstSlash + 1) else ""
    }
}
