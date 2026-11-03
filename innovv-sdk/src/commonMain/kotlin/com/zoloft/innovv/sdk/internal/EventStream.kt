package com.zoloft.innovv.sdk.internal

import com.zoloft.innovv.sdk.InnovvLogLevel
import com.zoloft.innovv.sdk.InnovvLogger
import com.zoloft.innovv.sdk.InnovvNetworkException
import com.zoloft.innovv.sdk.model.CameraEvent
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

internal class EventStream(
    private val host: String,
    private val port: Int,
    private val reconnect: Boolean,
    private val logger: InnovvLogger,
) {
    fun events(): Flow<CameraEvent> = flow {
        var backoffMillis = INITIAL_BACKOFF_MILLIS
        while (currentCoroutineContext().isActive) {
            val selector = SelectorManager(Dispatchers.Default)
            var socket: Socket? = null
            try {
                socket = aSocket(selector).tcp().connect(host, port)
                backoffMillis = INITIAL_BACKOFF_MILLIS
                logger.log(InnovvLogLevel.INFO, "event stream connected to $host:$port")
                val input = socket.openReadChannel()
                val buffer = ByteArray(READ_BUFFER_SIZE)
                var pending = ""

                while (currentCoroutineContext().isActive) {
                    val read = input.readAvailable(buffer, 0, buffer.size)
                    if (read == -1) break
                    if (read == 0) continue

                    pending += buffer.decodeToString(0, read)
                    var end = pending.indexOf(END_TAG)
                    while (end >= 0) {
                        val message = pending.substring(0, end + END_TAG.length)
                        pending = pending.substring(end + END_TAG.length)
                        parseEvent(message)?.let { emit(it) }
                        end = pending.indexOf(END_TAG)
                    }
                    if (pending.length > MAX_PENDING_CHARS) {
                        logger.log(InnovvLogLevel.WARN, "event stream buffer overflow; dropping partial message")
                        pending = ""
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (!reconnect) {
                    throw InnovvNetworkException("event stream $host:$port failed", failure)
                }
                logger.log(
                    InnovvLogLevel.WARN,
                    "event stream $host:$port failed; reconnecting in ${backoffMillis}ms",
                    failure,
                )
            } finally {
                socket?.close()
                selector.close()
            }

            if (!reconnect) break
            delay(backoffMillis)
            backoffMillis = (backoffMillis * 2).coerceAtMost(MAX_BACKOFF_MILLIS)
        }
    }

    private companion object {
        const val READ_BUFFER_SIZE: Int = 4 * 1024
        const val MAX_PENDING_CHARS: Int = 64 * 1024
        const val INITIAL_BACKOFF_MILLIS: Long = 1_000L
        const val MAX_BACKOFF_MILLIS: Long = 30_000L
        const val END_TAG: String = "</Function>"
    }
}
