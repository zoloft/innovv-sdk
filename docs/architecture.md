# Architecture

This document describes the internal structure of the SDK, why it is shaped
this way, and how a replacement application should use it.

## 1. Goals

The SDK exists to make a fast replacement client possible without re-deriving
the K5 protocol on Android and iOS. Its design goals are:

1. **One common Kotlin API** for Android, iOS, and JVM.
2. **Typed camera operations** instead of raw URL strings in application code.
3. **Streaming media**, never loading a whole video into memory.
4. **Predictable performance**: no response-body logging, no unnecessary
   request fan-out, explicit timeouts and retry rules.
5. **Tolerant protocol parsing** for the K5's non-standard XML.
6. **A documented, explicit public API** (`explicitApi()` is enabled).
7. **Publishable KMP artifacts** with Maven metadata and an iOS framework.

## 2. Module layout

```
innovv-sdk/
├── innovv-sdk/                         # the KMP library module
│   └── src/
│       ├── commonMain/kotlin/com/zoloft/innovv/sdk/
│       │   ├── InnovvSdk.kt            # factory
│       │   ├── InnovvClient.kt         # public camera operations
│       │   ├── InnovvConfig.kt         # configuration + logger
│       │   ├── InnovvException.kt      # error hierarchy
│       │   ├── K5Commands.kt           # numeric command constants
│       │   ├── model/                  # public data models
│       │   └── internal/               # HTTP, TCP, XML, parsers
│       ├── androidMain/                # OkHttp engine + manifest
│       ├── appleMain/                  # Darwin engine
│       ├── jvmMain/                    # CIO engine
│       └── commonTest/                 # parser + MockEngine tests
├── docs/                               # architecture/API/protocol docs
├── rea/                                # reverse-engineering evidence
└── gradle/libs.versions.toml           # version catalog
```

## 3. Layers

```
┌──────────────────────────────────────────────────────────────┐
│ Application (Compose/SwiftUI/CLI)                            │
├──────────────────────────────────────────────────────────────┤
│ Public API                                                   │
│   InnovvSdk → InnovvClient                                   │
│   Models: CameraFile, CameraSettings, CameraEvent, …         │
├──────────────────────────────────────────────────────────────┤
│ Protocol / parsing                                           │
│   Command URL builder, XML extractor, response parsers       │
├──────────────────────────────────────────────────────────────┤
│ Transport                                                    │
│   HttpTransport (Ktor client)                                │
│   EventStream (Ktor raw TCP on port 3333)                    │
├──────────────────────────────────────────────────────────────┤
│ Platform engines                                             │
│   Android: OkHttp   iOS: Darwin   JVM: CIO                   │
└──────────────────────────────────────────────────────────────┘
```

### 3.1 Public API

`InnovvClient` is the only stateful public object. It exposes suspend functions
for one camera operation each, plus `events()` for the TCP event stream. The
client does not own a UI lifecycle; the application decides when to create and
close it.

`InnovvSdk` is a stateless factory. `InnovvConfig` contains all tunables.

`K5Commands` provides the numeric IDs so application code does not use magic
numbers. The same IDs are also documented in `rea/data/api_commands.csv`.

### 3.2 Protocol layer

The K5 uses a query-string command protocol:

```
http://192.168.1.254/?custom=1&cmd=<id>[&par=<int>][&str=<string>]
```

`InnovvClient` builds this URL manually. `custom=1` is always first. The
protocol layer also contains:

- `Xml` — a small tag extractor, not a DOM parser.
- `parseCameraFile(s)` — `3015` file list.
- `parseSettingMenu` — `3031` menu.
- `parseResolutions` — `3030` list.
- `parseWifiNetworks` — `3038` scan.
- `parseGpsInfo` — `8058`.
- `parseMovieInfo` — `4005`.
- `parseEvent` — TCP `3333` event mapping.

**Why a tag extractor instead of an XML library?** The `3014` response is a
sequence of `<Function>` roots after the XML declaration. It is not a
single-root XML document. The camera's XML is otherwise simple and
non-namespaced. A tolerant extractor avoids a full XML dependency and handles
both the malformed `3014` response and normal documents. The trade-off is that
it is intentionally limited to the tags the SDK knows; unknown tags are
ignored, not preserved.

### 3.3 Transport layer

`HttpTransport` wraps a Ktor `HttpClient`:

- HTTP GET only for camera commands and media.
- `expectSuccess = false` so the SDK can inspect `<Status>` and HTTP statuses.
- `HttpTimeout` with configurable connect/request/socket timeouts.
- No logging interceptor. `InnovvLogger` logs method, URL, and status only;
  `str=` values for Wi-Fi credential commands are redacted.
- Read requests can be retried (`InnovvConfig.maxRetries`).
- Write commands are never retried automatically.
- Media downloads stream chunks to a callback; they are not buffered.

`EventStream` uses Ktor's raw TCP support (`ktor-network`) to connect to
`host:3333`. It accumulates bytes until `</Function>` and emits one
`CameraEvent` per complete message. It reconnects with exponential backoff when
configured. Cancellation closes the socket and the selector.

### 3.4 Platform engines

The common code declares:

```kotlin
internal expect fun createPlatformHttpClient(
    configure: HttpClientConfig<*>.() -> Unit,
): HttpClient
```

Each platform supplies its engine:

| Source set | Engine | Artifact |
|---|---|---|
| `androidMain` | OkHttp | `ktor-client-okhttp` |
| `appleMain` | Darwin | `ktor-client-darwin` |
| `jvmMain` | CIO | `ktor-client-cio` |

The raw TCP event stream uses `ktor-network`, which has JVM/Native support and
works on Android and iOS.

## 4. Data flow

### 4.1 Control request

```
caller
  → InnovvClient.getCurrentValues()
  → commandUrl(3014)
  → HttpTransport.getText(url)
  → Ktor engine
  → camera HTTP server
  → XML response
  → Xml.blocks("Function") + intTag("Cmd"/"Status")
  → Map<Int, Int>
```

### 4.2 Media download

```
caller
  → InnovvClient.download(file, onChunk)
  → HttpTransport.stream(file.url, onChunk)
  → Ktor response channel
  → readAvailable(buffer)
  → onChunk(buffer.copyOf(read))
  → platform file writer
```

The SDK never stores the full file. Backpressure is provided by the suspend
callback: when the platform write suspends, the read loop suspends too.

### 4.3 Event stream

```
TCP 3333
  → ByteReadChannel
  → decode chunk
  → append to pending string
  → while "</Function>" present:
       parseEvent(message)
       emit(CameraEvent)
```

The pending string has a 64 KiB cap to avoid unbounded growth if the camera
sends malformed data.

## 5. Concurrency and lifecycle

- `InnovvClient` is a long-lived object. Create one per camera connection; do
  not create one per request.
- Suspend functions are safe to call from the main thread; Ktor performs I/O
  off the UI thread.
- The HTTP engine uses its own connection pool. The SDK does not use a
  single-request dispatcher; the camera may still serialise requests
  internally, so a small application-level queue is recommended for writes.
- Read operations may be retried. Writes (record start/stop, delete, format,
  Wi-Fi changes) are not retried to avoid double actions.
- `events()` is a cold flow. Each collector opens its own TCP connection.
  Share one collection at the application level if only one event consumer is
  needed.
- `close()` closes the HTTP client. The event flow is cancelled by cancelling
  its collector scope.

## 6. Error model

```
InnovvException
├── InnovvNetworkException   transport failure / timeout
├── InnovvHttpException      non-2xx HTTP
├── InnovvCommandException   negative camera <Status>
└── InnovvParseException     unexpected/missing XML or non-JPEG image
```

The camera returns HTTP 200 even for command failures, so the SDK checks the
XML `<Status>` for every command except list-style responses (`3015`, `3031`,
`3038`, `2019`, `3029`, `3030`, `8058`). For `3014`, `<Status>` is the current
value, not an error (see `docs/protocol.md`).

## 7. K5-specific behavior

The SDK targets the K5, not every Novatek camera:

- Default base URL is `http://192.168.1.254/`.
- Event port is `3333`.
- `K5Commands` contains the K5 command IDs.
- `getSettings` and `getSettingMenu` match the K5's `3014`/`3031` behavior.
- `getFiles` matches the K5's per-file `<ALLFile>` wrapper.
- `getThumbnail`/`getPreview` use file-URL query modifiers (`4001`/`4002`).
- `getMovieInfo` uses the file URL path (`4005`), not `&str=`.
- `deleteFile` uses the camera path in `&str=` (`4003`).

A future generic Novatek client could add a camera profile abstraction, but the
current SDK deliberately keeps the K5 assumptions explicit.

## 8. Explicit API and binary compatibility

`explicitApi()` is enabled. Every public declaration has an explicit visibility
and return type. This makes accidental API leakage visible in review and keeps
the published ABI intentional.

The library is published as a KMP root module plus platform modules:

- `innovv-sdk`
- `innovv-sdk-android`
- `innovv-sdk-jvm`
- `innovv-sdk-iosarm64`
- `innovv-sdk-iossimulatorarm64`
- `innovv-sdk-iosx64`

The iOS targets produce a static `InnovvSDK.framework` with a bundle ID and a
minimum OS version of 18.0.

## 9. Extension points

| Need | Mechanism |
|---|---|
| Different camera IP/port | `InnovvConfig(baseUrl = …)` |
| Shorter/longer timeouts | `connectTimeoutMillis`, `requestTimeoutMillis`, `socketTimeoutMillis` |
| Disable retries | `maxRetries = 0` |
| Custom logs | `InnovvLogger` |
| Disable event reconnect | `eventReconnect = false` |
| Raw command | `setSetting(commandId, value/text)` + `K5Commands` |
| Future auth/proxy | Add a new `HttpTransport` or platform engine; public API stays response-model based |

## 10. Design decisions and trade-offs

| Decision | Reason |
|---|---|
| Ktor client | Mature KMP HTTP with Android/iOS/JVM engines |
| Ktor raw TCP | Avoid a separate socket library for the event stream |
| Regex tag extractor | Handles the malformed `3014` response; no XML dependency |
| No response-body logging | The stock app's `BODY` logging is a measured performance problem |
| Streaming download callback | Keeps the SDK platform-neutral and memory-safe |
| Explicit writes not retried | Avoids duplicate record/delete/format actions |
| `explicitApi()` | Stable, reviewable public ABI |
| Static iOS framework | Simple embedding in an iOS app; no dynamic linking lifecycle |
