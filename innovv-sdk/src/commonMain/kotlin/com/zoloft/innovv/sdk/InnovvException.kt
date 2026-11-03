package com.zoloft.innovv.sdk

/** Base type for all errors raised by the SDK. */
public sealed class InnovvException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** The camera could not be reached or the HTTP exchange failed at transport level. */
public class InnovvNetworkException(
    message: String,
    cause: Throwable? = null,
) : InnovvException(message, cause)

/**
 * The camera returned a non-2xx HTTP status.
 *
 * @property statusCode HTTP status code.
 * @property url requested URL.
 * @property body response body, truncated by the SDK only if the camera sends an
 *   unexpectedly large error page.
 */
public class InnovvHttpException(
    public val statusCode: Int,
    public val url: String,
    public val body: String,
) : InnovvException("HTTP $statusCode for $url")

/**
 * The camera accepted the HTTP request but reported a negative command status.
 *
 * The camera's XML status codes are documented in `docs/protocol.md`.
 *
 * @property commandId numeric camera command, for example `2001`.
 * @property status negative status returned by the camera.
 * @property body complete XML response.
 */
public class InnovvCommandException(
    public val commandId: Int,
    public val status: Int,
    public val body: String,
) : InnovvException("Camera command $commandId failed with status $status")

/** The camera response did not have the expected shape or value. */
public class InnovvParseException(
    message: String,
    cause: Throwable? = null,
) : InnovvException(message, cause)
