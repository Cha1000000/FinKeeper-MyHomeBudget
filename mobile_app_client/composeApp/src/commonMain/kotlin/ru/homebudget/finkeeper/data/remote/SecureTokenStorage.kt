package ru.homebudget.finkeeper.data.remote

interface SecureTokenStorage {
    var accessToken: String?
    var refreshToken: String?

    fun clear()
}
