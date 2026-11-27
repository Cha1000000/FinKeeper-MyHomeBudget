package ru.homebudget.finkeeper.util

import java.awt.FileDialog
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.io.File
import java.io.FilenameFilter
import javax.swing.SwingUtilities

actual object FileTransfer {
    private val isLinux: Boolean
        get() = System.getProperty("os.name", "").lowercase().contains("linux")

    private fun hasCommand(cmd: String): Boolean =
        try {
            val proc = ProcessBuilder("which", cmd).redirectErrorStream(true).start()
            proc.waitFor() == 0
        } catch (_: Exception) {
            false
        }

    private fun runNativeLinuxDialog(command: List<String>): Pair<Boolean, String?> {
        return try {
            val proc = ProcessBuilder(command).start()
            val output = proc.inputStream.bufferedReader(Charsets.UTF_8).readText().trim()
            val exitCode = proc.waitFor()
            if (exitCode == 0 && output.isNotBlank()) {
                true to output
            } else {
                // Пользователь отменил диалог
                true to null
            }
        } catch (_: Exception) {
            false to null
        }
    }

    private fun pickSavePathLinux(defaultFile: File): Pair<Boolean, String?> {
        if (hasCommand("kdialog")) {
            return runNativeLinuxDialog(
                listOf("kdialog", "--title", "Сохранить файл", "--getsavefilename", defaultFile.absolutePath),
            )
        }
        if (hasCommand("zenity")) {
            return runNativeLinuxDialog(
                listOf(
                    "zenity",
                    "--file-selection",
                    "--save",
                    "--confirm-overwrite",
                    "--title=Сохранить файл",
                    "--filename=${defaultFile.absolutePath}",
                ),
            )
        }
        return false to null
    }

    private fun pickOpenJsonPathLinux(startDir: File): Pair<Boolean, String?> {
        if (hasCommand("kdialog")) {
            return runNativeLinuxDialog(
                listOf("kdialog", "--title", "Выберите файл резервной копии", "--getopenfilename", startDir.absolutePath, "*.json"),
            )
        }
        if (hasCommand("zenity")) {
            return runNativeLinuxDialog(
                listOf(
                    "zenity",
                    "--file-selection",
                    "--title=Выберите файл резервной копии",
                    "--filename=${startDir.absolutePath}/",
                    "--file-filter=*.json",
                ),
            )
        }
        return false to null
    }

    private fun restoreMainWindowFocus() {
        runCatching {
            SwingUtilities.invokeLater {
                desktopAwtWindow?.let { win ->
                    win.toFront()
                    win.requestFocus()
                }
            }
        }
    }

    actual fun saveDownloadedFile(
        fileName: String,
        bytes: ByteArray,
    ): String? {
        val downloadsDir = File(System.getProperty("user.home"), "Downloads").apply { mkdirs() }
        val defaultFile = File(downloadsDir, fileName)
        return try {
            if (!GraphicsEnvironment.isHeadless()) {
                if (isLinux) {
                    val (handled, chosenPath) = pickSavePathLinux(defaultFile)
                    if (handled) {
                        restoreMainWindowFocus()
                        if (chosenPath == null) return null
                        val outFile = File(chosenPath)
                        outFile.parentFile?.mkdirs()
                        outFile.writeBytes(bytes)
                        return outFile.absolutePath
                    }
                }
                val dialog = FileDialog(null as Frame?, "Сохранить файл", FileDialog.SAVE)
                try {
                    dialog.directory = downloadsDir.absolutePath
                    dialog.file = fileName
                    dialog.isVisible = true
                    val dir = dialog.directory
                    val chosen = dialog.file
                    if (dir == null || chosen == null) return null
                    val outFile = File(dir, chosen)
                    outFile.writeBytes(bytes)
                    outFile.absolutePath
                } finally {
                    dialog.dispose()
                    restoreMainWindowFocus()
                }
            } else {
                defaultFile.writeBytes(bytes)
                defaultFile.absolutePath
            }
        } catch (_: Exception) {
            defaultFile.writeBytes(bytes)
            defaultFile.absolutePath
        }
    }

    actual fun pickBackupJsonFile(): Pair<String, String>? {
        val downloadsDir = File(System.getProperty("user.home"), "Downloads").apply { mkdirs() }
        return try {
            if (GraphicsEnvironment.isHeadless()) return null
            if (isLinux) {
                val (handled, chosenPath) = pickOpenJsonPathLinux(downloadsDir)
                if (handled) {
                    restoreMainWindowFocus()
                    if (chosenPath == null) return null
                    val file = File(chosenPath)
                    if (!file.exists() || !file.isFile) return null
                    return file.name to file.readText(Charsets.UTF_8)
                }
            }
            val dialog = FileDialog(null as Frame?, "Выберите файл резервной копии", FileDialog.LOAD)
            try {
                dialog.directory = downloadsDir.absolutePath
                dialog.filenameFilter = FilenameFilter { _, name -> name.lowercase().endsWith(".json") }
                dialog.isVisible = true
                val dir = dialog.directory ?: return null
                val chosen = dialog.file ?: return null
                val file = File(dir, chosen)
                if (!file.exists() || !file.isFile) return null
                file.name to file.readText(Charsets.UTF_8)
            } finally {
                dialog.dispose()
                restoreMainWindowFocus()
            }
        } catch (_: Exception) {
            null
        }
    }
}
