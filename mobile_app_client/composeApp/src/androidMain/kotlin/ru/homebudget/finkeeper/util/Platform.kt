package ru.homebudget.finkeeper.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

actual val isDesktop: Boolean = false

actual val appPlatform: String = "android"

@Composable
actual fun AppLogoIcon(modifier: Modifier) {
    // Not used on mobile
}

@Composable
actual fun ConfigureSystemBars(isDark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val context = view.context
    val activity = remember(context) {
        var ctx: Context? = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) break
            ctx = ctx.baseContext
        }
        ctx as? Activity
    }
    SideEffect {
        activity?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
        }
    }
}
