package com.zoloft.innovv.sdk

/**
 * Configuration for an [InnovvClient].
 *
 * The defaults target the K5 access point: `http://192.168.1.254/`, TCP event
 * port `3333`, and conservative timeouts suitable for the camera's small HTTP
 * server.
 *
 * The camera control API is plain HTTP. On Android the application must allow
 * cleartext traffic for the camera host; on iOS the application must add the
 * corresponding App Transport Security exception. See `docs/platform-support.md`.
 *
 * @property baseUrl camera control base URL, always ending with `/`.
 * @property connectTimeoutMillis TCP connect timeout for HTTP requests.
 * @property requestTimeoutMillis total request timeout for HTTP requests.
 * @property socketTimeoutMillis socket read timeout for HTTP requests.
 * @property eventPort TCP port used by the camera event stream.
 * @property eventReconnect whether [InnovvClient.events] reconnects after a
 *   dropped connection with exponential backoff.
 * @property maxRetries number of retries for idempotent read requests. Write
 *   commands are never retried automatically.
 * @property userAgent HTTP `User-Agent` header. The camera ignores it, but it is
 *   useful in access-point logs.
 * @property logger optional SDK logger. The SDK never logs response bodies by
 *   default.
 */
public data class InnovvConfig(
    public val baseUrl: String = DEFAULT_BASE_URL,
    public val connectTimeoutMillis: Long = 3_000L,
    public val requestTimeoutMillis: Long = 8_000L,
    public val socketTimeoutMillis: Long = 15_000L,
    public val eventPort: Int = DEFAULT_EVENT_PORT,
    public val eventReconnect: Boolean = true,
    public val maxRetries: Int = 1,
    public val userAgent: String = "innovv-kmp-sdk/0.1.0",
    public val logger: InnovvLogger = InnovvLogger.None,
    public val trafficLogger: InnovvTrafficLogger? = null,
) {
    init {
        require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
            "baseUrl must start with http:// or https://"
        }
        require(connectTimeoutMillis > 0) { "connectTimeoutMillis must be positive" }
        require(requestTimeoutMillis > 0) { "requestTimeoutMillis must be positive" }
        require(socketTimeoutMillis > 0) { "socketTimeoutMillis must be positive" }
        require(eventPort in 1..65535) { "eventPort must be a valid TCP port" }
        require(maxRetries >= 0) { "maxRetries must not be negative" }
    }

    /** Default K5 camera address and control port. */
    public companion object {
        /** Default K5 control base URL. */
        public const val DEFAULT_BASE_URL: String = "http://192.168.1.254/"

        /** Default camera event-stream port. */
        public const val DEFAULT_EVENT_PORT: Int = 3333
    }
}

/** Severity used by [InnovvLogger]. */
public enum class InnovvLogLevel {
    /** Fine-grained diagnostic information. */
    DEBUG,

    /** Normal lifecycle information. */
    INFO,

    /** Recoverable failure or unexpected state. */
    WARN,

    /** Operation failed. */
    ERROR,
}

/**
 * Minimal logging abstraction used by the SDK.
 *
 * The stock application logged every HTTP body, which is a major performance
 * problem on the camera's small HTTP server. This SDK only logs request
 * methods, URLs and status codes.
 */
public interface InnovvLogger {
    /** Writes one log record. [throwable] is optional. */
    public fun log(level: InnovvLogLevel, message: String, throwable: Throwable? = null)

    /** Logger that discards all records. */
    public companion object {
        /** No-op logger. */
        public val None: InnovvLogger = object : InnovvLogger {
            override fun log(level: InnovvLogLevel, message: String, throwable: Throwable?) = Unit
        }
    }
}

/**
 * Optional full-traffic logger for diagnosing camera protocol problems.
 *
 * The callback is invoked on the caller's coroutine context. Implementations
 * must be cheap and non-blocking; use a queue or a background writer for file
 * output. Response bodies are capped by the SDK and sensitive Wi-Fi credential
 * responses are redacted before this callback is invoked.
 */
public interface InnovvTrafficLogger {
    /** Called immediately before an HTTP request is sent. */
    public fun logRequest(method: String, url: String)

    /** Called after a text response is read. */
    public fun logResponse(method: String, url: String, statusCode: Int, body: String)

    /** Called after a streaming response finishes. */
    public fun logStream(method: String, url: String, statusCode: Int, bytes: Long)

    /** Called when a request fails before a complete response is available. */
    public fun logError(
        method: String,
        url: String,
        message: String,
        throwable: Throwable? = null,
    )
}
