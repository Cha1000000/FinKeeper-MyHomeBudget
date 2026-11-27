package ru.homebudget.finkeeper.util

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

import java.io.File

actual val isDesktop: Boolean = true

actual val appPlatform: String = "desktop"

actual val appDeviceName: String by lazy {
    val osName = System.getProperty("os.name", "").trim()
    val lower = osName.lowercase()
    val osLabel = when {
        lower.contains("mac") || lower.contains("darwin") -> "macOS"
        lower.contains("win") -> "Windows"
        lower.contains("linux") -> detectLinuxDistroName() ?: "Linux"
        osName.isNotBlank() -> osName
        else -> "Desktop"
    }
    if (osLabel == "Desktop") "Desktop" else "Desktop - $osLabel"
}

private fun detectLinuxDistroName(): String? {
    return try {
        val osRelease = File("/etc/os-release")
        if (!osRelease.exists()) return null
        val lines = osRelease.readLines(Charsets.UTF_8)
        val rawName = lines.firstOrNull { it.startsWith("NAME=") }
            ?.substringAfter("NAME=")
            ?.trim()
            ?.trim('"', '\'')
            ?.takeIf { it.isNotBlank() }
            ?: return null
        if (rawName.contains("linux", ignoreCase = true)) rawName else "Linux ($rawName)"
    } catch (_: Exception) {
        null
    }
}

@Composable
actual fun AppLogoIcon(modifier: Modifier) {
    Image(
        painter = painterResource("icon.png"),
        contentDescription = "FinKeeper24",
        modifier = modifier.size(28.dp),
    )
}

@Composable
actual fun ConfigureSystemBars(isDark: Boolean) {
    // Not used on desktop
}
