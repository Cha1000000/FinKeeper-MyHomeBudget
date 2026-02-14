package ru.homebudget.finkeeper.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

expect val isDesktop: Boolean

@Composable
expect fun AppLogoIcon(modifier: Modifier = Modifier)
