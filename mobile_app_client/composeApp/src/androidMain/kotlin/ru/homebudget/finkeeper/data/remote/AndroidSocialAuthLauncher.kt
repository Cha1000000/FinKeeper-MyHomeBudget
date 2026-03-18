package ru.homebudget.finkeeper.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri

class AndroidSocialAuthLauncher(
    private val context: Context,
) : SocialAuthLauncher {
    override val clientType: String = "android"

    override fun open(url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
