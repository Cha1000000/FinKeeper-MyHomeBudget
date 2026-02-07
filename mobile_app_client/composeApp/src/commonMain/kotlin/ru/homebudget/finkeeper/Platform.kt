package ru.homebudget.finkeeper

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform