package com.itantra.core.download

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Model Asset Extractor.
 *
 * Extracts bundled .onnx, .ort, and .ftz models from the APK's virtual assets
 * into physical files in [Context.getFilesDir]/models/.
 *
 * The C++ ONNX Runtime (ONNXRuntime-Android) requires raw, absolute physical
 * file paths to perform memory mapping (mmap) without blowing up the JVM heap.
 */
object ModelAssetExtractor {

    private const val TAG = "ModelAssetExtractor"

    fun getModelsDir(context: Context): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Runs asynchronously during Application.onCreate() to prepare physical model files.
     */
    fun extractAllBundledModelsAsync(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            extractBundledModels(context)
        }
    }

    /**
     * Synchronously/blocking scans assets and extracts any models into filesDir.
     */
    fun extractBundledModels(context: Context) {
        val modelsDir = getModelsDir(context)

        try {
            // Scan both root and "models/" subfolder in APK assets
            val assetFolders = listOf("", "models", "models/tts", "models/stt")
            for (folder in assetFolders) {
                val list = try {
                    context.assets.list(folder) ?: emptyArray()
                } catch (e: Exception) {
                    emptyArray()
                }

                for (name in list) {
                    if (name.endsWith(".onnx") || name.endsWith(".ort") || name.endsWith(".ftz") || name.endsWith(".bin")) {
                        val assetPath = if (folder.isEmpty()) name else "$folder/$name"
                        val destFile = File(modelsDir, name)
                        extractAssetFileIfNeeded(context, assetPath, destFile)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Asset extraction scan notice: ${e.message}")
        }
    }

    /**
     * Extracts a single asset file into a destination physical file if not already present.
     */
    fun extractAssetFileIfNeeded(context: Context, assetPath: String, destFile: File): Boolean {
        if (destFile.exists() && destFile.length() > 0) {
            // Already extracted on disk
            return true
        }

        return try {
            destFile.parentFile?.mkdirs()
            val tempFile = File(destFile.parentFile, "${destFile.name}.tmp")

            context.assets.open(assetPath).use { input: InputStream ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                if (destFile.exists()) destFile.delete()
                val renamed = tempFile.renameTo(destFile)
                if (!renamed) {
                    tempFile.copyTo(destFile, overwrite = true)
                    tempFile.delete()
                }
                Log.i(TAG, "Extracted asset '$assetPath' to physical file '${destFile.absolutePath}' (${destFile.length()} bytes)")
                true
            } else {
                tempFile.delete()
                false
            }
        } catch (e: Exception) {
            Log.d(TAG, "Asset '$assetPath' not bundled in APK assets (${e.message})")
            false
        }
    }

    /**
     * Retrieves the absolute physical path for a model, extracting it from assets first if needed.
     */
    fun getPhysicalModelPath(context: Context, fileName: String, assetFallbackPath: String? = null): String? {
        val destFile = File(getModelsDir(context), fileName)
        if (destFile.exists() && destFile.length() > 0) {
            return destFile.absolutePath
        }

        if (assetFallbackPath != null) {
            val success = extractAssetFileIfNeeded(context, assetFallbackPath, destFile)
            if (success && destFile.exists() && destFile.length() > 0) {
                return destFile.absolutePath
            }
        }
        return if (destFile.exists() && destFile.length() > 0) destFile.absolutePath else null
    }
}
