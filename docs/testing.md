# Testing

The SDK has fast unit tests for the protocol layer and a hardware checklist for
the real camera. The camera was not available during the reverse-engineering
phase, so the hardware checklist is the final source of truth.

## 1. Common/JVM unit tests

```bash
./gradlew :innovv-sdk:jvmTest
```

The tests use Ktor `MockEngine`, so they do not need a camera or network.

Covered areas:

- XML tag/CDATA/entity extraction.
- `3015` file-list parsing and URL construction.
- `3031` menu parsing.
- `3038` Wi-Fi scan parsing.
- `8058` GPS parsing.
- `4005` movie-info parsing.
- `3030` resolution parsing.
- Event parsing (`2020`, `3020`, `9019`).
- Request URL shape: `custom=1` first, `&par=`, `&str=`, file-URL commands.
- Negative command status → `InnovvCommandException`.
- Non-JPEG thumbnail response → `InnovvParseException`.

## 2. Android compilation and host tests

```bash
./gradlew :innovv-sdk:compileAndroidMain
./gradlew :innovv-sdk:testAndroidHostTest
```

The Android target uses the new KMP Android library plugin. Host tests are
enabled with `withHostTest {}`.

## 3. iOS compilation

```bash
# Kotlin/Native klib compilation (works on Linux)
./gradlew :innovv-sdk:compileKotlinIosSimulatorArm64

# Framework link (requires macOS + Xcode)
./gradlew :innovv-sdk:linkDebugFrameworkIosSimulatorArm64
```

Compile all Apple targets:

```bash
./gradlew \
  :innovv-sdk:compileKotlinIosArm64 \
  :innovv-sdk:compileKotlinIosSimulatorArm64 \
  :innovv-sdk:compileKotlinIosX64
```

## 4. Publication check

```bash
./gradlew :innovv-sdk:publishToMavenLocal
```

This validates the KMP metadata, Android AAR, JVM jar, and all iOS klibs. The
resulting POMs include the MIT license, SCM, and developer metadata.

## 5. Hardware checklist

Connect the host to the camera AP first. The default camera address is
`192.168.1.254`.

### 5.1 Control API

```bash
curl -s 'http://192.168.1.254/?custom=1&cmd=3016'
curl -s 'http://192.168.1.254/?custom=1&cmd=3012'
curl -s 'http://192.168.1.254/?custom=1&cmd=3014'
curl -s 'http://192.168.1.254/?custom=1&cmd=3031&str=all'
curl -s 'http://192.168.1.254/?custom=1&cmd=3015'
curl -s 'http://192.168.1.254/?custom=1&cmd=2019'
```

Check:

- `3016` returns `<Status>0</Status>`.
- `3012` contains an `IK5…` version.
- `3014` returns `<Cmd>`/`<Status>` pairs where `Status` is a setting value.
- `3031&str=all` returns a menu (not an empty list).
- `3015` returns files, or empty list if the card is empty / wrong mode.
- `2019` returns both live-view links.

### 5.2 Recording and photo

```bash
curl -s 'http://192.168.1.254/?custom=1&cmd=3001&par=1'
curl -s 'http://192.168.1.254/?custom=1&cmd=2001&par=1'
sleep 3
curl -s 'http://192.168.1.254/?custom=1&cmd=2001&par=0'
curl -s 'http://192.168.1.254/?custom=1&cmd=1001'
```

Verify the file appears in `3015` and downloads.

### 5.3 Media

```bash
# Use an FPATH from cmd=3015.
curl -o clip.mp4 'http://192.168.1.254/NOVATEK/MOVIE/clip.mp4'
curl -o thumb.jpg 'http://192.168.1.254/NOVATEK/MOVIE/clip.mp4?custom=1&cmd=4001'
curl -s 'http://192.168.1.254/NOVATEK/MOVIE/clip.mp4?custom=1&cmd=4005'
```

Verify:

- The download size matches `<SIZE>`.
- The thumbnail is a JPEG.
- `4005` returns `Width:…, Height:…, Length:… sec`.
- `4003` deletes with `str=A:\…`.

### 5.4 Live view

```bash
curl -s 'http://192.168.1.254/?custom=1&cmd=2019'
ffplay -rtsp_transport tcp '<movie-url>'
```

For MJPEG:

```bash
ffplay '<photo-url>'
```

Verify startup time, latency, and reconnect behavior.

### 5.5 Events

```bash
nc 192.168.1.254 3333
```

Start/stop recording and insert/remove the SD card. Verify messages:

```xml
<Function><Cmd>3020</Cmd><Status>1</Status></Function>
<Function><Cmd>2020</Cmd><Status>42</Status></Function>
```

### 5.6 Wi-Fi

```bash
curl -s 'http://192.168.1.254/?custom=1&cmd=3029'
curl -s 'http://192.168.1.254/?custom=1&cmd=3038'
```

Verify SSID/passphrase read and AP scan. Changing credentials will restart the
AP; test on a spare device or be ready to reconnect.

### 5.7 Bulk transfer (optional)

```bash
curl -v 'ftp://root:@192.168.1.254/'
```

If the empty-password login is rejected, record the actual behavior and update
the protocol notes. FTP/TFTP are firmware features, not SDK features.

## 6. Performance measurements

Record:

- heartbeat latency,
- `3014` latency and size,
- `3015` latency and size on a full card,
- download MB/s over HTTP,
- download MB/s over FTP (if available),
- RTSP startup time and stall rate,
- event reconnect time.

This data drives the replacement app's request policy and media strategy.

## 7. CI example

The repository includes a GitHub Actions workflow with:

- Ubuntu: common/JVM tests, Android compile, publication check.
- macOS: iOS compilation and simulator framework link.

See `.github/workflows/ci.yml`.
