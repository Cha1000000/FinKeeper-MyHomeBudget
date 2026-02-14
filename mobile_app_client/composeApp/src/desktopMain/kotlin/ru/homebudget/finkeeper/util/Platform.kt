package ru.homebudget.finkeeper.util

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

actual val isDesktop: Boolean = true

@Composable
actual fun AppLogoIcon(modifier: Modifier) {
    Image(
        painter = painterResource("icon.png"),
        contentDescription = "FinKeeper",
        modifier = modifier.size(28.dp),
    )
}
