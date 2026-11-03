package com.zoloft.innovv.sdk

import com.zoloft.innovv.sdk.internal.HttpTransport
import com.zoloft.innovv.sdk.internal.createInnovvHttpClient

/** Entry point for the INNOVV K5 SDK. */
public object InnovvSdk {
    /** SDK version. */
    public const val VERSION: String = "0.1.0"

    /**
     * Creates a camera client with the default K5 address and engine for the
     * current platform.
     *
     * The caller owns the returned client and must call [InnovvClient.close]
     * when finished.
     */
    public fun createClient(config: InnovvConfig = InnovvConfig()): InnovvClient {
        val httpClient = createInnovvHttpClient(config)
        return InnovvClient(
            config = config,
            transport = HttpTransport(httpClient, config),
        )
    }
}
