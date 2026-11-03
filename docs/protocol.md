# K5 camera protocol

This is the subset of the K5 protocol that the SDK implements. The complete
reverse-engineering notes and the 127-row command table are in
[`rea/`](../rea/):

- [`rea/03-api-reference.md`](../rea/03-api-reference.md)
- [`rea/data/api_commands.csv`](../rea/data/api_commands.csv)
- [`rea/01-firmware-analysis.md`](../rea/01-firmware-analysis.md)
- [`rea/02-android-app-analysis.md`](../rea/02-android-app-analysis.md)

## 1. Transport

| Property | Value |
|---|---|
| Control base | `http://192.168.1.254/` |
| Control port | 80/tcp |
| Method | GET |
| Authentication | none |
| Event stream | TCP `192.168.1.254:3333` |
| Live movie | RTSP URL from `cmd=2019` |
| Live photo | MJPEG URL from `cmd=2019` |
| Media download | direct HTTP URL from `<FPATH>` |

The camera is an access point. The application must join its Wi-Fi network
before using the SDK. The SDK does not join the network.

## 2. Request grammar

```
/?custom=1&cmd=<id>[&par=<int>][&str=<string>]
```

- `custom=1` **must be the first query parameter**. The firmware
  prefix-compares the query against `custom=1&cmd=`. A request without it gets
  `A:\test.htm`, not an error.
- `cmd` is a decimal command ID.
- Numeric/enum values use `&par=<int>`.
- Text values use `&str=<url-encoded-string>`.
- Media commands `4001`, `4002`, and `4005` use the file path as the URL path:
  `/<FPATH>?custom=1&cmd=4001`.
- `4003` uses `&str=A:\path`.

The SDK builds these URLs in `InnovvClient.commandUrl` and
`CameraFile.thumbnailUrl`/`previewUrl`/`infoUrl`.

## 3. Response shapes

Most commands return:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<Function>
<Cmd>3016</Cmd>
<Status>0</Status>
</Function>
```

Optional elements:

```xml
<Value>123</Value>
<Value1>456</Value1>
<String>text</String>
<FREEPICNUM>42</FREEPICNUM>
<File><NAME>…</NAME><FPATH>A:\…</FPATH></File>
```

Negative `<Status>` values are failures. The SDK maps them to
`InnovvCommandException`.

## 4. Important quirks

### 4.1 `3014` `<Status>` is a value

`cmd=3014` returns one `<Function>` per menu-backed setting:

```xml
<Function><Cmd>2003</Cmd><Status>2</Status></Function>
```

`Status` is the **current value** of command `2003` (2 = 3 minutes), not an
error. This is why `InnovvClient.getCurrentValues()` parses each function
separately and never calls the usual status check.

The response is also **not single-root XML**: it is a sequence of `<Function>`
elements after the XML declaration. The SDK uses a tolerant tag extractor
rather than a strict XML parser.

### 4.2 `3015` wraps each file separately

The file list is a `<LIST>` containing an `<ALLFile>` wrapper per file:

```xml
<LIST>
<ALLFile><File>
<NAME>clip.MP4</NAME>
<FPATH>A:\NOVATEK\MOVIE\clip.MP4</FPATH>
<SIZE>123456789</SIZE>
<TIMECODE>1609459200</TIMECODE>
<TIME>2021/01/01 12:00:00</TIME>
<ATTR>0</ATTR></File>
</ALLFile>
...
</LIST>
```

The SDK selects all `<File>` elements. `CameraFile.relativePath` removes `A:`
and converts `\` to `/`; `CameraFile.url` prepends the base URL.

The firmware builds the response into a bounded buffer. On a card with many
files, the XML may be truncated. The SDK parses the complete `<File>` elements
it receives, but callers should be prepared to retry or use FTP/TFTP for bulk
sync.

### 4.3 `4003` vs `4005`

- `4003` (delete) takes the camera path in `&str=`: `A:\NOVATEK\...`.
- `4005` (movie info) takes the file **URL path**:
  `GET /NOVATEK/MOVIE/clip.MP4?custom=1&cmd=4005`.

The SDK exposes this correctly via `deleteFile` and `getMovieInfo`.

### 4.4 `4001` / `4002` are URL modifiers

They are appended to a file URL:

```
http://192.168.1.254/NOVATEK/MOVIE/clip.MP4?custom=1&cmd=4001
```

They are not standalone `/?custom=1&cmd=4001` commands. The SDK builds the URL
through `CameraFile.thumbnailUrl` and `CameraFile.previewUrl`.

### 4.5 `8322` / `8323` are JPEGs

`8322` and `8323` return front/rear raw JPEG snapshots. They are **not** the
Wi-Fi SSID/password; Wi-Fi credentials come from `3029`, `3003`, and `3004`.

### 4.6 Live-view URLs

`cmd=2019` returns:

```xml
<LIST>
<MovieLiveViewLink>rtsp://192.168.1.254/xxx.mov</MovieLiveViewLink>
<PhotoLiveViewLink>http://192.168.1.254:8192</PhotoLiveViewLink>
</LIST>
```

The values depend on the current mode and whether a rear camera is present.
Do not hard-code the RTSP path; use `getLiveView()`.

### 4.7 `3015` and playback mode

Some Novatek firmware only returns a file list in playback mode. If `3015`
returns an empty list, switch to playback (`3001&par=2`), wait about a second,
and retry. Mode changes may stop recording. The SDK does not do this
automatically.

## 5. Command groups used by the SDK

| Area | Commands |
|---|---|
| Identity/status | `3012`, `3016`, `3022`, `3024`, `3017`, `3019`, `3037` |
| Record/photo | `1001`, `2001`, `3001`, `3010`, `3011`, `3007` |
| Settings | `3014`, `3031`, `3021`, `2002`, `1002`, `2003`, `8200`, `8201`, `8006`, `8007`, `8010`, `8304`, `8206`, `3028`, `8306`, `8003`, `8005`, `8210`, `8320`, `8219`, `8309`, `8321` |
| Media | `3015`, `4001`, `4002`, `4003`, `4004`, `4005` |
| Live view | `2019`, `2015`, `2010`, `2014`, `2026` |
| Wi-Fi | `3003`, `3004`, `3029`, `3032`, `3033`, `3018`, `8201`, `8011`, `3038` |
| GPS | `8003`, `8004`, `8058`, `8307`, `8308`, `8210`, `8320` |
| Events | `2020`, `3020`, `9019` (TCP `3333`) |

The full table, including firmware-only commands, is in
[`rea/data/api_commands.csv`](../rea/data/api_commands.csv).

## 6. Event stream

The camera pushes complete XML messages on TCP `3333`:

```xml
<Function><Cmd>3020</Cmd><Status>1</Status></Function>
<Function><Cmd>2020</Cmd><Status>42</Status></Function>
<Function><Cmd>9019</Cmd><Status>5</Status></Function>
```

The SDK parses the stream into `CameraEvent`:

| Event | Meaning |
|---|---|
| `RecordingTime(seconds)` | Recording duration update (`2020`) |
| `RecordStarted` / `RecordStopped` | Recording state (`3020`, 1/2) |
| `SensorCountChanged` | Sensor count changed (`3020`, 8) |
| `CardInserted` / `CardRemoved` | SD card events (`3020`, 9/10) |
| `ProtectionChanged(enabled)` | Protection state (`3020`, 11/12) |
| `GpsChanged(enabled)` | GPS state (`3020`, 13/14) |
| `PhotoDownloaded` | Photo saved/downloaded (`3020`, 16) |
| `TimelapseChanged(enabled)` | Timelapse state (`3020`, 21/22) |
| `BatteryChanged(level)` | Battery level (`9019`) |
| `Unknown(commandId, status)` | Unrecognised message |

Some status mappings are inferred from the stock app's event switch and
notification constants; unrecognised codes are preserved in `Unknown`.

## 7. Error codes

| Status | Meaning |
|---:|---|
| `0` | Success |
| `-1` | Generic failure / file not found |
| `-2` | No card / card error |
| `-4` | File operation error (older docs) |
| `-5` | Write/argument error (observed in K5 handlers) |
| `-7` | Movie storage full |
| `-8` | Movie write error |
| `-9` | Movie too slow |
| `-11` | Storage full |
| `-12` | Folder full |
| `-13` | Unknown error |
| `-256` | Unsupported command |

Always treat any negative status as failure. The exact code can vary by
handler.

## 8. Security

The K5 API has **no authentication**:

- Anyone on the camera AP can control recording, read the Wi-Fi password
  (`3029`), download/delete media, or format the card (`3010`).
- The firmware also enables FTP and TFTP on the SD-card filesystem. The
  firmware image's `/etc/passwd` has an empty root password, and TFTP is
  unauthenticated. Treat the AP as the security boundary.
- The control API is plain HTTP. Android apps must allow cleartext traffic for
  the camera host; iOS apps need an ATS exception.

Recommendations:

1. Change the camera AP SSID/password (`setWifi`).
2. Do not expose the camera AP to an untrusted network.
3. Do not log `getWifiInfo()` results.
4. Gate `formatSdCard`, `resetSettings`, `deleteAllFiles`, and firmware-update
   commands behind explicit user confirmation.

## 9. Extending the protocol layer

If a new command is needed:

1. Add the numeric ID to `K5Commands` with KDoc.
2. Add a typed method to `InnovvClient`, or use the generic
   `setSetting(commandId, value/text)`.
3. Add a parser under `internal/` if the response is not a standard
   `<Status>`/`<Value>`/`<String>`.
4. Add a MockEngine test with a captured response.
5. Document the command in `docs/protocol.md` and, if it is part of the reverse
   engineering evidence, in `rea/`.
