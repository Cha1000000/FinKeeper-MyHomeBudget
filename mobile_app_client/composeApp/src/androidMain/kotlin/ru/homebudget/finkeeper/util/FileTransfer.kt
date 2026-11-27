package ru.homebudget.finkeeper.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.koin.core.context.GlobalContext
import java.io.File

actual object FileTransfer {
    actual fun saveDownloadedFile(
        fileName: String,
        bytes: ByteArray,
    ): String? {
        return try {
            val context = GlobalContext.getOrNull()?.getOrNull<Context>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && context != null) {
                val mimeType = when {
                    fileName.endsWith(".xlsx", ignoreCase = true) ->
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    fileName.endsWith(".csv", ignoreCase = true) ->
                        "text/csv"
                    fileName.endsWith(".json", ignoreCase = true) ->
                        "application/json"
                    else -> "application/octet-stream"
                }
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        out.write(bytes)
                    }
                    return "Загрузки/$fileName"
                }
            }
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                ?.apply { mkdirs() }
            if (downloadsDir != null) {
                val outFile = File(downloadsDir, fileName)
                outFile.writeBytes(bytes)
                return outFile.absolutePath
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    actual fun pickBackupJsonFile(): Pair<String, String>? {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                ?: return null
            val candidate = downloadsDir
                .listFiles { file ->
                    file.isFile && file.name.endsWith(".json", ignoreCase = true) &&
                        file.name.contains("finkeeper24", ignoreCase = true)
                }
                ?.maxByOrNull { it.lastModified() }
                ?: downloadsDir
                    .listFiles { file -> file.isFile && file.name.endsWith(".json", ignoreCase = true) }
                    ?.maxByOrNull { it.lastModified() }
                ?: return null
            candidate.name to candidate.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}
