# Media, live view, and event transports

This document covers everything that is not a simple control command: file
listing, downloads, thumbnails, FTP/TFTP, RTSP/MJPEG, and the event socket.

## 1. File listing (`cmd=3015`)

Browsing `http://192.168.1.254/` does **not** show a directory listing; the
RTOS serves `A:\test.htm` for non-API requests. Use `cmd=3015` (or FTP/TFTP) to
enumerate files.

Request:

```bash
curl -s 'http://192.168.1.254/?custom=1&cmd=3015'
```

Response shape:

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
...
</LIST>
```

Field meanings:

| Field | Meaning |
|---|---|
| `NAME` | File name (usually same as the last path component) |
| `FPATH` | Camera path, `A:\...` with backslashes |
| `SIZE` | File size in bytes |
| `TIMECODE` | Unix epoch seconds |
| `TIME` | `yyyy/MM/dd HH:mm:ss` |
| `ATTR` | Firmware attribute; exact bits unknown; stock app ignores it |

The stock app's parser (`m.mifan.acase.ly.h`) selects all `<File>` elements and
reads `fpath`, `size`, and `time` case-insensitively. It converts:

```
A:\NOVATEK\MOVIE\2021_0101_120000_001.MP4
  -> NOVATEK/MOVIE/2021_0101_120000_001.MP4
  -> http://192.168.1.254/NOVATEK/MOVIE/2021_0101_120000_001.MP4
```

The examples use `NOVATEK`; the actual directory case comes from `FPATH`. The
firmware also contains a mixed-case `A:\Novatek\PHOTO` string, so always use the
exact path returned by `cmd=3015` rather than hard-coding a case.

A robust parser should select `<File>` elements rather than assume a single
`<ALLFile>` wrapper. Each file is individually wrapped.

### Truncation risk

The firmware builds the file list into a bounded output buffer. The handler at
`0x01A8C244` reserves 0x200 bytes at the end and stops when full. On a large SD
card, the XML may be truncated. Mitigations:

* Parse incrementally and keep the complete `<File>` elements you received.
* Re-request and merge if the XML is incomplete.
* Use FTP/TFTP for bulk sync (section 4).
* Keep the card organized and delete old files regularly.

The exact server buffer size was not established statically; treat this as a
real risk for multi-thousand-file cards.

Some Novatek firmware only returns a file list in playback mode. The stock K5
album flow switches modes around the album/preview screens. If `3015` returns an
empty list, try:

```bash
curl 'http://192.168.1.254/?custom=1&cmd=3001&par=2'
sleep 1
curl 'http://192.168.1.254/?custom=1&cmd=3015'
```

Then switch back to the desired mode with `cmd=3001&par=1` (video) or `par=0`
(photo). Mode changes may stop recording; do not do this while a recording you
care about is in progress. The exact mode requirement on the K5 is not
confirmed.

## 2. Direct HTTP download

Once you have `FPATH`, build:

```
url = "http://192.168.1.254/" + FPATH.removePrefix("A:").replace("\\", "/")
```

Example:

```bash
curl -O 'http://192.168.1.254/NOVATEK/MOVIE/2021_0101_120000_001.MP4'
```

Observed behavior from the app:

* HTTP GET, no auth.
* The media server serves the file directly from the SD card.
* The file list gives `SIZE`, so you can verify the download.
* Range requests were not verified against the K5; test `Range: bytes=...`
  before relying on resume. If supported, use a `.part` file and resume.

Recommended download algorithm:

1. Fetch `cmd=3015` once and cache the list.
2. Compare with local state (name, size, timecode).
3. Download new files to a `.part` file.
4. Verify size (and hash if you want).
5. Rename to the final name.
6. Optionally delete from the camera only after verification.

### Concurrency

The control API is handled by a small RTOS HTTP server. It may serialise or
limit concurrent requests. For media downloads:

* Use a small number of parallel connections (2–4) and measure.
* Keep control requests separate from downloads; do not block the control
  channel with a large download.
* If the camera becomes unresponsive, reduce to one download at a time.

## 3. Thumbnails and previews

The stock app appends a command to the file URL:

```
thumbnail = downloadUrl + "?custom=1&cmd=4001"
preview   = downloadUrl + "?custom=1&cmd=4002"
```

Example:

```bash
curl -o thumb.jpg \
  'http://192.168.1.254/NOVATEK/MOVIE/2021_0101_120000_001.MP4?custom=1&cmd=4001'
```

The response is `image/jpeg`. This is useful for album grids and avoids
downloading the whole video.

`cmd=4005` returns movie metadata. Unlike `4003`, it takes the file **URL path**,
not `&str=`:

```bash
curl 'http://192.168.1.254/NOVATEK/MOVIE/2021_0101_120000_001.MP4?custom=1&cmd=4005'
```

```xml
<Function>
<Cmd>4005</Cmd>
<Status>0</Status>
<String>Width:1920, Height:1080, Length:60 sec</String>
</Function>
```

The stock app parses the first value (width) by splitting on `,` and `:`.

## 4. FTP and TFTP (bulk transfer)

The Linux rootfs `/etc/inetd.conf` enables both services:

```
21 stream tcp nowait root ftpd  ftpd  -w /mnt/sd
69 dgram  udp wait   root tftpd tftpd -l -c /mnt/sd
```

### FTP

* Server: `192.168.1.254:21`
* Root directory: `/mnt/sd` (the SD card)
* User: `root`
* Password: `/etc/passwd` contains `root::0:0:root:/root:/bin/sh`, i.e. an
  **empty password** in the firmware image. BusyBox `ftpd` may accept
  `root` with an empty password. This was not tested on hardware.

Try:

```bash
curl -v 'ftp://root:@192.168.1.254/'
curl -O 'ftp://root:@192.168.1.254/NOVATEK/MOVIE/2021_0101_120000_001.MP4'
```

If the empty password is rejected, try the camera's Wi-Fi password or set a
root password via a shell (not possible without telnet/SSH; telnet is disabled
in `inetd.conf`).

Advantages: FTP is a mature, streaming protocol and may be much faster than the
camera's small HTTP server for large files. It also supports directory listing,
resume, and parallel transfers.

### TFTP

* Server: `192.168.1.254:69/udp`
* Root: `/mnt/sd`
* No authentication.
* `-c` allows create (upload).

Download:

```bash
tftp 192.168.1.254
tftp> binary
tftp> get NOVATEK/MOVIE/2021_0101_120000_001.MP4
```

TFTP is simple but has small block sizes and no reliability guarantees. Use it
for small files or as a fallback, not for multi-GB sync.

The RTOS `A:\` namespace and the Linux `/mnt/sd` mount refer to the same SD-card
files. The file list is the authoritative mapping; FTP/TFTP paths use the same
relative path without `A:\`.

## 5. RTSP live view

`cmd=2019` returns the live-view URLs:

```xml
<LIST>
<MovieLiveViewLink>rtsp://192.168.1.254/xxx.mov</MovieLiveViewLink>
<PhotoLiveViewLink>http://192.168.1.254:8192</PhotoLiveViewLink>
</LIST>
```

The stock app defaults if the fields are empty:

* movie: `rtsp://192.168.1.254:554/xxx.mp4`
* photo: `http://192.168.1.254:8192`

The firmware's format strings use `rtsp://%s/xxx.mov` and
`http://%s:8192`. The exact value depends on mode and rear-camera presence.
Always use the `2019` result.

Recommended player settings (from the app's IJK configuration):

```
rtsp_transport=tcp
fflags=nobuffer
packet-buffering=0
infbuf=1
max_cached_duration=30
probesize=200
```

For a replacement client:

* Use RTSP over TCP for reliability on Wi-Fi.
* Use a low-latency player (ExoPlayer with RTSP, FFmpeg, or GStreamer).
* Avoid an HTTP caching proxy for the live stream.
* Keep a single RTSP session; reconnect on failure with backoff.

## 6. MJPEG live view

The photo-mode live view is an HTTP MJPEG stream on port 8192:

```
http://192.168.1.254:8192
```

The stream is a sequence of JPEG frames over a single HTTP response. Use a
streaming HTTP client and parse JPEG SOI/EOI markers (`FF D8` … `FF D9`).
Do not use a normal "download whole body" client.

Some configurations may expose the movie stream over HTTP on port 8192 instead
of RTSP; the `2019` response tells you which link is which.

## 7. Event socket (TCP 3333)

Connect to `192.168.1.254:3333` and read XML messages:

```xml
<Function><Cmd>3020</Cmd><Status>1</Status></Function>
<Function><Cmd>2020</Cmd><Status>42</Status></Function>
<Function><Cmd>9019</Cmd><Status>5</Status></Function>
```

Observed event commands:

| Cmd | Meaning | Status |
|---:|---|---|
| 2020 | Recording time | seconds |
| 3020 | Camera action | 1 record start, 2 record stop, 8 sensor count, 9 card insert, 10 card remove, 11/12 protect on/off, 13/14 GPS on/off, 16 photo download, 21/22 timelapse |
| 9019 | Battery change | level |

Framing advice:

* The stock app reads into a 500-byte buffer and hands each read to the parser.
  This can split a message.
* A replacement should use a `BufferedReader`/byte buffer and accumulate until
  `</Function>` is seen.
* A single read may contain multiple `<Function>` messages; parse in a loop.
* Reconnect with backoff if the socket closes.

Event socket vs polling:

* Use the event socket for record start/stop, card insert/remove, protect/GPS
  changes, battery, and recording time.
* Use `cmd=3016` heartbeat only for connectivity, not for state.
* Do not poll `cmd=3014` every second; it is a full dump.

## 8. File formats and paths

The firmware records:

* Movies: `%s\FILE%04d.MP4` and `A:\DCIM\%dNVTIM\IMAG%04d.MP4` patterns are
  present in the RTOS strings. The actual file list is authoritative.
* Photos: under `A:\NOVATEK\PHOTO` (string observed) and `A:\DCIM\...`.
* Movie format can be MP4 or TS (`cmd=8010`).
* Encode can be H.264 or H.265 (`cmd=8304`).

The stock app classifies `.mp4`, `.mov`, `.ts` as video and everything else as
photo. A replacement should use the file extension and/or `ATTR`.

## 9. Performance checklist for media

* Fetch the file list once; cache it with name/size/timecode.
* Download only new files; use `.part` + size verification.
* Try FTP for bulk sync; fall back to HTTP.
* Use thumbnails (`4001`) for grids, not full videos.
* Do not run downloads and live view through the same tiny HTTP server if you
  can avoid it; test the impact.
* Use RTSP/TCP for live view; avoid HTTP caching proxies.
* If `3015` is truncated, merge and/or switch to FTP directory listing.
