package ru.homebudget.finkeeper.data.network

import java.net.UnknownHostException

internal actual fun Throwable.isUnresolvableHost(): Boolean = this is UnknownHostException
