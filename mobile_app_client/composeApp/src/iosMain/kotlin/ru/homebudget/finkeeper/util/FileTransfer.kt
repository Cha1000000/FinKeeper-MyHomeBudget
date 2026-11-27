package ru.homebudget.finkeeper.util

actual object FileTransfer {
    actual fun saveDownloadedFile(
        fileName: String,
        bytes: ByteArray,
    ): String? = fileName

    actual fun pickBackupJsonFile(): Pair<String, String>? = null
}
