package ru.homebudget.finkeeper.data.remote

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

class IosSocialAuthLauncher : SocialAuthLauncher {
    override val clientType: String = "ios"

    override fun open(url: String): Boolean {
        val nsUrl = NSURL(string = url) ?: return false
        val app = UIApplication.sharedApplication
        if (!app.canOpenURL(nsUrl)) {
            return false
        }
        app.openURL(nsUrl)
        return true
    }
}
