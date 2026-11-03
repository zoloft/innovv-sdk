package com.zoloft.innovv.sdk.internal

import com.zoloft.innovv.sdk.InnovvConfig
import com.zoloft.innovv.sdk.InnovvHttpException
import com.zoloft.innovv.sdk.InnovvLogLevel
import com.zoloft.innovv.sdk.InnovvNetworkException
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal fun createInnovvHttpClient(config: InnovvConfig): HttpClient =
    createPlatformHttpClient {
        expectSuccess = false
        followRedirects = false
        install(HttpTimeout) {
            connectTimeoutMillis = config.connectTimeoutMillis
            requestTimeoutMillis = config.requestTimeoutMillis
            socketTimeoutMillis = config.socketTimeoutMillis
        }
        defaultRequest {
            header(HttpHeaders.UserAgent, config.userAgent)
        }
    }

internal class HttpTransport(
    private val client: HttpClient,
    private val config: InnovvConfig,
) : AutoCloseable {
    private val logger = config.logger
    private val maxRetries = config.maxRetries

    suspend fun getText(url: String, retryable: Boolean = true): String {
        val response = execute(url, retryable)
        return response.bodyAsText()
    }

    suspend fun getBytes(url: String): ByteArray {
        val response = execute(url, retryable = true)
        return response.bodyAsBytes()
    }

    /**
     * Streams [url] to [onChunk]. The callback is invoked with each received
     * chunk; the SDK never buffers the entire media file.
     */
    suspend fun stream(url: String, onChunk: suspend (ByteArray) -> Unit) {
        val response = execute(url, retryable = false)
        val channel = response.bodyAsChannel()
        val buffer = ByteArray(STREAM_BUFFER_SIZE)
        try {
            while (true) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read == -1) break
                if (read > 0) {
                    onChunk(buffer.copyOf(read))
                }
            }
        } finally {
            channel.cancel(CancellationException("stream complete"))
        }
    }

    private suspend fun execute(url: String, retryable: Boolean): HttpResponse {
        var attempt = 0
        while (true) {
            try {
                logger.log(InnovvLogLevel.DEBUG, "GET $url")
                val response = client.get(url)
                if (response.status.isSuccess()) {
                    return response
                }

                val body = try {
                    response.bodyAsText()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    ""
                }
                if (retryable && response.status.value in 500..599 && attempt < maxRetries) {
                    attempt += 1
                    delay(backoffMillis(attempt))
                    continue
                }
                logger.log(InnovvLogLevel.WARN, "HTTP ${response.status.value} for $url")
                throw InnovvHttpException(response.status.value, url, body.take(MAX_ERROR_BODY))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (http: InnovvHttpException) {
                throw http
            } catch (failure: Exception) {
                if (retryable && attempt < maxRetries) {
                    attempt += 1
                    logger.log(
                        InnovvLogLevel.WARN,
                        "GET $url failed (attempt $attempt), retrying",
                        failure,
                    )
                    delay(backoffMillis(attempt))
                    continue
                }
                throw InnovvNetworkException("GET $url failed", failure)
            }
        }
    }

    override fun close() {
        client.close()
    }

    private fun backoffMillis(attempt: Int): Long = 150L * attempt

    private companion object {
        const val STREAM_BUFFER_SIZE: Int = 64 * 1024
        const val MAX_ERROR_BODY: Int = 8 * 1024
    }
}
