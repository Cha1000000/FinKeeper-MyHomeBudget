package ru.homebudget.finkeeper.data.remote

interface SocialAuthLauncher {
    val clientType: String

    fun open(url: String): Boolean
}
