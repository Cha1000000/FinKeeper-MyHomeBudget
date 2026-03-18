package ru.homebudget.finkeeper.data.remote

import java.awt.Desktop
import java.net.URI

class DesktopSocialAuthLauncher : SocialAuthLauncher {
    override val clientType: String = "desktop"

    override fun open(url: String): Boolean {
        return try {
            if (!Desktop.isDesktopSupported()) {
                false
            } else {
                Desktop.getDesktop().browse(URI(url))
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
