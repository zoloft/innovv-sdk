# INNOVV K5 — firmware and Android app reverse-engineering notes

This folder documents the INNOVV K5 dashcam firmware (`K5.bin`) and the stock
Android app (`INNOVV 1.0.38.20260618`) with the goal of replacing the app with a
faster client. The focus is the **actual local API** used over the camera's
Wi-Fi access point.

The camera was not reachable while this analysis was performed, so all
statements are static-analysis observations unless explicitly marked otherwise.
See [06-evidence-ledger.md](06-evidence-ledger.md) for the distinction between
observed, inferred, and unknown facts.

> **Publishing note.** These are raw research notes. They include firmware
> layout details and observations about default access-point credentials and
> the firmware's root account. Review or remove this directory before making
> the repository public if that information should not be disclosed.

## TL;DR

The K5 exposes the standard **Novatek CarDV HTTP API**:

```
http://192.168.1.254/?custom=1&cmd=<command>[&par=<integer>][&str=<string>]
```

Key transports:

| Transport | Address | Purpose |
|---|---|---|
| HTTP control API | `http://192.168.1.254/` (port 80) | Commands, settings, file list, XML/JPEG responses |
| HTTP media download | `http://192.168.1.254/<FPATH>` | Direct MP4/JPG download from SD card |
| RTSP live view | `rtsp://192.168.1.254:554/...` | H.264/H.265 live stream (movie mode) |
| MJPEG live view | `http://192.168.1.254:8192` | JPEG live stream (photo mode / some configurations) |
| Event push | TCP `192.168.1.254:3333` | Camera-pushed `<Function><Cmd>…</Cmd><Status>…</Status></Function>` |
| FTP (BusyBox) | `ftp://192.168.1.254/` | SD card bulk transfer; enabled in `inetd.conf` |
| TFTP (BusyBox) | `udp://192.168.1.254:69` | SD card bulk transfer; enabled in `inetd.conf` |
| Thumbnail/preview | `GET /<FPATH>?custom=1&cmd=4001` / `4002` | JPEG thumbnail / playback preview |

The most important K5-specific details:

* **`custom=1` must be the first query parameter.** The firmware CGI dispatcher
  looks for the literal string `custom=1&cmd=`.
* **`cmd=3014` is a full settings dump**, and its `<Status>` field is the
  **current value** of each setting, not an error code. This is a Novatek quirk.
* **`cmd=3031&str=all` returns the camera's setting menu** (command IDs, labels,
  option indices). The stock app uses it to build the preferences screen.
* **`cmd=3015` returns the media file list** with `NAME`, `FPATH`, `SIZE`,
  `TIMECODE`, `TIME`, `ATTR`. The file download URL is the `FPATH` with `A:`
  removed and `\` replaced by `/`.
* **File transfers do not need the XML API at all.** Once you know `FPATH`,
  download it directly. FTP/TFTP are also enabled by the firmware and may be
  much faster for bulk sync.
* **The stock app is slow for identifiable reasons**: OkHttp is configured with
  a `BODY`-level logging interceptor, a single-request dispatcher in the primary
  client, repeated full `3014` dumps, a 500-byte unframed event-socket buffer,
  and (in some playback paths) an AndroidVideoCache proxy. See
  [05-replacement-client-notes.md](05-replacement-client-notes.md).

## Folder map

| File | Contents |
|---|---|
| [01-firmware-analysis.md](01-firmware-analysis.md) | Firmware container, partitions, decompression, rootfs, services, ports |
| [02-android-app-analysis.md](02-android-app-analysis.md) | APK identity, protocol classes, K5 preference JSON, performance issues |
| [03-api-reference.md](03-api-reference.md) | Complete HTTP command reference, request/response schemas, examples |
| [04-media-and-transfers.md](04-media-and-transfers.md) | File list, download, thumbnail, FTP/TFTP, RTSP/MJPEG, event framing |
| [05-replacement-client-notes.md](05-replacement-client-notes.md) | Design and performance recommendations for a fast replacement |
| [06-evidence-ledger.md](06-evidence-ledger.md) | Hashes, REA evidence IDs, key addresses, confidence and unknowns |
| [data/api_commands.csv](data/api_commands.csv) | Curated command table (127 rows, includes firmware-only unknowns) |
| [data/firmware_commands_raw.csv](data/firmware_commands_raw.csv) | Raw RTOS command table extracted from the firmware |
| [data/k5_settings.csv](data/k5_settings.csv) | K5 settings menu from the app's bundled JSON |
| [data/k5_preferences.json](data/k5_preferences.json) | K5 preference definition extracted from the APK (cleaned JSON) |
| [data/k5_preferences.raw.json](data/k5_preferences.raw.json) | Exact extracted file, including `//` comments |
| [data/firmware_partitions.txt](data/firmware_partitions.txt) | K5.bin partition table |
| [data/sha256.txt](data/sha256.txt) | Artifact hashes |
| [data/README.md](data/README.md) | Column descriptions for the data files |

## Quick start for a replacement client

```bash
# 1. Connect to the camera Wi-Fi AP first.
# 2. Heartbeat.
curl 'http://192.168.1.254/?custom=1&cmd=3016'

# 3. Current settings (Status = current value).
curl 'http://192.168.1.254/?custom=1&cmd=3014'

# 3b. Setting menu (command IDs + option indices).
curl 'http://192.168.1.254/?custom=1&cmd=3031&str=all'

# 4. Media list.
curl 'http://192.168.1.254/?custom=1&cmd=3015'

# 5. Start recording.
curl 'http://192.168.1.254/?custom=1&cmd=2001&par=1'

# 6. Live-view URLs.
curl 'http://192.168.1.254/?custom=1&cmd=2019'

# 7. Direct file download (replace with an FPATH from step 4).
curl -O 'http://192.168.1.254/NOVATEK/MOVIE/2021_0101_120000_001.MP4'

# 8. Event stream (example; use a proper TCP client and buffer until </Function>).
nc 192.168.1.254 3333
```

## Reproduction / tooling

* Firmware container parsed with Python; the RTOS partition is **BCL1 / LZ77**
  (Basic Compression Library, algorithm `0x09`, marker `0xAD`). The decompressed
  image loads at `0x01800000` (ARM little-endian). Decompressed size is
  `0x76406C` (7,749,740 bytes).
* UBI volumes were reconstructed with a small parser and extracted with
  `ubi_reader` (installed locally under `work/pylibs`).
* Android code was decompiled with JADX 1.5.6 and inspected with REA's Android
  tools.
* Ghidra analysis of the raw ARM RTOS image was performed through REA; key
  addresses are in [06-evidence-ledger.md](06-evidence-ledger.md).

## Safety notes

The HTTP API has **no authentication**. Anyone connected to the camera AP can
control it, read the Wi-Fi password (`cmd=3029`), download/delete media, or
format the SD card (`cmd=3010`). The firmware rootfs also enables **FTP**
(`inetd.conf`, running as root; `/etc/passwd` has an empty root password) and
**unauthenticated TFTP** by default. FTP login behavior was not tested on
hardware. Treat the camera AP as the security boundary and change its Wi-Fi
credentials.

Firmware-update commands (`3013`, `3026`, `5001`) are documented for
completeness. Do not call them on a device you are not prepared to recover.
