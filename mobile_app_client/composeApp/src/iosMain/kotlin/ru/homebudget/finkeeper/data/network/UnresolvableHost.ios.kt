package ru.homebudget.finkeeper.data.network

import io.ktor.client.engine.darwin.DarwinHttpRequestException
import platform.Foundation.NSURLErrorCannotFindHost
import platform.Foundation.NSURLErrorDNSLookupFailed

// Движок Darwin отдаёт ошибки NSURLSession как DarwinHttpRequestException с исходной NSError
internal actual fun Throwable.isUnresolvableHost(): Boolean =
    this is DarwinHttpRequestException &&
        (origin.code == NSURLErrorCannotFindHost || origin.code == NSURLErrorDNSLookupFailed)
