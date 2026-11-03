# Android app analysis

Artifact: `binaries/INNOVV_1.0.38.20260618_APKPure.xapk`
XAPK SHA-256: `de3689f7e74ec15743386b57c58b7092c4f0bb77f4f7c7cf9c904867a8a9560d`

The XAPK is a split-APK bundle. The base APK is
`cn.rxt.case.innovv.apk`:

* package `cn.rxt.case.innovv`
* version name `1.0.38.20260618`
* version code `38`
* minSdk 21, targetSdk 35
* base APK SHA-256 `efa532cefd6c64285fd712c24890f632e1d8f29b2f4d6ddeb77d0dea49ae4307`
* 9,096 classes, 620 resources (REA `inspect_android_package`)
* permissions: `INTERNET`, `ACCESS_WIFI_STATE`, `ACCESS_NETWORK_STATE`,
  `CHANGE_NETWORK_STATE`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`,
  storage permissions, `MEDIA_CONTENT_CONTROL`

The app is obfuscated with R8. The meaningful packages are:

| Package | Role |
|---|---|
| `cn.rxt.ui.*` | Activities, ViewModels, camera preview, album, preferences, FOTA |
| `cn.rxt.ui.hyhHttp` | Connectivity probe (Ly and Qz endpoints) |
| `m.mifan.acase.core` | Protocol abstraction, HTTP client, event socket, media model |
| `m.mifan.acase.ly` | **Ly / Novatek protocol implementation** |
| `m.mifan.acase.qz` | Qz protocol implementation (different camera family) |

The K5 uses the **Ly** protocol. `m.mifan.acase.core.c.b(ip)` maps
`192.168.1.254` to `h.Ly`; `192.168.10.1` maps to `h.Qz`. The K5 AP gateway is
`192.168.1.254`, so the Ly path is the relevant one. The Qz path
(`http://192.168.10.1:8082/api/...`) is not used by the K5 and is out of scope.

## 1. Protocol selection and connectivity

`cn.rxt.ui.hyhHttp.a` performs two probes:

* `a()` — GET `http://192.168.1.254/?custom=1&cmd=3016` (Ly heartbeat).
* `b()` — GET `http://192.168.10.1:8082/api/getdeviceinfo/?custom=1&cmd=2001` (Qz).

Both use `HttpURLConnection` with 1-second connect/read timeouts. The Ly probe is
the K5 path.

`m.mifan.acase.ly.k` is the main Ly protocol class (`LyProtocol.kt`). Its
constructor takes an IP and builds:

```
base = "http://" + ip + "/"
```

All control requests are built as:

```
base + "custom=1&cmd=" + cmd + suffix
suffix = "&par=" + value   or   "&str=" + value
```

## 2. Key classes

| Class | Role |
|---|---|
| `m.mifan.acase.ly.k` | Ly protocol: commands, parsing, live URLs, settings, media |
| `m.mifan.acase.ly.c` | XML response converter; parses `<Status>`, treats non-negative as success |
| `m.mifan.acase.ly.h` | Media file list parser (`<File>`, `fpath`, `size`, `time`) |
| `m.mifan.acase.ly.j` | Setting-menu XML parser (`<Item>`, `<Cmd>`, `<Name>`, `<MenuList>`, `<Option>`) |
| `m.mifan.acase.ly.l` | Constants: command IDs, notification IDs, error codes |
| `m.mifan.acase.ly.innovv.a` | INNOVV Ly subclass: 8315 storage, 8322 JPEG, 3041/8219 string handling |
| `m.mifan.acase.ly.innovv.b` | K5/INNOVV preference builder |
| `m.mifan.acase.core.r` | OkHttp HTTP client |
| `m.mifan.acase.core.d` | TCP event socket (port 3333) |
| `m.mifan.acase.ly.f` | `LyCase`: high-level camera operations |
| `cn.rxt.ui.camera.ControlPreviewViewModel` | Live preview, record/photo, shortcut menu |
| `cn.rxt.ui.camera.album.AlbumContentViewModel` | Media list, download, delete, video info |
| `cn.rxt.ui.camera.preference.PreferenceViewModel` | Settings screen |

## 3. K5 setting menu (bundled JSON)

The app ships a K5-specific preference definition at
`res/raw/ly_innovv_preferences_k5.json`. It was extracted to
[data/k5_preferences.json](data/k5_preferences.json) and summarised in
[data/k5_settings.csv](data/k5_settings.csv). This is the K5-specific definition
bundled in the APK; the preference builder loads raw JSON resources of this
schema (`m.mifan.acase.ly.a.m(int)`). The app also queries the camera's
`cmd=3031`/`cmd=3014` at runtime. `LyCase.H` selects the raw resource by the
version string from `cmd=3012`: when it contains `ik5`, it loads
`R.raw.ly_innovv_preferences_k5`. Treat the bundled JSON as the stock UI's exact
K5 list and the camera's live `3031`/`3014` responses as the source of truth for
the current values.

The schema of each item:

```json
{
  "name_trn": "pref_resolution",
  "cmd": "2002",
  "ui": "UIList",
  "items": [
    {"value": "2160P30+1080P30", "key": "20"},
    {"value": "1440P30+1080P30", "key": "22"},
    {"value": "1080P30+1080P30", "key": "21"}
  ]
}
```

* `cmd` is the HTTP command ID (or a pseudo-command such as `sdcard_size`,
  `sync_time`, `clean_cache`).
* `ui` selects the widget: `UIHeader`, `UIList`, `UIInput`, `UISwitch`,
  `UISeekBar`, `UISDFormat`, `UIAction`, `UIReset`, `UIText`, `UICleanCache`.
* `items[].key` is sent as `&par=<key>`.
* `digits` / `maxLength` / `minLength` / `tips_trn` control text validation.
* `enable: 1` means the item is disabled/hidden (the builder skips it).
* A missing `key` means the index in the array is used as the value
  (`n(jsonItem)` uses `String.valueOf(i3)` when no key).

The K5 menu has 41 entries (including headers and pseudo-actions). The actual
setting commands are:

> **K5-specific `2002` values.** The bundled K5 JSON uses `20`, `22`, and `21`
> for dual-camera combinations (`2160P30+1080P30`, `1440P30+1080P30`,
> `1080P30+1080P30`). The firmware's generic `Resolution` menu (menu id 26)
> lists single-camera modes (`2, 6, 7, 9, 10, 11, 13, 15, 16, 17, 18, 19, 20,
> 29`). These are different scales. For the K5, use the JSON values, or query
> `cmd=3030`/`cmd=3031` on the device and verify which indices the camera
> reports. Do not blindly apply the generic firmware menu list to `2002`.

| Command | Setting | Values |
|---:|---|---|
| 3003 | Wi-Fi SSID | string, max 32 |
| 3004 | Wi-Fi password | string, 8–26 |
| 8201 | Wi-Fi band | 1=2.4G, 2=5.8G |
| 8011 | Wi-Fi off timer | 0=On/never, 1=5 min, 2=10 min |
| 2002 | Video resolution | 20=2160P30+1080P30, 22=1440P30+1080P30, 21=1080P30+1080P30 |
| 8200 | Bitrate | 0=low, 1=normal, 2=high, 3=maximum |
| 8006 | Front mirror | 0=normal, 1=mirror, 2=flip mirror, 3=flip forward |
| 8007 | Rear mirror | same |
| 2003 | Loop recording | 0=off, 1=1 min, 2=3 min, 3=5 min, 4=10 min |
| 8010 | Movie format | 0=MP4, 1=TS |
| 8304 | Movie encode | 0=H.264, 1=H.265 |
| 8206 | Time-lapse | 0=off, 1=100 ms, 2=200 ms, 3=300 ms, 4=500 ms, 5=1 s |
| 3028 | Live-view source | 0=F, 1=R, 2=F&R, 3=R&F |
| 8306 | Mic volume | 0=off, 1=20 %, 2=40 %, 3=60 %, 4=80 %, 5=100 % |
| 2008 | Date/time stamp | on/off |
| 8005 | Speed unit | 0=km/h, 1=mph |
| 8210 | GPS watermark | 0=off, 1=all, 2=speed, 3=coordinates |
| 8003 | GPS | on/off |
| 8320 | GPS rate | 0=1 Hz … 4=5 Hz |
| 8219 | Video watermark text | string, max 11 |
| 8309 | Parking mode | on/off |
| 2011 | G-sensor sensitivity | 0–10 (UI shows high/mid/low/off) |
| 8321 | G-sensor info stamp | on/off |
| 8215 | Light frequency | 0=50 Hz, 1=60 Hz |
| 3010 | Format SD | par=1 |
| 3011 | Reset to defaults | par=1 |
| 3005/3006 | Set date/time | `&str=yyyy-MM-dd`, `&str=HH:mm:ss` |

Pseudo-commands handled locally:

* `sdcard_size` → `innovv.a.F0()` → `cmd=8315` `<Value>`
* `sdcard_free_size` → `cmd=3017` `<Value>` (or card state)
* `sdcard_check` → `cmd=3024` `<Value>`
* `sync_time` → `cmd=3005` + `cmd=3006` with the phone's time
* `clean_cache` → clears the app's local cache
* `local_album_path`, `fw_version`, `app_version` → local values

The K5 JSON is the best source for **what the stock app actually exposes**.
The firmware's own menu table is larger; see
[01-firmware-analysis.md](01-firmware-analysis.md).

## 4. Live view

`LyProtocol` reads the live-view URLs with `cmd=2019`:

```java
c cVar = q(2019);
String movie = cVar.a("MovieLiveViewLink");
String photo = cVar.a("PhotoLiveViewLink");
```

Defaults if the fields are empty:

* movie: `rtsp://192.168.1.254:554/xxx.mp4`
* photo: `http://192.168.1.254:8192`

The app selects the URL by work mode:

* video / video-lapse / slow-motion modes → movie URL (RTSP)
* photo / photo-timer / photo-blur modes → photo URL (HTTP MJPEG)

The player is IJKMediaPlayer (`m.mifan.acase.ly.player.IjkLivePlayerView`) with
low-delay options:

```
rtsp_transport=tcp
fflags=nobuffer
packet-buffering=0
infbuf=1
max_cached_duration=30
probesize=200
```

The app also contains VLC and ExoPlayer players. The presence of
`AndroidVideoCache` strings suggests video may be proxied through a local HTTP
cache in some paths; a replacement should play RTSP directly.

## 5. Media list and download

`m.mifan.acase.ly.h` parses `cmd=3015`:

* selects all `<File>` elements,
* reads `<FPATH>`, `<SIZE>`, `<TIME>`,
* converts `A:\NOVATEK\MOVIE\...` to `NOVATEK/MOVIE/...`,
* sets the media path to `host + path` where host is `http://192.168.1.254`,
* sets the thumbnail URL to `path + "?custom=1&cmd=4001"`,
* classifies `.mp4`, `.mov`, `.ts` as video (type 2) and everything else as
  photo (type 1).

The album uses these URLs directly for playback/download. In `MediaItem`,
`source` is the relative path (`NOVATEK/MOVIE/...`) and `path` is the full URL.
This matters for the API: `4003` sends the camera path (`A:\...`) in `&str=`,
while `4001`/`4002`/`4005` use the relative path as the URL path. See
[04-media-and-transfers.md](04-media-and-transfers.md).

## 6. Event socket

`m.mifan.acase.core.d` connects to TCP port **3333** on the camera:

```java
new Socket().connect(new InetSocketAddress(ip, 3333), 5000);
byte[] buffer = new byte[500];
while (working) {
    int n = inputStream.read(buffer);
    callback(new String(buffer, 0, n, UTF_8));
}
```

The callback `LyProtocol.d0(String)` extracts `<Cmd>` and `<Status>` with simple
string helpers. It handles:

| Event cmd | Meaning | Status mapping |
|---:|---|---|
| 2020 | Recording time | integer seconds |
| 3020 | Camera action | 1 record start, 2 record stop, 8 sensor count, 9 card insert, 10 card remove, 11/12 protect on/off, 13/14 GPS on/off, 16 photo download, 21/22 timelapse (some mappings inferred) |
| 9019 | Battery change | integer level |

The 500-byte buffer has no message framing. A replacement should use a buffered
reader and parse complete `<Function>…</Function>` messages, because a single
read can contain a partial message or multiple messages.

## 7. Why the stock app is slow

These are concrete, observed implementation choices (not guesses):

### 7.1 OkHttp logs every response body in production

`m.mifan.acase.core.r` builds both OkHttp clients with
`HttpLoggingInterceptor` at `Level.BODY`:

```java
okhttp3.logging.a aVar = new okhttp3.logging.a(logger);
aVar.d(okhttp3.logging.a.EnumC0413a.BODY);
bVar.b(aVar);
```

`Level.BODY` logs the full response body for every control request. For
`cmd=3014` and `cmd=3015` responses, this is significant CPU and I/O overhead.
It also fills logcat/file logs.

### 7.2 The primary client serialises requests

When constructed with `singleTask = true` (the LyProtocol constructor does
`new m.mifan.acase.core.r(true)`), the client installs a dispatcher with
`maxRequests = 1`:

```java
okhttp3.p pVar = new okhttp3.p();
pVar.q(1);
bVar.n(pVar);
```

Every control request therefore waits for the previous one. Combined with
repeated settings dumps, this produces visible latency.

### 7.3 Repeated full settings dumps

`LyCase.H(context)` builds the preferences screen by calling:

* `cmd=3031&str=all` — the full setting menu,
* `cmd=3014` — the full current-value dump,
* per-item values from the dump.

`LyCase.M()` (preview state) also calls `cmd=3014`, `cmd=3012`, and
`cmd=8004` each time it refreshes. The menu XML is cached per `LyCase` instance
(`mapMenuXmlCache`), but the `3014` value dump is re-fetched when the settings
screen is rebuilt and on preview-state refresh. A replacement should fetch
`3014` once, cache it, and update only changed values.

### 7.4 1-second heartbeat timeouts

`cn.rxt.ui.hyhHttp.a` uses `HttpURLConnection` with a 1-second connect and read
timeout. On a busy camera or weak Wi-Fi link this causes frequent false
negatives and retries, independent of OkHttp.

### 7.5 Unframed 500-byte event socket

The event socket reads at most 500 bytes and passes each read directly to the
parser. If the camera coalesces or splits messages, the parser can miss events
or parse partial XML. A replacement should buffer until a complete
`</Function>` is seen.

### 7.6 AndroidVideoCache / player proxying

DEX strings include `AndroidVideoCache` (danikula) and the app includes IJK,
VLC, and ExoPlayer. Proxying a local camera stream through a caching HTTP server
adds latency and disk I/O. For live view, play the RTSP URL directly with a
low-latency player.

### 7.7 Large media list

`cmd=3015` returns the entire SD-card file list. The stock app fetches it and
filters by extension client-side. On a full card this is a large XML response
and a large parse. A replacement should:

* fetch the list once and cache it,
* filter locally by path/extension,
* avoid re-fetching on every album screen,
* consider FTP/TFTP for bulk transfer.

## 8. FOTA note

`cn.rxt.ui.fota` is a **Bluetooth LE OTA** implementation (`BluetoothGatt`,
`GUID_SERVICE`, `OTA_CTRL_*`). It is not the K5 Wi-Fi firmware-update path. The
K5 RTOS does expose HTTP firmware-update commands (`3013`, `3026`, `5001`), but
the stock K5 UI only links to
`https://innovv.com/pages/app-firmware-manual`. Do not confuse the BLE FOTA
package with the camera's HTTP update API.

## 9. Unknowns

* Runtime behavior against the real K5 (camera was offline during analysis).
* Whether the app's `singleTask` client is used for all Ly requests (the
  constructor passes `true`; the second client is used for the 5-second checks).
* Exact paths that use AndroidVideoCache vs direct RTSP.
* The meaning of some `3020` event status codes.
* The app's behavior for `cmd=3015` truncation.
