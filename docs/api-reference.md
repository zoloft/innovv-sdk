# API reference

All public types are in `com.zoloft.innovv.sdk` or
`com.zoloft.innovv.sdk.model`.

- [Entry point](#entry-point)
- [Configuration](#configuration)
- [Client operations](#client-operations)
- [Media](#media)
- [Settings](#settings)
- [Live view](#live-view)
- [Wi-Fi](#wi-fi)
- [GPS and status](#gps-and-status)
- [Events](#events)
- [Models](#models)
- [Errors](#errors)
- [Command constants](#command-constants)
- [Threading and lifecycle](#threading-and-lifecycle)

## Entry point

### `InnovvSdk`

```kotlin
object InnovvSdk {
    const val VERSION: String
    fun createClient(config: InnovvConfig = InnovvConfig()): InnovvClient
}
```

`createClient` selects the platform HTTP engine and returns a client that the
caller must close.

```kotlin
val client = InnovvSdk.createClient()
try {
    client.heartbeat()
} finally {
    client.close()
}
```

## Configuration

### `InnovvConfig`

```kotlin
data class InnovvConfig(
    val baseUrl: String = "http://192.168.1.254/",
    val connectTimeoutMillis: Long = 3_000,
    val requestTimeoutMillis: Long = 8_000,
    val socketTimeoutMillis: Long = 15_000,
    val eventPort: Int = 3333,
    val eventReconnect: Boolean = true,
    val maxRetries: Int = 1,
    val userAgent: String = "innovv-kmp-sdk/0.1.0",
    val logger: InnovvLogger = InnovvLogger.None,
)
```

| Property | Meaning |
|---|---|
| `baseUrl` | Camera base URL. Must start with `http://` or `https://`. |
| `connectTimeoutMillis` | TCP connect timeout. |
| `requestTimeoutMillis` | Whole-request timeout. |
| `socketTimeoutMillis` | Socket read timeout. |
| `eventPort` | Event TCP port; K5 uses `3333`. |
| `eventReconnect` | Reconnect `events()` with exponential backoff. |
| `maxRetries` | Retries for idempotent reads. Writes are never retried. |
| `userAgent` | HTTP `User-Agent`. |
| `logger` | Optional `InnovvLogger`. Response bodies are never logged. |

### `InnovvLogger`

```kotlin
enum class InnovvLogLevel { DEBUG, INFO, WARN, ERROR }

interface InnovvLogger {
    fun log(level: InnovvLogLevel, message: String, throwable: Throwable? = null)
    companion object { val None: InnovvLogger }
}
```

## Client operations

### `InnovvClient`

```kotlin
class InnovvClient internal constructor(/* ... */) : AutoCloseable {
    val baseUrl: String
    override fun close()
}
```

The constructor is internal; use `InnovvSdk.createClient`.

### Identity and status

| Method | Returns | Camera command |
|---|---|---|
| `suspend heartbeat(): CommandResult` | Heartbeat result | `3016` |
| `suspend getVersion(): String` | Firmware version | `3012` |
| `suspend getDeviceInfo(): DeviceInfo` | Version + hardware capability | `3012`, `3022` |
| `suspend getHardwareCapability(): Int` | Raw capability code | `3022` |
| `suspend getModeStatus(): Int` | Raw mode/status value | `3037` |
| `suspend getFreeSpaceBytes(): Long` | Free SD-card bytes | `3017` |
| `suspend getSdCardState(): SdCardState` | Card state | `3024` |
| `suspend getBattery(): BatteryStatus` | Battery state | `3019` |

```kotlin
val info = client.getDeviceInfo()
println("${info.firmwareVersion} / hw ${info.hardwareCapability}")

when (client.getSdCardState()) {
    SdCardState.REMOVED -> println("No card")
    SdCardState.INSERTED -> println("Free: ${client.getFreeSpaceBytes()} bytes")
    SdCardState.LOCKED_OR_ERROR -> println("Card error")
}
```

### Recording and photos

| Method | Returns | Camera command |
|---|---|---|
| `suspend setMode(mode: CameraMode): CommandResult` | Mode change | `3001` |
| `suspend startRecording(): CommandResult` | Start recording | `2001&par=1` |
| `suspend stopRecording(): CommandResult` | Stop recording | `2001&par=0` |
| `suspend capturePhoto(): CaptureResult` | File + free slots | `1001` |
| `suspend formatSdCard(): CommandResult` | Format result | `3010&par=1` |
| `suspend resetSettings(): CommandResult` | Reset result | `3011&par=1` |
| `suspend powerOff(): CommandResult` | Power-off result | `3007&par=4` |

`formatSdCard`, `resetSettings`, and `powerOff` are destructive/intentional
actions. The SDK does not retry them.

### Settings

| Method | Returns | Notes |
|---|---|---|
| `suspend getCurrentValues(): Map<Int, Int>` | command ID → current value | Full `3014` dump |
| `suspend getSettingMenu(): List<CameraSettingDefinition>` | Menu definitions | `3031&str=all` |
| `suspend getSettings(includeMenu: Boolean = true): CameraSettings` | Values + optional menu | Convenience |
| `suspend setSetting(commandId: Int, value: Int): CommandResult` | Numeric write | `&par=` |
| `suspend setSetting(commandId: Int, text: String): CommandResult` | Text write | `&str=` |
| `suspend saveSettings(): CommandResult` | Persist | `3021` |

Typed helpers:

| Method | Command |
|---|---|
| `setVideoResolution(value: Int)` | `2002` |
| `setPhotoResolution(value: Int)` | `1002` |
| `setLoopRecording(value: Int)` | `2003` |
| `setBitrate(value: Int)` | `8200` |
| `setWifiBand(value: Int)` | `8201` |
| `setParkingMode(enabled: Boolean)` | `8309` |
| `setGpsEnabled(enabled: Boolean)` | `8003` |
| `setMicVolume(value: Int)` | `8306` |
| `setMirror(front: Int, rear: Int)` | `8006`, `8007` |
| `setMovieFormat(value: Int)` | `8010` |
| `setEncodeMode(value: Int)` | `8304` |
| `setTimeLapse(value: Int)` | `8206` |
| `setLiveViewSource(value: Int)` | `3028` |
| `setWifiOffTimer(value: Int)` | `8011` |
| `setWatermark(text: String)` | `8219` |

```kotlin
val menu = client.getSettingMenu()
val loop = menu.first { it.commandId == K5Commands.LOOP_RECORDING }
println(loop.options) // [0=OFF, 1=1 Minute, …]

client.setSetting(K5Commands.LOOP_RECORDING, 2)
client.saveSettings()
```

`getCurrentValues()` is a full settings dump. Cache it and refresh only when
needed. `getSettingMenu()` is also cacheable for the session.

### Media

| Method | Returns | Notes |
|---|---|---|
| `suspend getFiles(): List<CameraFile>` | File list | `3015` |
| `suspend getMovieInfo(file: CameraFile): MovieInfo` | Width/height/length | `4005` on the file URL |
| `suspend getThumbnail(file: CameraFile): ByteArray` | JPEG thumbnail | `4001` |
| `suspend getPreview(file: CameraFile): ByteArray` | JPEG preview | `4002` |
| `suspend download(file: CameraFile, onChunk: suspend (ByteArray) -> Unit)` | Streaming download | Direct file URL |
| `suspend deleteFile(file: CameraFile): CommandResult` | Delete one | `4003&str=A:\…` |
| `suspend deleteAllFiles(): CommandResult` | Delete all | `4004` |

```kotlin
val clip = client.getFiles().first { it.type == FileType.VIDEO }
val info = client.getMovieInfo(clip)
println("${info.width}x${info.height}, ${info.lengthSeconds}s")

val thumb = client.getThumbnail(clip)
client.download(clip) { bytes ->
    // write bytes to disk
}
client.deleteFile(clip)
```

### Live view

| Method | Returns | Notes |
|---|---|---|
| `suspend getLiveView(): LiveViewLinks` | Movie/photo URLs | `2019` |

```kotlin
val links = client.getLiveView()
links.movieUrl?.let { rtspUrl -> /* RTSP/TCP player */ }
links.photoUrl?.let { mjpegUrl -> /* MJPEG reader */ }
```

### Wi-Fi

| Method | Returns | Notes |
|---|---|---|
| `suspend getWifiInfo(): WifiInfo` | SSID + passphrase | `3029` |
| `suspend setWifi(ssid: String, passphrase: String, save: Boolean = true): CommandResult` | Change AP credentials | `3003`, `3004`, optionally `3021` |
| `suspend reconnectWifi(): CommandResult` | Reconnect | `3018` |
| `suspend scanWifi(): List<WifiNetwork>` | AP scan | `3038` |

```kotlin
val current = client.getWifiInfo()
println("${current.ssid} / ${current.passphrase}")

client.setWifi(ssid = "MyK5", passphrase = "a-strong-passphrase")
// The AP may restart; reconnect with the new credentials.
```

SSID is limited to 32 characters, passphrase to 8–26 characters by the camera.

### GPS and status

| Method | Returns | Notes |
|---|---|---|
| `suspend getGpsInfo(): GpsInfo` | Fix data | `8058` |
| `suspend isGpsReady(): Boolean` | GPS readiness | `8004` |
| `suspend getResolutions(): List<CameraResolution>` | Supported modes | `3030` |

```kotlin
if (client.isGpsReady()) {
    val fix = client.getGpsInfo()
    println("${fix.latitude}, ${fix.longitude} at ${fix.speed}")
}
```

### Events

| Method | Returns | Notes |
|---|---|---|
| `fun events(): Flow<CameraEvent>` | Cold event flow | TCP `3333` |

```kotlin
client.events().collect { event ->
    when (event) {
        is CameraEvent.RecordingTime -> println("Recording ${event.seconds}s")
        CameraEvent.RecordStarted -> println("REC started")
        CameraEvent.RecordStopped -> println("REC stopped")
        CameraEvent.CardInserted -> println("Card inserted")
        CameraEvent.CardRemoved -> println("Card removed")
        is CameraEvent.BatteryChanged -> println("Battery ${event.level}")
        else -> println(event)
    }
}
```

## Models

### `CameraFile`

```kotlin
data class CameraFile(
    val name: String,
    val path: String,          // A:\NOVATEK\MOVIE\clip.mp4
    val relativePath: String,  // NOVATEK/MOVIE/clip.mp4
    val url: String,           // http://192.168.1.254/NOVATEK/MOVIE/clip.mp4
    val sizeBytes: Long,
    val timecodeEpochSeconds: Long,
    val timeText: String,
    val attributes: Int,
    val type: FileType,
) {
    val thumbnailUrl: String // url + ?custom=1&cmd=4001
    val previewUrl: String   // url + ?custom=1&cmd=4002
    val infoUrl: String      // url + ?custom=1&cmd=4005
}
```

`attributes` is the raw `<ATTR>` value; its bit meanings are not documented by
the firmware and the SDK does not interpret it.

### `CameraMode`

`PHOTO(0)`, `VIDEO(1)`, `PLAYBACK(2)`, `VIDEO_TIMELAPSE(3)`,
`PHOTO_TIMELAPSE(4)`, `VIDEO_SLOW_MOTION(5)`, `PHOTO_BLUR(6)`.

### `SdCardState`

`REMOVED(0)`, `INSERTED(1)`, `LOCKED_OR_ERROR(2)`.

### `FileType`

`PHOTO`, `VIDEO`, `UNKNOWN`.

### `CameraSettings`

```kotlin
data class CameraSettings(
    val values: Map<Int, Int>,
    val definitions: List<CameraSettingDefinition>,
) {
    fun valueOf(commandId: Int): Int?
    fun definitionOf(commandId: Int): CameraSettingDefinition?
}
```

### `CameraSettingDefinition` / `CameraSettingOption`

```kotlin
data class CameraSettingDefinition(
    val commandId: Int,
    val name: String,
    val options: List<CameraSettingOption>,
) {
    fun option(index: Int): CameraSettingOption?
}

data class CameraSettingOption(val index: Int, val label: String)
```

### `CaptureResult`

```kotlin
data class CaptureResult(val file: CameraFile?, val freePictures: Int?)
```

### `CameraResolution`

```kotlin
data class CameraResolution(
    val name: String,
    val index: Int,
    val width: Int,
    val height: Int,
    val frameRate: Int,
    val type: Int,
)
```

### `MovieInfo`

```kotlin
data class MovieInfo(val width: Int, val height: Int, val lengthSeconds: Int)
```

### `LiveViewLinks`

```kotlin
data class LiveViewLinks(val movieUrl: String?, val photoUrl: String?)
```

### `WifiInfo` / `WifiNetwork`

```kotlin
data class WifiInfo(val ssid: String, val passphrase: String)

data class WifiNetwork(
    val ssid: String,
    val authType: Int,
    val rssiDbm: Int,
)
```

### `GpsInfo`

```kotlin
data class GpsInfo(
    val timestampEpochSeconds: Long,
    val longitude: Double,
    val latitude: Double,
    val altitudeMeters: Double,
    val speed: Double,
    val satellites: Int,
    val snr: Int,
) {
    val hasFix: Boolean
}
```

### `DeviceInfo`

```kotlin
data class DeviceInfo(
    val firmwareVersion: String,
    val hardwareCapability: Int,
)
```

### `BatteryStatus`

```kotlin
data class BatteryStatus(val level: Int) {
    val isCharging: Boolean
    val isLow: Boolean
    companion object {
        const val FULL = 0
        const val MEDIUM = 1
        const val LOW = 2
        const val EMPTY = 3
        const val EXHAUSTED = 4
        const val CHARGING = 5
    }
}
```

### `CommandResult`

```kotlin
data class CommandResult(
    val commandId: Int,
    val status: Int,
    val rawXml: String,
)
```

### `CameraEvent`

```kotlin
sealed interface CameraEvent {
    val commandId: Int
    val status: Int

    data class RecordingTime(val seconds: Int) : CameraEvent
    data object RecordStarted : CameraEvent
    data object RecordStopped : CameraEvent
    data object SensorCountChanged : CameraEvent
    data object CardInserted : CameraEvent
    data object CardRemoved : CameraEvent
    data class ProtectionChanged(val enabled: Boolean) : CameraEvent
    data class GpsChanged(val enabled: Boolean) : CameraEvent
    data object PhotoDownloaded : CameraEvent
    data class TimelapseChanged(val enabled: Boolean) : CameraEvent
    data class BatteryChanged(val level: Int) : CameraEvent
    data class Unknown(override val commandId: Int, override val status: Int) : CameraEvent
}
```

## Errors

```kotlin
sealed class InnovvException(message: String, cause: Throwable? = null) : Exception

class InnovvNetworkException(message: String, cause: Throwable? = null)
class InnovvHttpException(val statusCode: Int, val url: String, val body: String)
class InnovvCommandException(val commandId: Int, val status: Int, val body: String)
class InnovvParseException(message: String, cause: Throwable? = null)
```

`InnovvClient` methods throw these exceptions. `InnovvCommandException` is the
one to catch for camera-level failures such as a full SD card or an unsupported
mode.

## Command constants

`K5Commands` exposes the numeric IDs used by the SDK and the camera. Examples:

```kotlin
K5Commands.CAPTURE_PHOTO      // 1001
K5Commands.RECORD             // 2001
K5Commands.VIDEO_RESOLUTION   // 2002
K5Commands.LOOP_RECORDING     // 2003
K5Commands.CURRENT_VALUES     // 3014
K5Commands.FILE_LIST          // 3015
K5Commands.LIVE_VIEW_LINKS    // 2019
K5Commands.SETTING_MENU       // 3031
K5Commands.WIFI_INFO          // 3029
K5Commands.GPS_INFO           // 8058
K5Commands.PARKING_MODE       // 8309
K5Commands.WATERMARK          // 8219
```

The complete table is in `K5Commands.kt` and in
[`rea/data/api_commands.csv`](../rea/data/api_commands.csv).

## Threading and lifecycle

- All suspend functions perform I/O and can be called from any dispatcher.
- Use one `InnovvClient` per camera connection.
- Call `close()` when the connection is no longer needed.
- Cancelling a coroutine cancels the HTTP request or event collection.
- `events()` is cold; each collector opens a TCP connection.
- The SDK is not a singleton and does not hold Android `Context` or iOS
  application state.
