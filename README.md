# INNOVV K5 Kotlin Multiplatform SDK

A Kotlin Multiplatform client for the **INNOVV K5** dashcam local API. It is
designed as the foundation for a replacement Android/iOS application: typed
camera operations, streaming media downloads, live-view URL discovery, event
streaming, and a protocol layer built around the camera's actual HTTP/XML
behavior.

The SDK is licensed under the [MIT License](LICENSE).

- Repository: <https://github.com/zoloft/innovv-sdk>
- Maven coordinates: `com.zoloft.innovv:innovv-sdk:0.1.0`
- Kotlin: **2.4.21**
- Gradle: **9.8.2**
- Android Gradle Plugin: **9.5.0** (new `com.android.kotlin.multiplatform.library` plugin)
- Ktor: **3.6.1**
- Coroutines: **1.11.0**

## Features

- K5 control API over `http://192.168.1.254/` (`custom=1&cmd=…`).
- Typed camera operations: heartbeat, version, mode, record, photo, format,
  reset, power-off.
- Settings dump (`3014`), menu discovery (`3031`), typed setting writes, and
  persistence (`3021`).
- Media file listing (`3015`) with direct download URLs and JPEG
  thumbnail/preview helpers (`4001`/`4002`).
- Streaming file downloads; the SDK never buffers a whole video in memory.
- Movie metadata (`4005`) and supported resolutions (`3030`).
- Live-view link discovery (`2019`) for RTSP and MJPEG.
- Wi-Fi info (`3029`), credential changes (`3003`/`3004`/`3021`), AP scan
  (`3038`), and reconnect (`3018`).
- GPS fix (`8058`), GPS readiness (`8004`), and GPS settings.
- Camera event stream over TCP `3333` exposed as a Kotlin `Flow`.
- Configurable timeouts, retries, logging, and event reconnection.
- Android, iOS, and JVM targets from one common API.

The reverse-engineering evidence and the complete camera command reference are
in the [`rea/`](rea/) directory. The SDK-facing protocol notes are in
[`docs/protocol.md`](docs/protocol.md).

## Platform support

| Platform | Support |
|---|---|
| Android | Compile SDK 37 (Android 17), compatible with Android 16 (API 36) and Android 17 (API 37); `minSdk 26`; JVM target 17 |
| iOS | Deployment target 18.0; latest two major iOS versions (iOS 26 and iOS 27) supported; `iosArm64`, `iosSimulatorArm64`, `iosX64` |
| JVM | JDK 17+; useful for desktop tools and common tests |

See [docs/platform-support.md](docs/platform-support.md) for permissions,
cleartext-HTTP configuration, iOS App Transport Security, and framework
integration.

## Installation

### Gradle Kotlin Multiplatform

```kotlin
commonMain.dependencies {
    implementation("com.zoloft.innovv:innovv-sdk:0.1.0")
}
```

The SDK selects the correct HTTP engine per platform:

- Android: OkHttp
- iOS: Darwin/NSURLSession
- JVM: CIO

### Local development

```bash
./gradlew :innovv-sdk:publishToMavenLocal
```

Then depend on `com.zoloft.innovv:innovv-sdk:0.1.0` from Maven Local.

## Quick start

```kotlin
import com.zoloft.innovv.sdk.InnovvSdk
import com.zoloft.innovv.sdk.model.CameraEvent

suspend fun main() {
    val client = InnovvSdk.createClient()
    try {
        // Connectivity and identity.
        client.heartbeat()
        val info = client.getDeviceInfo()
        println("Firmware: ${info.firmwareVersion}")

        // Current values are a full dump; cache them in a real app.
        val settings = client.getSettings(includeMenu = true)
        println("Video resolution value: ${settings.valueOf(2002)}")

        // Media.
        val files = client.getFiles()
        val clip = files.firstOrNull { it.name.endsWith(".MP4") }
        if (clip != null) {
            println("Download: ${clip.url}")
            client.download(clip) { chunk ->
                // Write chunk to the platform file system.
            }
        }

        // Live view.
        val live = client.getLiveView()
        println("Movie: ${live.movieUrl}, photo: ${live.photoUrl}")

        // Events.
        client.events().collect { event ->
            when (event) {
                is CameraEvent.RecordingTime -> println("REC ${event.seconds}s")
                is CameraEvent.CardRemoved -> println("Card removed")
                else -> println(event)
            }
        }
    } finally {
        client.close()
    }
}
```

The application must first join the camera's Wi-Fi access point. The SDK does
not change the device's Wi-Fi network.

## Recording and photos

```kotlin
client.setMode(CameraMode.VIDEO)
client.startRecording()

val photo = client.capturePhoto()
println("Saved ${photo.file?.name}, ${photo.freePictures} slots left")

client.stopRecording()
```

## Settings

```kotlin
// Menu definitions from the camera (cache these).
val menu = client.getSettingMenu()
val values = client.getCurrentValues() // full 3014 dump

// Write and persist one setting.
client.setSetting(K5Commands.LOOP_RECORDING, 2) // 3 minutes
client.saveSettings()

// Typed helpers for common K5 settings.
client.setVideoResolution(21)
client.setParkingMode(true)
client.setGpsEnabled(true)
client.setMicVolume(3)
```

## Media

```kotlin
val files = client.getFiles()

val thumbnail: ByteArray = client.getThumbnail(files.first())
val info: MovieInfo = client.getMovieInfo(files.first())

client.deleteFile(files.first())
```

`CameraFile.url` is a direct HTTP URL. `deleteFile` converts the file back to
the camera's `A:\…` path and uses `cmd=4003` as required by the firmware.

## Live view

```kotlin
val links = client.getLiveView()
// Play links.movieUrl with RTSP/TCP, or links.photoUrl as an MJPEG stream.
```

The camera returns the authoritative URLs. Do not hard-code the RTSP path.

## Events

`InnovvClient.events()` exposes the TCP `3333` stream as a cold `Flow`. It
reconnects with exponential backoff by default. Cancelling collection closes
the socket.

```kotlin
client.events().collect { event ->
    println(event)
}
```

## Error handling

All network and protocol failures are reported as `InnovvException` subtypes:

- `InnovvNetworkException` — transport failure or timeout.
- `InnovvHttpException` — non-2xx HTTP response.
- `InnovvCommandException` — camera returned a negative command `<Status>`.
- `InnovvParseException` — response shape/value could not be parsed.

Example:

```kotlin
try {
    client.startRecording()
} catch (failure: InnovvCommandException) {
    println("Camera rejected cmd ${failure.commandId}: ${failure.status}")
}
```

Read requests may be retried automatically (`InnovvConfig.maxRetries`); write
commands are never retried automatically.

## Documentation

| Document | Contents |
|---|---|
| [docs/architecture.md](docs/architecture.md) | Layering, data flow, concurrency, error model, extension points |
| [docs/api-reference.md](docs/api-reference.md) | Complete public API reference and examples |
| [docs/protocol.md](docs/protocol.md) | K5 protocol mapping, command quirks, security |
| [docs/platform-support.md](docs/platform-support.md) | Android/iOS versions, permissions, cleartext HTTP, framework setup |
| [docs/testing.md](docs/testing.md) | Unit tests, platform builds, hardware test checklist |
| [rea/](rea/) | Reverse-engineering notes and complete camera command table |

## Build

```bash
# Common/JVM tests
./gradlew :innovv-sdk:jvmTest

# Android compilation
./gradlew :innovv-sdk:compileAndroidMain

# iOS klib compilation (Linux can compile; linking needs macOS)
./gradlew :innovv-sdk:compileKotlinIosSimulatorArm64

# Publish all KMP artifacts to Maven Local
./gradlew :innovv-sdk:publishToMavenLocal
```

## Security

The K5 control API has **no authentication** and runs over plain HTTP. The
camera access point is the security boundary. Change the default AP credentials
and do not expose the camera API to untrusted networks. See
[docs/protocol.md#security](docs/protocol.md#security).

## License

MIT. See [LICENSE](LICENSE).
