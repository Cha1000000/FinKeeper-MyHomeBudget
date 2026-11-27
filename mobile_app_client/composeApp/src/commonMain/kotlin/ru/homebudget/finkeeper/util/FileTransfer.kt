package ru.homebudget.finkeeper.util

/**
 * Кроссплатформенное сохранение выгруженных отчётов (.xlsx, .csv) и резервных копий (.json),
 * а также чтение локального .json файла резервной копии.
 */
expect object FileTransfer {
    /**
     * Сохраняет файл на устройство (на Desktop — через диалог выбора пути или ~/Downloads,
     * на Android — в папку Загрузки / Downloads).
     * Возвращает отображаемый путь сохранённого файла или null, если пользователь отменил.
     */
    fun saveDownloadedFile(
        fileName: String,
        bytes: ByteArray,
    ): String?

    /**
     * Открывает выбор .json файла резервной копии (на Desktop — FileDialog,
     * на Android — ищет последний экспортированный finkeeper24-backup-*.json в Загрузках).
     * Возвращает пару (имя файла, JSON-строка) или null.
     */
    fun pickBackupJsonFile(): Pair<String, String>?
}
