# K5 local API reference

This is the API exposed by the K5 camera over its Wi-Fi access point. It is the
Novatek CarDV HTTP API as implemented in the K5 RTOS (`ImageUnit_NetHTTP`) and
used by the stock Ly protocol (`m.mifan.acase.ly.k`).

All observations are from the firmware RTOS image and the APK. The camera was not
online during analysis; values marked **inferred** should be verified against a
real device before relying on them.

## 1. Transport and base URL

| Property | Value |
|---|---|
| Base URL | `http://192.168.1.254/` |
| Method | `GET` |
| Control port | 80/tcp |
| Authentication | none |
| Response type | `text/xml` for control, `image/jpeg` for thumbnails/snapshots |
| Character encoding | UTF-8 |

The camera AP DHCP range is `192.168.1.20`–`192.168.1.253`; the camera itself is
`192.168.1.254`.

## 2. Request grammar

```
GET /?custom=1&cmd=<cmd>[&par=<integer>[ <integer> ...]][&str=<string>]
```

Rules observed in the firmware dispatcher (`0x01B9826C`):

1. **`custom=1` must be the first query parameter.** The dispatcher looks for the
   literal `custom=1&cmd=`. If it is missing, the server serves `A:\test.htm`
   instead of executing a command.
2. `cmd` is a decimal integer. The dispatcher parses up to 16 space-separated
   integers after `cmd=` (format `%d %d …`).
3. `&par=` is for integer parameters. Older Novatek commands occasionally use a
   string in `par`; the K5 app uses integer `par` for lists/switches and
   `str` for text.
4. `&str=` is for strings (SSID, password, date, time, watermark, file paths).
5. URL-encode `&`, `#`, spaces, `%`, etc. The camera expects a normal HTTP query
   string.
6. The path is ignored by the dispatcher for most control commands. `/`,
   `/api/...`, and `/<anything>` all reach the same handler if the query matches.
   The stock Ly app uses `/`; the Qz app uses `/api/getdeviceinfo/` on a
   different model.
7. Media commands `4001`, `4002`, and `4005` are the exception: they use the
   file path as the URL path, e.g.
   `GET /NOVATEK/MOVIE/file.MP4?custom=1&cmd=4001`. `4003` instead takes the
   camera path in `&str=A:\...`.
8. The control API is GET. The upload commands `5001`/`5002` may use a POST or
   multipart body; their framing is not established and the stock K5 UI does
   not use them.

Examples:

```bash
# heartbeat
curl 'http://192.168.1.254/?custom=1&cmd=3016'

# start recording
curl 'http://192.168.1.254/?custom=1&cmd=2001&par=1'

# set Wi-Fi SSID
curl 'http://192.168.1.254/?custom=1&cmd=3003&str=MyK5'

# set date
curl 'http://192.168.1.254/?custom=1&cmd=3005&str=2026-11-03'
```

## 3. Response grammar

### 3.1 Standard function response

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<Function>
<Cmd>2001</Cmd>
<Status>0</Status>
</Function>
```

Optional elements observed in the firmware:

```xml
<Value>123</Value>
<Value1>456</Value1>
<String>text</String>
<File>
<NAME>2021_0101_120000_001.MP4</NAME>
<FPATH>A:\NOVATEK\MOVIE\2021_0101_120000_001.MP4</FPATH>
</File>
<FREEPICNUM>42</FREEPICNUM>
```

### 3.2 File list response (`cmd=3015`)

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<LIST>
<ALLFile><File>
<NAME>2021_0101_120000_001.MP4</NAME>
<FPATH>A:\NOVATEK\MOVIE\2021_0101_120000_001.MP4</FPATH>
<SIZE>123456789</SIZE>
<TIMECODE>1609459200</TIMECODE>
<TIME>2021/01/01 12:00:00</TIME>
<ATTR>0</ATTR></File>
</ALLFile>
<ALLFile><File>
...
</File></ALLFile>
</LIST>
```

Notes:

* Each file is wrapped in its own `<ALLFile>…</ALLFile>` inside the outer
  `<LIST>`. This is what the firmware does; a parser should select all `<File>`
  elements rather than assume one `<ALLFile>` wrapper.
* `SIZE` is decimal bytes.
* `TIMECODE` is a Unix epoch (seconds).
* `TIME` is formatted `yyyy/MM/dd HH:mm:ss`.
* `ATTR` is a firmware file-attribute value; the stock app ignores it. Its exact
  bit meanings are unknown.
* `FPATH` uses the camera's `A:\` path, backslashes, and usually uppercase
  extensions. Convert to a URL by removing `A:` and replacing `\` with `/`.

### 3.3 Menu response (`cmd=3031`)

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<LIST>
<Item>
<Cmd>2003</Cmd>
<Name>Loop Recording</Name>
<MenuList>
<Option>
<Index>0</Index>
<Id>OFF</Id>
</Option>
<Option>
<Index>1</Index>
<Id>1 Minute</Id>
</Option>
<Option>
<Index>2</Index>
<Id>3 Minutes</Id>
</Option>
</MenuList>
</Item>
...
</LIST>
```

* `Cmd` is the HTTP command ID.
* `Index` is the value to send as `&par=`.
* `Id` is the display label.
* `Name` is the menu item name (English in the firmware; the app maps labels via
  `name_trn` resources).

### 3.4 Current-values response (`cmd=3014`)

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<Function>
<Cmd>2002</Cmd>
<Status>21</Status>
</Function>
<Function>
<Cmd>2003</Cmd>
<Status>2</Status>
</Function>
...
```

**Important:** `<Status>` here is the **current value** of the setting, not an
error code. This is confirmed by the firmware handler (`0x01A8D89C`), which
calls the per-menu getter and passes the result as the second `%d`. Do not treat
a `Status` other than 0 as a failure for `cmd=3014`.

Also note that the 3014 response is **not a single-root XML document**: it is a
sequence of `<Function>` elements after the XML declaration. A strict XML parser
will reject it. The stock app uses string extraction (`<Cmd>…</Cmd>`,
`<Status>…</Status>`). A replacement can wrap the body in a synthetic root or
extract the pairs with a tolerant parser.

The handler only emits entries whose command-table record has a non-zero
`menu_id` (the generic settings). It is not a dump of every command, and it does
not include file/media state. Use `3015`, `3024`, `3017`, etc. for those.

### 3.5 Live-view response (`cmd=2019`)

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<LIST>
<MovieLiveViewLink>rtsp://192.168.1.254/xxx.mov</MovieLiveViewLink>
<PhotoLiveViewLink>http://192.168.1.254:8192</PhotoLiveViewLink>
</LIST>
```

The exact values depend on the current mode and whether a rear camera is
present. Use the returned URLs.

### 3.6 Wi-Fi info response (`cmd=3029`)

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<LIST>
<SSID><![CDATA[MyK5]]></SSID>
<PASSPHRASE><![CDATA[12345678]]></PASSPHRASE>
</LIST>
```

### 3.7 Wi-Fi scan response (`cmd=3038`)

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<List>
<AP_index>
<SSID>MyNetwork</SSID>
<Auth_type>3</Auth_type>
<RSSI>-55 dBm</RSSI>
</AP_index>
...
</List>
```

### 3.8 GPS info response (`cmd=8058`)

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<Function>
<timestamp>1609459200</timestamp><lng>12.345678</lng>
<lat>45.678901</lat>
<alt>123.4</alt>
<spd>42.0</spd>
<SAT>
<SatNum>9</SatNum>
<SNR>32</SNR>
</SAT>
</Function>
```

When there is no fix, the firmware emits zero values.

## 4. Status and error codes

`<Status>0</Status>` means success. The stock app treats any non-negative status
as success (`LyApiResponseConverter`), and negative status as an error.

Known/observed values:

| Status | Meaning | Source |
|---:|---|---|
| `0` | Success | firmware/app |
| `-1` | Generic failure / file not found | handler code, public NT9666x docs |
| `-2` | No card / card error | handler code, public docs |
| `-4` | File operation error | handler code, public docs |
| `-5` | Write file failed | 1001 handler |
| `-7` | Movie storage full | public NT9666x docs |
| `-8` | Movie write error | public NT9666x docs |
| `-9` | Movie too slow | public NT9666x docs |
| `-11` | Storage full | 1001 handler (`WIFIAPP_RET_STORAGE_FULL`) |
| `-12` | Folder full | 1001 handler (`WIFIAPP_RET_FOLDER_FULL`) |
| `-13` | Unknown error | public docs |
| `-256` | Unsupported command | dispatcher / public docs |

The exact negative code for each error can vary by handler. Treat any negative
status as failure and use the command context.

## 5. Command groups

The complete curated table is [data/api_commands.csv](data/api_commands.csv)
(127 rows, including firmware-only commands whose purpose is not established).
The raw firmware table (including menu IDs, handler addresses, and menu options)
is [data/firmware_commands_raw.csv](data/firmware_commands_raw.csv).

### 5.1 Connectivity and identity

| cmd | Name | Request | Response |
|---:|---|---|---|
| 3016 | Heartbeat | no params | `<Cmd>3016</Cmd><Status>0</Status>` |
| 3002 | Query supported commands | no params | XML command list from `A:\query.xml` |
| 3012 | Firmware version | no params | `<String>version</String>` |
| 3022 | Hardware capability | no params | `<Value>19</Value>` (observed K5 handler; older docs show 3) |
| 3037 | Current mode/status | no params | `<Value>n</Value>` (0–4) |
| 3019 | Battery level | no params | `<Value>0..5</Value>` |
| 3024 | SD card state | no params | `<Value>0/1/2</Value>` |
| 3017 | Free SD space | no params | `<Value>bytes</Value>` |
| 8315 | Storage capacity | no params | `<Value>total</Value><Value1>used</Value1>` (meaning inferred) |

Recommended heartbeat interval: 5–10 seconds. The stock app uses a 1-second
timeout per probe, which is aggressive.

### 5.2 Capture and recording

| cmd | Name | Request | Response/notes |
|---:|---|---|---|
| 1001 | Capture photo | no params (app sends `par=1`) | `<File>…</File><FREEPICNUM>n</FREEPICNUM>` |
| 1002 | Photo resolution | `par=0..5` | 0=12M,1=10M,2=8M,3=5M,4=3M,5=VGA |
| 1003 | Free photo slots | no params | `<Value>n</Value>` |
| 2001 | Start/stop recording | `par=1` / `0` | `<Status>0</Status>` |
| 2009 | Max record time | no params | `<Value>seconds</Value>` |
| 2016 | Current recording time | no params | `<Value>seconds</Value>` |
| 2017 | Raw snapshot trigger | `par=1/0` | app `k.y0()` |
| 2018 | Get raw encoded JPEG | no params | `image/jpeg` |
| 8322 | Front raw JPEG | `GET /?custom=1&cmd=8322` | `image/jpeg` |
| 8323 | Rear raw JPEG | `GET /?custom=1&cmd=8323` | `image/jpeg` |

The record command returns errors such as `-7`, `-8`, `-9` when the card is
full, writing fails, or the card is too slow.

### 5.3 Settings

The K5-specific settings table is in
[data/k5_settings.csv](data/k5_settings.csv). The protocol-level operations are:

| cmd | Name | Request | Response |
|---:|---|---|---|
| 3014 | Get all current values | no params | `<Cmd>N</Cmd><Status>V</Status>` repeated; `V` is the value |
| 3031 | Get setting menu | `str=all` or `str=<key>` | `<Item>…` list |
| 3021 | Save settings | no params | `<Status>0</Status>` |
| 3001 | Change mode | `par=0 photo,1 video,2 playback,3 video-lapse,4 photo-lapse` | `<Status>0</Status>` |
| 3005 | Set date | `str=yyyy-MM-dd` | `<Status>0</Status>` |
| 3006 | Set time | `str=HH:mm:ss` | `<Status>0</Status>` |
| 3007 | Auto power off | `par=0 off,1 3min,2 5min,3 10min,4 now` | `<Status>0</Status>` |
| 3010 | Format SD | `par=1` | `<Status>0</Status>` |
| 3011 | Reset defaults | `par=1` | `<Status>0</Status>` |

> **K5 `2002` caveat.** The stock K5 JSON uses `par=20/22/21` for
> `2160P30+1080P30` / `1440P30+1080P30` / `1080P30+1080P30`. The firmware's
> generic `Resolution` menu (menu id 26) lists a different single-camera set
> (`2, 6, 7, 9, 10, 11, 13, 15, 16, 17, 18, 19, 20, 29`). These scales are not
> interchangeable. For a K5 replacement, use the JSON values or query
> `cmd=3030`/`cmd=3031` on the device and verify the reported indices.
> `cmd=3030` returns the camera's resolution list (`<Size>W*H</Size>`,
> `<FrameRate>`, `<Type>`), which is the best way to map indices on a real K5.

Recommended flow for a settings screen:

1. `GET ?custom=1&cmd=3031&str=all` — get the menu structure (cache it).
2. `GET ?custom=1&cmd=3014` — get all current values (cache them).
3. When the user changes a value:
   `GET ?custom=1&cmd=<id>&par=<index>` (or `&str=<text>`),
   then `GET ?custom=1&cmd=3021` to persist.
4. Update the local cache instead of re-fetching `3014`.

### 5.4 Wi-Fi

| cmd | Name | Request | Response/notes |
|---:|---|---|---|
| 3003 | Set SSID | `str=<ssid>` (max 32) | then `3021` |
| 3004 | Set password | `str=<pass>` (8–26) | then `3021` |
| 3029 | Get SSID/password | no params | `<SSID><![CDATA[…]]></SSID><PASSPHRASE><![CDATA[…]]></PASSPHRASE>` |
| 3032 | Set STA SSID:password | `str=SSID:password` | then `3021` |
| 3033 | Set Wi-Fi connect mode | `par=<enum>` | |
| 3018 | Reconnect Wi-Fi | no params | applies changes |
| 8201 | Wi-Fi band | `par=0 off,1 2.4GHz,2 5GHz` | |
| 8011 | Wi-Fi off timer | `par=0 On,1 5min,2 10min` | |
| 3038 | AP scan | no params | `<List><AP_index>…` |

The camera exposes its Wi-Fi credentials in plaintext via `3029`. This is useful
for a replacement client but is also a security consideration.

Changing the AP credentials:

```bash
curl 'http://192.168.1.254/?custom=1&cmd=3003&str=NewSSID'
curl 'http://192.168.1.254/?custom=1&cmd=3021'
curl 'http://192.168.1.254/?custom=1&cmd=3004&str=NewPassword'
curl 'http://192.168.1.254/?custom=1&cmd=3021'
curl 'http://192.168.1.254/?custom=1&cmd=3018'
```

The AP will restart; reconnect with the new credentials. Use 8–26 characters for
the password and ≤32 for the SSID. The stock app replaces spaces in the
watermark text with `*`; for SSID/password, avoid spaces to be safe.

### 5.5 Media

| cmd | Name | Request | Response |
|---:|---|---|---|
| 3015 | File list | no params | `<LIST><ALLFile><File>…` |
| 4001 | Thumbnail | `GET /<FPATH>?custom=1&cmd=4001` | `image/jpeg` |
| 4002 | Preview/screen | `GET /<FPATH>?custom=1&cmd=4002` | `image/jpeg` |
| 4003 | Delete one | `str=A:\path` | `<Status>0</Status>` |
| 4004 | Delete all | none in stock app; path param not established | `<Status>0</Status>` |
| 4005 | Movie file info | `GET /<FPATH>?custom=1&cmd=4005` | `<String>Width:W, Height:H, Length:L sec</String>` |

`3015` may require playback mode on some Novatek firmware. If it returns an
empty list, try `cmd=3001&par=2` (playback), wait about a second, then retry
`3015`; switch back to video (`par=1`) or photo (`par=0`) afterwards. The exact
requirement on the K5 is not confirmed.

`4001`/`4002` are not query-only commands; they are applied to a file URL. The
stock app builds:

```
thumbnail = downloadUrl + "?custom=1&cmd=4001"
```

where `downloadUrl = http://192.168.1.254/<FPATH without A: and with />`.

`4003` expects the camera path (`A:\...`) in `&str=`, not the URL path. The
stock app converts `http://192.168.1.254/NOVATEK/...` back to
`A:\NOVATEK\...`.

`4005` is different: the stock app sends the relative file path as part of the
**URL path**, not as `&str=`. For example:

```bash
curl 'http://192.168.1.254/NOVATEK/MOVIE/2021_0101_120000_001.MP4?custom=1&cmd=4005'
```

The handler reads the file from the path and returns an XML `<String>` with
width, height, and duration.

### 5.6 Live view

| cmd | Name | Request | Response |
|---:|---|---|---|
| 2019 | Live-view URLs | no params | `<MovieLiveViewLink>…</MovieLiveViewLink><PhotoLiveViewLink>…</PhotoLiveViewLink>` |
| 2015 | Live-view start/stop | `par=1/0` | optional when using RTSP directly |
| 2010 | Live-view size | `par=<enum>` | |
| 2014 | Live-view bitrate | firmware-specific | |
| 2026 | RTSP audio | `par=0/1` | |

The RTSP stream is the primary live view for movie mode. The MJPEG endpoint on
port 8192 is used for photo mode / rear-camera configurations. See
[04-media-and-transfers.md](04-media-and-transfers.md).

### 5.7 GPS

| cmd | Name | Request | Response |
|---:|---|---|---|
| 8003 | GPS enable | `par=1/0` | |
| 8004 | GPS status | no params | `<Value>0/1</Value>` |
| 8058 | GPS info | no params | `<timestamp>…<lng>…<lat>…<alt>…<spd>…<SAT>…` |
| 8307 | GPS speed | no params | `<Value>n</Value>` |
| 8308 | GPS coordinates | no params | `<String>N:… E:…</String>` |
| 8210 | GPS info stamp | `par=0 off,1 all,2 speed,3 coordinates` | |
| 8320 | GPS update rate | `par=0 1Hz … 4 5Hz` | |

The app's `checkGpsEnable` reads `8003` from the `3014` map and checks `8004`
separately. A replacement can use `8004` for readiness and `8058` for the fix.

### 5.8 Events (TCP 3333)

The camera pushes `<Function><Cmd>…</Cmd><Status>…</Status></Function>` messages
on a TCP connection to port 3333. The stock app handles:

| Event cmd | Meaning | Status |
|---:|---|---|
| 2020 | Recording time | seconds |
| 3020 | Camera action | 1 start, 2 stop, 8 sensor count, 9 card insert, 10 card remove, 11/12 protect on/off, 13/14 GPS on/off, 16 photo download, 21/22 timelapse |
| 9019 | Battery change | level |

The event stream is independent of the HTTP API. A replacement should connect
once, keep the connection open, and parse complete XML messages.

### 5.9 Firmware update / upload

| cmd | Name | Request | Notes |
|---:|---|---|---|
| 3013 | Firmware update trigger | no params | Caution; exact precondition not established |
| 3025 | Get download URL | no params | Firmware string is a hard-coded vendor URL |
| 3026 | Get update file path | no params | `http://<ip><path>` |
| 5001 | Upload file | upload body (POST/multipart suspected; not established) | `XML_UploadFile`; framing not established |
| 5002 | Upload audio | upload body (POST/multipart suspected; not established) | `XML_UploadAudio`; framing not established |

The stock K5 UI does not use these for firmware updates; it opens
`https://innovv.com/pages/app-firmware-manual`. Treat this group as
"documented but not recommended" until tested.

## 6. Request/response examples

### 6.1 Heartbeat

```bash
curl -s 'http://192.168.1.254/?custom=1&cmd=3016'
```

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<Function>
<Cmd>3016</Cmd>
<Status>0</Status>
</Function>
```

### 6.2 Current values

```bash
curl -s 'http://192.168.1.254/?custom=1&cmd=3014'
```

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<Function>
<Cmd>2002</Cmd>
<Status>21</Status>
</Function>
<Function>
<Cmd>2003</Cmd>
<Status>2</Status>
</Function>
<Function>
<Cmd>8003</Cmd>
<Status>1</Status>
</Function>
...
```

### 6.3 Media list

```bash
curl -s 'http://192.168.1.254/?custom=1&cmd=3015'
```

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<LIST>
<ALLFile><File>
<NAME>2021_0101_120000_001.MP4</NAME>
<FPATH>A:\NOVATEK\MOVIE\2021_0101_120000_001.MP4</FPATH>
<SIZE>123456789</SIZE>
<TIMECODE>1609459200</TIMECODE>
<TIME>2021/01/01 12:00:00</TIME>
<ATTR>0</ATTR></File>
</ALLFile>
</LIST>
```

### 6.4 Start recording

```bash
curl -s 'http://192.168.1.254/?custom=1&cmd=2001&par=1'
```

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<Function>
<Cmd>2001</Cmd>
<Status>0</Status>
</Function>
```

### 6.5 Set a setting and save

```bash
curl -s 'http://192.168.1.254/?custom=1&cmd=2002&par=21'
curl -s 'http://192.168.1.254/?custom=1&cmd=3021'
```

### 6.6 Download a file

```bash
curl -O 'http://192.168.1.254/NOVATEK/MOVIE/2021_0101_120000_001.MP4'
```

### 6.7 Thumbnail

```bash
curl -o thumb.jpg \
  'http://192.168.1.254/NOVATEK/MOVIE/2021_0101_120000_001.MP4?custom=1&cmd=4001'
```

### 6.8 Delete a file

```bash
curl -s --get 'http://192.168.1.254/' \
  --data-urlencode 'custom=1' \
  --data-urlencode 'cmd=4003' \
  --data-urlencode 'str=A:\NOVATEK\MOVIE\2021_0101_120000_001.MP4'
```

Note: `custom=1` must remain first; with `--data-urlencode` and `--get`, curl
preserves the order of the `--data-urlencode` options. In practice, building the
query string manually is safer:

```bash
curl -s 'http://192.168.1.254/?custom=1&cmd=4003&str=A%3A%5CNOVATEK%5CMOVIE%5C2021_0101_120000_001.MP4'
```

## 7. Special quirks and pitfalls

1. **`custom=1` first.** A request without it returns the test page, not an
   error. This is the most common mistake.
2. **`3014` `<Status>` is a value.** Do not interpret `Status=21` as an error.
3. **`3015` wraps each file in `<ALLFile>`.** Select `<File>` elements, not a
   single `<ALLFile>`.
4. **`3015` may be truncated.** The firmware builds the XML into a bounded
   buffer. If the list is huge, the response may end mid-XML. Handle parse
   failure by retrying or using FTP/TFTP.
5. **`8322`/`8323` are JPEG snapshots, not Wi-Fi credentials.** The stock app's
   `E()` method builds URLs for these; they are front/rear raw JPEGs. Wi-Fi
   credentials come from `3029`/`3003`/`3004`.
6. **`2020` and `3020` are dual-use.** `2020` is video contrast over HTTP and
   recording time over the event socket. `3020` is the camera-action event.
7. **`4001`/`4002` are file-URL modifiers.** They are not standalone
   `/?custom=1&cmd=4001` commands; they must be appended to a file path.
8. **`4003` expects `A:\...`**, while downloads use `/...`. Convert explicitly.
9. **`str` vs `par`.** Text values (SSID, password, date, time, watermark, file
   path) use `&str=`. Enum/numeric values use `&par=`.
10. **No auth.** Anyone on the AP can call any command.
11. **URL-encode backslashes and colons** in `str` values. `A:\NOVATEK\...`
    should be percent-encoded (`A%3A%5CNOVATEK%5C...`) or sent with a tool that
    encodes it.
12. **The RTSP/MJPEG URLs should come from `2019`.** The app and firmware use
    different default strings (`xxx.mp4` vs `xxx.mov`, port 554 vs default).
13. **`http://192.168.1.254/` is not a directory listing.** For non-API requests
    the RTOS serves `A:\test.htm`. Use `cmd=3015` or FTP/TFTP to enumerate files.

## 8. Command availability

The firmware command table has 122 entries, but not every entry is guaranteed to
be functional on every K5 variant. The K5 app uses a subset. The curated table
marks commands that are:

* used by the stock K5 app,
* present in the K5 menu,
* present in the firmware table but not exposed by the app.

If a command returns `-256` (unsupported), the firmware build does not implement
it for that model/mode. Always check the status and fall back gracefully.

## 9. Open questions

* Exact semantics of `ATTR` in the file list.
* Exact meaning of `8008` and `8315` `<Value1>`.
* Whether `3015` accepts a filter/paging parameter.
* Exact `3020` event status mapping (some codes inferred).
* Firmware-update preconditions and signature checks.
* RTSP path/port on the K5 hardware; prefer `2019`.
* Concurrent request behavior of the RTOS HTTP server.
