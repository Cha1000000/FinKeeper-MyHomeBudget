package ru.homebudget.finkeeper.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

expect val isDesktop: Boolean

/** Платформа для сервера (заголовок X-App-Platform): android, ios или desktop */
expect val appPlatform: String

@Composable
expect fun AppLogoIcon(modifier: Modifier = Modifier)
