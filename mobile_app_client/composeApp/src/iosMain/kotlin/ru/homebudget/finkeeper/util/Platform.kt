package ru.homebudget.finkeeper.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

actual val isDesktop: Boolean = false

@Composable
actual fun AppLogoIcon(modifier: Modifier) {
    // Not used on mobile
}
