package com.zoloft.innovv.sdk.internal

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

internal expect fun createPlatformHttpClient(
    configure: HttpClientConfig<*>.() -> Unit,
): HttpClient
